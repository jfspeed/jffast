/*
 * Copyright 2024-2026 jffast
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.jfspeed.job.scheduler;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.job.config.JobProperties;
import cn.jfspeed.job.domain.JobRecord;
import cn.jfspeed.job.domain.JobTargetInfo;
import cn.jfspeed.job.handler.DefaultQuartzJob;
import cn.jfspeed.job.handler.DisallowConcurrentQuartzJob;
import cn.jfspeed.job.manager.QuartzManager;
import cn.jfspeed.job.scanner.JobAnnotationScanner;
import cn.jfspeed.job.store.JobStore;
import cn.jfspeed.job.util.JobInvokeUtil;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobKey;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务调度器（Starter 内，**任何服务引入 starter 即拥有**）。
 *
 * <p>三件事：
 * <ol>
 *   <li><b>登记</b>：启动时把本服务 `@JfFastJob` 声明的方法登记进 `sys_job`（注解为主，代码即任务定义），
 *       `service_name` 自动 = 本服务名；老数据（`service_name` 为空）由"代码里真有这个 Bean"的服务**认领**；</li>
 *   <li><b>调度</b>：只调度 <b>归属本服务</b> 的任务（见 {@link #canRunHere}），
 *       彻底解决"共享一张 `sys_job` 表 → 每个服务都跑一遍"的重复执行问题；</li>
 *   <li><b>同步</b>：每 {@code jffast.job.scheduler.refresh-interval-seconds} 秒对一次数据库，
 *       于是"页面上启用/暂停任务、改 cron"**无需重启服务**即可生效。</li>
 * </ol>
 *
 * <p>设计意图：调度能力放在本 Starter（而非业务模块），配合任务归属判定，
 * 一个任务**只有一个**执行者；多副本之间再由 {@code JobLock}（Redis）保证同一时刻只执行一次。
 *
 * @author jffast
 */
@Slf4j
public class JobScheduler implements CommandLineRunner, DisposableBean {

    public static final String DEFAULT_GROUP = "DEFAULT";

    /**
     * 触发器归属戳的 {@code JobDataMap} 键：这个 trigger 是**哪个服务**建的。
     *
     * <p>为什么需要它：Quartz 的 {@code SCHED_NAME}（{@code QRTZ_*.SCHED_NAME}）是行分区键，
     * 两个应用共用同一个库**且**用同一个 {@code instanceName} 时会被并进同一个集群，
     * 彼此的 trigger 在同一张表里。此时"这条 trigger 是谁的"不能靠命名空间推断，
     * 只能靠建它时写下的戳。
     */
    static final String OWNER_KEY = "ownerServiceName";

    private final JobProperties properties;
    private final JobStore jobStore;
    private final QuartzManager quartzManager;
    private final JobAnnotationScanner scanner;
    /** 本服务名 —— 就是任务的"执行服务"标识（`spring.application.name`） */
    private final String appName;

    private volatile ScheduledExecutorService refresher;

    /** 本进程是否已完成首轮对账（首轮会**强制重建**触发器，把 misfire 策略/执行类应用到历史数据） */
    private volatile boolean firstSyncDone;

    public JobScheduler(JobProperties properties, JobStore jobStore, QuartzManager quartzManager,
                        JobAnnotationScanner scanner, Environment environment) {
        this.properties = properties;
        this.jobStore = jobStore;
        this.quartzManager = quartzManager;
        this.scanner = scanner;
        // 服务名 = 任务归属标识。单体没有 spring.application.name（本项目只有 app.name），
        // 故做回退，否则单体的任务归属会写成 "application"（可读性差）。
        this.appName = environment.getProperty("spring.application.name",
                environment.getProperty("app.name", "application"));
    }

    public String getAppName() {
        return appName;
    }

    @Override
    public void run(String... args) {
        if (!properties.getScheduler().isEnabled()) {
            log.info("【定时任务】本服务已关闭调度（jffast.job.scheduler.enabled=false），服务={}", appName);
            return;
        }
        if (!jobStore.isAvailable()) {
            log.warn("【定时任务】任务存储不可用，跳过登记与调度（服务={}）", appName);
            return;
        }
        registerAnnotatedJobs();
        sync();
        startRefresher();
        log.info("【定时任务】初始化完成：服务={}，本服务调度={}，是否调度无归属任务={}，锁={}",
                appName,
                properties.getScheduler().isEnabled(),
                properties.getScheduler().isDefaultScope(),
                properties.getLock().getTtlSeconds() + "s TTL");
    }

    /**
     * 把本服务 `@JfFastJob` 声明的方法登记进 `sys_job`。
     * <p>规则：① 已登记过（按 `invoke_target` 查）且已有归属 ⇒ **不动**（管理员改过的 cron/启停永远优先）；
     * ② 已登记但归属为空 ⇒ **认领**为本服务（老数据平滑迁移）；③ 没登记过 ⇒ 插入，`status` 取注解
     * `enabled`（默认 false=暂停），`cron` 取注解 `cron`（可为空 = 未排期）。
     */
    public void registerAnnotatedJobs() {
        List<JobTargetInfo> targets = scanner.getJobTargets();
        if (targets.isEmpty()) {
            return;
        }
        // 一次读出全表，在内存里做匹配（表通常只有几十行），避免 N 次查询
        List<JobRecord> existingRows = jobStore.listAll();
        int inserted = 0;
        int claimed = 0;
        for (JobTargetInfo target : targets) {
            try {
                boolean hasParams = target.getParamNames() != null && !target.getParamNames().isEmpty();
                JobRecord existing = findMatch(existingRows, target, hasParams);
                if (existing == null) {
                    if (hasParams) {
                        // 带参任务的调用目标是 `bean.method({param})`，`{param}` 只是**占位符**（由前端弹窗替换为用户输入），
                        // 直接入库不可执行 ⇒ 只提示，等管理员在页面上配好具体参数再建。
                        log.info("【定时任务】带参任务 {}（{}）未登记：请在「系统管理 → 定时任务」里配置参数后新增",
                                target.getName(), target.getInvokeTarget());
                        continue;
                    }
                    JobRecord record = new JobRecord();
                    record.setJobName(target.getName());
                    record.setJobGroup(DEFAULT_GROUP);
                    record.setInvokeTarget(target.getInvokeTarget());
                    record.setCronExpression(StrUtil.nullToEmpty(target.getCron()));
                    record.setMisfirePolicy("3");
                    record.setConcurrent(0);
                    record.setStatus(target.isEnabled() ? 1 : 0);
                    record.setServiceName(appName);
                    record.setRemark("由 @JfFastJob 自动登记（" + appName + "）");
                    jobStore.insert(record);
                    inserted++;
                    log.info("【定时任务】登记新任务: {} → {}（归属={}，初始状态={}，cron={}）",
                            target.getName(), target.getInvokeTarget(), appName,
                            target.isEnabled() ? "启用" : "暂停",
                            StrUtil.isBlank(target.getCron()) ? "未排期" : target.getCron());
                } else if (StrUtil.isBlank(existing.getServiceName())) {
                    jobStore.claim(existing.getId(), appName);
                    claimed++;
                    log.info("【定时任务】认领无归属任务: {} → 归属 {}", existing.getJobName(), appName);
                } else if (!appName.equals(existing.getServiceName())) {
                    log.warn("【定时任务】任务 {} 已归属 {}（本服务 {} 不会执行它）",
                            existing.getJobName(), existing.getServiceName(), appName);
                }
            } catch (Exception e) {
                log.error("【定时任务】登记任务失败: {}", target.getInvokeTarget(), e);
            }
        }
        if (inserted > 0 || claimed > 0) {
            log.info("【定时任务】注解登记完成：新增 {} 条、认领 {} 条（服务={}）", inserted, claimed, appName);
        }
    }

    /**
     * 在已有任务里找该注解对应的行：
     * <ul>
     *   <li>无参任务：按调用目标**精确匹配**；</li>
     *   <li>带参任务：按 `beanName.methodName(` 前缀匹配（页面上配好的具体参数，如 `...generateReport(daily)`）。</li>
     * </ul>
     */
    private JobRecord findMatch(List<JobRecord> rows, JobTargetInfo target, boolean hasParams) {
        String prefix = target.getBeanName() + "." + target.getMethodName() + "(";
        for (JobRecord row : rows) {
            if (StrUtil.isBlank(row.getInvokeTarget())) {
                continue;
            }
            if (row.getInvokeTarget().equals(target.getInvokeTarget())) {
                return row;
            }
            if (hasParams && row.getInvokeTarget().startsWith(prefix)) {
                return row;
            }
        }
        return null;
    }

    /**
     * 与数据库对账：把"本服务该跑且已启用"的任务排进本进程 Quartz，
     * 把"不该本服务跑 / 已暂停 / 已删除"的触发器清掉。可反复调用（幂等）。
     *
     * <p>⚠️ 对账是**双向**的（2026-09-24 整改）：除了遍历数据库行做增改，还会做一次
     * <b>反向差集清理</b> —— 本进程里存在、而数据库已不再要求它跑的触发器一律删除。
     * 否则"任务改名 / 改组 / 被删除 / 被手工改库"后，旧 JobKey 的触发器会**按老 cron 继续幽灵执行**。
     */
    public synchronized void sync() {
        if (!properties.getScheduler().isEnabled() || !jobStore.isAvailable()) {
            return;
        }
        List<JobRecord> rows = jobStore.listAll();
        int scheduled = 0;
        int notMine = 0;
        int noCron = 0;
        int removed = 0;
        int disabled = 0;
        int running = 0;
        boolean firstSync = !firstSyncDone;
        // 本进程"应当存在"的任务标识；反向对账时凡是"存在但不在该集合里"的都要清掉
        Set<JobKey> desired = new HashSet<>();
        for (JobRecord row : rows) {
            try {
                // 不归本服务 **或已暂停** 的，都要确保本进程里没有它的触发器。
                // ⚠️ 2026-09-23 现场验证抓到过漏判 status 的 bug：只判归属不判启用，
                //    结果 paused 的任务照样被排进 Quartz（表现：启动日志"新建/改动 2 条"而两条都是暂停的）。
                if (!canRunHere(row) || !row.isEnabled()) {
                    if (quartzManager.checkExists(row.getJobName(), row.getJobGroup())) {
                        if (ownedByOtherService(row.getJobName(), row.getJobGroup())) {
                            // 归属戳表明这条 trigger 是别的服务建的：共享同一套 QRTZ_ 表时，
                            // 删掉它就是打断兄弟服务（对方页面里任务还在、却不再执行）。
                            log.debug("【定时任务】触发器 {}.{} 属于其它服务，本服务不删除",
                                    row.getJobGroup(), row.getJobName());
                        } else {
                            quartzManager.deleteJob(row.getJobName(), row.getJobGroup());
                            removed++;
                        }
                    }
                    if (!row.isEnabled()) {
                        disabled++;
                    } else {
                        notMine++;
                    }
                    continue;
                }
                if (StrUtil.isBlank(row.getCronExpression())) {
                    noCron++;
                    continue;
                }
                running++;
                claimIfRunnableAndUnowned(row);
                desired.add(JobKey.jobKey(row.getJobName(), row.getJobGroup()));
                String currentCron = quartzManager.getCronExpression(row.getJobName(), row.getJobGroup());
                // 首轮（进程刚启动）**强制重建**：misfire 策略、执行类这些"只在创建时生效"的东西
                // 必须重新写进触发器 —— JDBC jobstore 里历史触发器的策略无法就地比较/修改，
                // 不重建就会出现"改了 misfire_policy 却仍按旧策略跑"（2026-09-23 单体实测）。
                Class<? extends Job> jobClass = jobClassOf(row);
                String currentClass = quartzManager.getJobClassName(row.getJobName(), row.getJobGroup());
                boolean classChanged = currentClass != null && !currentClass.equals(jobClass.getName());
                // ⚠️ 调用目标（invokeTarget）与失火策略同样只写在 JobDataMap / 触发器里，
                // **不改 cron 时也要重建**，否则"在任务页改了执行目标"静默不生效，
                // 仍按旧目标执行（且「立即执行」也会跑旧目标），直到服务重启才恢复。
                boolean targetChanged = !Objects.equals(
                        quartzManager.getJobDataValue(row.getJobName(), row.getJobGroup(), "invokeTarget"),
                        row.getInvokeTarget());
                boolean misfireChanged = !Objects.equals(
                        effectiveMisfirePolicy(quartzManager.getMisfirePolicy(row.getJobName(), row.getJobGroup())),
                        effectiveMisfirePolicy(row.getMisfirePolicy()));
                if (currentCron == null || firstSync || classChanged || targetChanged || misfireChanged
                        || !currentCron.equalsIgnoreCase(row.getCronExpression())) {
                    quartzManager.createJob(row.getJobName(), row.getJobGroup(), row.getCronExpression(),
                            jobClass, buildJobDataMap(row), row.getMisfirePolicy());
                    scheduled++;
                }
            } catch (Exception e) {
                log.error("【定时任务】同步任务失败: {}", row.getJobName(), e);
            }
        }
        // 反向对账：清掉"数据库已不再需要"的孤儿触发器（改名 / 改组 / 删除 / 转归属 / 手工改库）
        removed += orphanCleanup(desired, rows);
        firstSyncDone = true;
        if (scheduled > 0 || removed > 0) {
            log.info("【定时任务】同步完成：服务={} 共 {} 条；本次调度/改动 {} 条、清理 {} 条；"
                            + "本服务在跑 {} 条（已暂停 {} 条，未排期 {} 条，非本服务 {} 条）",
                    appName, rows.size(), scheduled, removed, running, disabled, noCron, notMine);
        } else {
            log.debug("【定时任务】同步完成（无变化）：服务={} 共 {} 条；本服务在跑 {} 条"
                            + "（已暂停 {} 条，未排期 {} 条，非本服务 {} 条）",
                    appName, rows.size(), running, disabled, noCron, notMine);
        }
    }

    /**
     * 反向对账：把本进程里"不该存在"的触发器删掉。
     *
     * <p>为什么必须有：Quartz 的 JobKey = `jobName + jobGroup`，任务一改名/改组，旧触发器就与数据库**失去关联**
     * ——正向遍历数据库永远发现不了它，于是它会一直按老定义（含老 cron、老 `invokeTarget`）继续触发。
     *
     * <p>⚠️ 清理范围必须限在"数据库里查无此行"的键上：{@code listJobKeys()} 走的是
     * {@code GroupMatcher.anyJobGroup()}，共享同一套 QRTZ_ 表的多服务部署里它返回的是**所有服务**的任务。
     * 只跟本服务的 desired 比差集时，A 服务一轮对账就能把 B 服务刚排好的触发器当孤儿删掉，
     * 而且删除落在共享表上，B 重启也找不回来。现在凡是任务表仍有对应行的键一律放行，交给归属它的服务管。
     *
     * @param desired 本进程当前**应当**存在的任务标识集合
     * @param rows    任务表全量行，用于判断某个 JobKey 是否仍被任何服务需要
     */
    private int orphanCleanup(Set<JobKey> desired, List<JobRecord> rows) {
        int cleaned = 0;
        Set<JobKey> referenced = new HashSet<>();
        for (JobRecord row : rows) {
            referenced.add(JobKey.jobKey(row.getJobName(), row.getJobGroup()));
        }
        try {
            for (JobKey key : quartzManager.listJobKeys()) {
                if (desired.contains(key)) {
                    continue;
                }
                if (referenced.contains(key)) {
                    // 任务表里还有这一行：要么归别的服务，要么本服务不该跑（正向对账已删），都不是孤儿
                    log.debug("【定时任务】触发器 {}.{} 仍被任务表引用，不由本服务清理", key.getGroup(), key.getName());
                    continue;
                }
                if (ownedByOtherService(key.getName(), key.getGroup())) {
                    // 归属戳表明是别的服务建的：任务表里这一行已经被删掉了，但触发器归对方管，
                    // 共享 QRTZ_ 表时替它清理 = 打断兄弟服务。留给对方自己的对账处理。
                    log.debug("【定时任务】触发器 {}.{} 属于其它服务，本服务不清理", key.getGroup(), key.getName());
                    continue;
                }
                quartzManager.deleteJob(key.getName(), key.getGroup());
                cleaned++;
                log.warn("【定时任务】清理孤儿触发器（任务表已无对应行）: {}.{}",
                        key.getGroup(), key.getName());
            }
        } catch (Exception e) {
            log.error("【定时任务】清理孤儿触发器失败（下次同步会重试）", e);
        }
        return cleaned;
    }

    /**
     * 无归属任务（{@code service_name} 为空）由**跑得动它的那个服务**认领。
     *
     * <p>为什么必须认领：{@code service_name} 为空的行按 {@code canRunHere} 的口径归
     * 「默认调度者」（{@code jffast.job.scheduler.default-scope=true} 的那个服务）。
     * 而这个开关是**每个应用各自配**的 —— 两个服务都配了 true 时，同一条无归属任务
     * 会被两边同时排期：内存调度器下变成**同一个任务被执行两次**，
     * 共享 QRTZ_ 表时变成两个 trigger 互删（谁也跑不稳）。
     *
     * <p>认领后 {@code service_name} 落库，归属变成**记录下来的事实**而不是运行时开关，
     * 别的服务就不会再认领它（{@code claim} 自带 {@code AND service_name IS NULL}，并发下也只有一个赢家）。
     *
     * <p>⚠️ <b>只认领自己跑得动的任务</b>（调用目标的 Bean 在本进程的 classpath 里）：
     * 认领一个跑不动的任务，会把真正跑得动的那个服务挡在门外 ⇒ 任务永远没人执行
     * （这正是 cloud 的 system 服务去执行 demo 任务、结果 "NoSuchBeanDefinitionException" 的那类问题）。
     *
     * <p>注解登记路径（{@link #registerAnnotatedJobs}）已经对"本服务有 Bean"的行做过同样的认领，
     * 这里补的是**没有任何服务有 Bean** 的那部分：谁都不认领 ⇒ 保持无归属，
     * 由默认调度者按原口径执行（老行为不变，不会凭空多出"没人跑"的洞）。
     */
    private void claimIfRunnableAndUnowned(JobRecord row) {
        if (row.getId() == null || StrUtil.isNotBlank(row.getServiceName())) {
            return;
        }
        if (!properties.getScheduler().isDefaultScope()) {
            return;
        }
        if (!JobInvokeUtil.isBeanPresent(row.getInvokeTarget())) {
            // 本服务跑不动它（Bean 不在本进程 classpath）⇒ 不认领，留给跑得动的那个服务
            return;
        }
        try {
            jobStore.claim(row.getId(), appName);
            log.info("【定时任务】默认调度者认领无归属任务: {} → 归属 {}", row.getJobName(), appName);
        } catch (Exception e) {
            log.warn("【定时任务】认领无归属任务失败（不影响本轮调度）: {}", row.getJobName(), e);
        }
    }

    /**
     * 归属判定 —— **只有归属本服务的任务才在本服务执行**。
     * <ul>
     *   <li>`service_name` 非空：必须等于本服务名；</li>
     *   <li>`service_name` 为空（老数据 / 页面手工新建）：由 `jffast.job.scheduler.default-scope=true`
     *       的"默认调度者"执行（单体即唯一进程；多服务部署时通常是系统服务）。</li>
     * </ul>
     */
    public boolean canRunHere(JobRecord row) {
        if (!properties.getScheduler().isEnabled()) {
            return false;
        }
        if (StrUtil.isNotBlank(row.getServiceName())) {
            return row.getServiceName().equals(appName);
        }
        return properties.getScheduler().isDefaultScope();
    }

    /** 供页面提示用：这条任务归谁执行 */
    public String describeOwner(JobRecord row) {
        if (StrUtil.isNotBlank(row.getServiceName())) {
            return row.getServiceName();
        }
        return "（无归属）由默认调度者执行";
    }

    /**
     * 立即执行一次（页面上的"执行"按钮）。
     * <p>只有归属本服务的任务才能触发 —— 任务方法是反射调用的 Bean，本服务不一定有它。
     * <p>⚠️ 抛 {@link IllegalStateException} 而不是业务异常，是因为本类在 Starter 里、不依赖 `jffast-common`；
     * 页面侧（`SysJobService.triggerJob`）会先做归属预检并把它转成 `BusinessException`，用户看到的是可读提示。
     * <p>与 {@link #sync()} 同锁：本方法要"先补建 JobDetail、再触发"，两步之间若插进一轮对账，
     * 对账可能正好把这个 JobKey 删掉（任务刚被暂停/改名时就会撞上）。表现是点了"执行"什么也没跑，
     * 而 Quartz 的 `triggerJob` 对不存在的 JobKey 不报错，所以日志里连失败都看不到。
     */
    public synchronized void triggerNow(JobRecord row) {
        if (!canRunHere(row)) {
            throw new IllegalStateException("任务「" + row.getJobName() + "」归属 " + describeOwner(row)
                    + "，本服务（" + appName + "）不执行它");
        }
        try {
            quartzManager.ensureJobExists(row.getJobName(), row.getJobGroup(), jobClassOf(row),
                    buildJobDataMap(row));
            quartzManager.triggerJob(row.getJobName(), row.getJobGroup());
            log.info("【定时任务】已触发一次执行: {}（服务={}）", row.getJobName(), appName);
        } catch (Exception e) {
            throw new IllegalStateException("立即执行失败: " + e.getMessage(), e);
        }
    }

    /** 删除本进程的触发器（任务被删除时调用；正常启停走 {@link #sync()} 即可） */
    public void cancel(String jobName, String jobGroup) {
        try {
            if (quartzManager.checkExists(jobName, jobGroup)) {
                quartzManager.deleteJob(jobName, jobGroup);
            }
        } catch (Exception e) {
            // 这里只是"尽力而为"：即使本次失败，{@link #sync()} 的反向对账与下个周期同步也会把它清掉
            log.error("【定时任务】取消调度失败（将由下一次 sync 的反向对账兜底）: {}.{}", jobGroup, jobName, e);
        }
    }

    /**
     * 按 `sys_job.concurrent` 挑执行类 —— Quartz 的并发控制是 **Job 类级注解**（按 JobKey 生效），
     * 所以要"某条任务不并发执行"只能换类：
     * <ul>
     *   <li>`concurrent = 1`（允许并发）⇒ {@link DefaultQuartzJob}；</li>
     *   <li>其它（交付默认 0）⇒ {@link DisallowConcurrentQuartzJob}（带 {@code @DisallowConcurrentExecution}）。</li>
     * </ul>
     * 改造前这个字段是**死配置**（只被塞进 JobDataMap、没人读）；`sync()` 会在类不一致时重建触发器。
     */
    private Class<? extends Job> jobClassOf(JobRecord row) {
        return (row.getConcurrent() != null && row.getConcurrent() == 1)
                ? DefaultQuartzJob.class
                : DisallowConcurrentQuartzJob.class;
    }

    private Map<String, Object> buildJobDataMap(JobRecord row) {
        Map<String, Object> params = new HashMap<>();
        params.put("invokeTarget", row.getInvokeTarget());
        params.put("misfirePolicy", row.getMisfirePolicy());
        params.put("concurrent", row.getConcurrent());
        // 归属戳：标记这个 trigger 是本服务建的。
        // 共享同一套 QRTZ_ 表时（两个应用连同一个库且 instanceName 相同），删除动作落在共享表上，
        // 没有这个戳就无法判断"这条 trigger 是不是我的" ⇒ 会把兄弟服务刚排好的 trigger 删掉。
        params.put(OWNER_KEY, appName);
        return params;
    }

    /**
     * 这个触发器是不是**别的服务**建的？
     *
     * <p>读的是建 trigger 时写下的归属戳（{@link #OWNER_KEY}）。
     * <ul>
     *   <li>戳写着别的服务 ⇒ **不是我的**，删除动作不能碰它（共享 QRTZ_ 表时碰了就是打断对方）；</li>
     *   <li>戳是自己 / 没有戳 ⇒ 视为自己的（没有戳的是升级前建的老 trigger，行为与从前一致）。</li>
     * </ul>
     * 读失败时按"自己的"处理并告警：读取失败属于 Quartz 侧异常，此时整条调度链大概率已经不正常，
     * 保持旧行为比改变删除语义更可控。
     */
    private boolean ownedByOtherService(String jobName, String jobGroup) {
        try {
            Object owner = quartzManager.getJobDataValue(jobName, jobGroup, OWNER_KEY);
            return owner != null && !appName.equals(owner.toString());
        } catch (Exception e) {
            log.warn("【定时任务】读取触发器归属失败（按本服务处理）: {}.{}", jobGroup, jobName, e);
            return false;
        }
    }

    /**
     * 失火策略的**生效值**：{@code sys_job.misfire_policy} 允许为空，空即"下次执行"（口径 3）。
     * 对账时两侧都换算成生效值再比，否则"null vs 3"会被误判成"改过"而白白重建触发器。
     */
    private String effectiveMisfirePolicy(String policy) {
        return StrUtil.isBlank(policy) ? "3" : policy.trim();
    }

    private void startRefresher() {
        int interval = properties.getScheduler().getRefreshIntervalSeconds();
        if (interval <= 0) {
            log.info("【定时任务】周期同步已关闭（jffast.job.scheduler.refresh-interval-seconds=0）："
                    + "启用/暂停任务、修改 cron 需重启服务后生效");
            return;
        }
        refresher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jffast-job-refresher");
            t.setDaemon(true);
            return t;
        });
        refresher.scheduleWithFixedDelay(() -> {
            try {
                sync();
            } catch (Exception e) {
                log.error("【定时任务】周期同步异常", e);
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
        if (refresher != null) {
            refresher.shutdownNow();
        }
    }
}
