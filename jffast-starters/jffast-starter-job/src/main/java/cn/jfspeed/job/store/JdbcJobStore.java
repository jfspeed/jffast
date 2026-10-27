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
package cn.jfspeed.job.store;

import cn.hutool.core.util.IdUtil;
import cn.jfspeed.job.domain.JobLogRecord;
import cn.jfspeed.job.domain.JobRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.List;

/**
 * `sys_job` / `sys_job_log` 的 JdbcTemplate 实现。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>不依赖 `jffast-system`</b>：任何服务引入本 Starter 就拥有"登记 + 调度自己的任务"的能力；</li>
 *   <li><b>SQL 保持跨库可移植</b>：不写 `NOW()`/`SYSDATE` 之类函数，时间列交给 Java 传参或数据库默认值，
 *       以便 MySQL/PostgreSQL/Oracle/达梦/人大金仓 都能跑（项目支持多数据库）；</li>
 *   <li><b>降级分两类（2026-09-24 整改）</b>：
 *       <ul>
 *         <li><b>结构性错误</b>（缺表 / 缺列，如 sys_job.service_name 缺失）⇒ 永久停用，
 *             打一条可操作的错误日志，服务照常启动（否则客户升级后会直接起不来，违背"可平滑升级"）；</li>
 *         <li><b>瞬时故障</b>（连接超时 / 死锁 / 连接池耗尽…）⇒ 只进入**冷却期**（{@value #COOLDOWN_MILLIS}ms），
 *             冷却结束后自动恢复重试。⚠️ 旧实现是"任何一次读写失败就把 Store 永久置为不可用"
 *             （`available=false` 且永不复位），于是**一次瞬时抖动就让整个 JVM 的定时调度停摆到重启** —— 已修；</li>
 *       </ul></li>
 *   <li><b>写执行日志失败只告警</b>：`sys_job_log` 写不进去绝不影响任务执行、更不会拖垮调度
 *       （旧实现里日志写失败会触发全局降级，属于最严重的自伤，已修）。</li>
 * </ul>
 *
 * @author jffast
 */
@Slf4j
public class JdbcJobStore implements JobStore {

    private static final String COLS = "id, job_name, job_group, invoke_target, cron_expression, "
            + "misfire_policy, concurrent, status, service_name, remark";

    /** 瞬时故障的冷却时长：冷却期内不再重试（避免刷屏），到期后自动恢复 */
    private static final long COOLDOWN_MILLIS = 30_000L;

    private final JdbcTemplate jdbcTemplate;

    /** 缺表/缺列等**结构性**错误：置 true 后不再重试（重试也没意义），需人工执行补丁 SQL 后重启 */
    private volatile boolean schemaMissing = false;

    /** 瞬时故障的冷却截止时间（epoch millis）；到期后下次调用自动重试 */
    private volatile long cooldownUntil = 0L;

    /** 写执行日志失败的告警是否已打过（只提示一次，避免每次执行都刷屏） */
    private volatile boolean logWriteWarned = false;

    public JdbcJobStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isAvailable() {
        return !schemaMissing && !inCooldown();
    }

    private boolean inCooldown() {
        return System.currentTimeMillis() < cooldownUntil;
    }

    @Override
    public List<JobRecord> listAll() {
        if (!isAvailable()) {
            // ⚠️ 读不到时必须返回 null 而不是空表：JobScheduler.sync() 的反向对账会把「表里没有的行」
            //    当成已删除并清理触发器，空表 = "任务全被删了" ⇒ 一次抖动就清空本进程的调度。
            //    null 表示"我不知道表里有什么"，调用方据此跳过本轮对账；空列表只表示"表确实为空"。
            return null;
        }
        try {
            return jdbcTemplate.query("SELECT " + COLS + " FROM sys_job", this::mapRow);
        } catch (DataAccessException e) {
            degrade(e);
            return null;
        }
    }

    @Override
    public JobRecord findByInvokeTarget(String invokeTarget) {
        if (!isAvailable()) {
            return null;
        }
        try {
            List<JobRecord> list = jdbcTemplate.query(
                    "SELECT " + COLS + " FROM sys_job WHERE invoke_target = ? ORDER BY id", this::mapRow, invokeTarget);
            return list.isEmpty() ? null : list.get(0);
        } catch (DataAccessException e) {
            degrade(e);
            return null;
        }
    }

    @Override
    public boolean insert(JobRecord record) {
        if (!isAvailable()) {
            return false;
        }
        try {
            jdbcTemplate.update("INSERT INTO sys_job (id, job_name, job_group, invoke_target, cron_expression, "
                            + "misfire_policy, concurrent, status, service_name, remark) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    IdUtil.getSnowflakeNextId(),
                    record.getJobName(),
                    record.getJobGroup(),
                    record.getInvokeTarget(),
                    record.getCronExpression() == null ? "" : record.getCronExpression(),
                    record.getMisfirePolicy() == null ? "3" : record.getMisfirePolicy(),
                    record.getConcurrent() == null ? 0 : record.getConcurrent(),
                    record.getStatus() == null ? 0 : record.getStatus(),
                    record.getServiceName(),
                    record.getRemark());
            return true;
        } catch (DuplicateKeyException e) {
            // 多副本同时启动时可能同时登记同一条任务并撞唯一键 —— 这是**并发正常现象**，不是故障
            log.debug("【定时任务】任务已存在（并发登记），跳过插入: {}", record.getInvokeTarget());
            return false;
        } catch (DataAccessException e) {
            degrade(e);
            return false;
        }
    }

    @Override
    public boolean claim(Long id, String serviceName) {
        if (!isAvailable()) {
            return false;
        }
        try {
            // 影响行数 = 1 表示"这一行原本 service_name IS NULL，被本服务认领成功"；
            // = 0 表示"已经被别的服务认领走了"（认领是**有竞争**的，并发下只有一个赢家）。
            int affected = jdbcTemplate.update(
                    "UPDATE sys_job SET service_name = ? WHERE id = ? AND service_name IS NULL",
                    serviceName, id);
            return affected > 0;
        } catch (DataAccessException e) {
            degrade(e);
            return false;
        }
    }

    @Override
    public void insertLog(JobLogRecord record) {
        if (schemaMissing) {
            return;
        }
        try {
            jdbcTemplate.update("INSERT INTO sys_job_log (id, job_name, job_group, invoke_target, status, "
                            + "error_msg, start_time, end_time, duration) VALUES (?,?,?,?,?,?,?,?,?)",
                    IdUtil.getSnowflakeNextId(),
                    record.getJobName(),
                    record.getJobGroup(),
                    record.getInvokeTarget(),
                    record.getStatus(),
                    record.getErrorMsg(),
                    record.getStartTime(),
                    record.getEndTime(),
                    record.getDuration());
        } catch (DataAccessException e) {
            // ⚠️ 记日志失败**只告警**，绝不降级：一条执行记录写不进去不该影响任务结果，
            //    更不该把整个调度器停掉（旧实现这里调 degrade()，是"一条日志拖垮全调度"的自伤）。
            if (!logWriteWarned) {
                logWriteWarned = true;
                log.warn("【定时任务】写 sys_job_log 失败：执行记录会缺失，但任务本身照常执行（相同错误不再重复提示）: {}",
                        e.getMessage());
            } else {
                log.debug("【定时任务】写 sys_job_log 仍失败: {}", e.getMessage());
            }
        }
    }

    private JobRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        JobRecord r = new JobRecord();
        r.setId(rs.getLong("id"));
        r.setJobName(rs.getString("job_name"));
        r.setJobGroup(rs.getString("job_group"));
        r.setInvokeTarget(rs.getString("invoke_target"));
        r.setCronExpression(rs.getString("cron_expression"));
        r.setMisfirePolicy(rs.getString("misfire_policy"));
        r.setConcurrent(rs.getInt("concurrent"));
        r.setStatus(rs.getInt("status"));
        r.setServiceName(rs.getString("service_name"));
        r.setRemark(rs.getString("remark"));
        return r;
    }

    /**
     * 降级：区分"结构性错误"（永久停用）与"瞬时故障"（冷却后自动重试）。
     */
    private void degrade(DataAccessException e) {
        if (isSchemaError(e)) {
            if (!schemaMissing) {
                schemaMissing = true;
                log.error("【定时任务】读不到 sys_job / sys_job_log（缺表或缺列），本服务的定时任务已停用。"
                        + "请确认数据库已按 sql/system_sql.sql 建表（含 sys_job.service_name 列）。原因: {}",
                        e.getMessage());
            }
            return;
        }
        cooldownUntil = System.currentTimeMillis() + COOLDOWN_MILLIS;
        log.error("【定时任务】访问 sys_job / sys_job_log 失败，*暂停 {}s 后自动重试*"
                        + "（已排期的触发器继续按原计划执行，不会因本次失败被清掉）。原因: {}",
                COOLDOWN_MILLIS / 1000, e.getMessage());
    }

    /** 缺表 / 缺列 / 语法错误 ⇒ 重试无意义，永久降级（需人工执行补丁 SQL 后重启服务） */
    private boolean isSchemaError(DataAccessException e) {
        return e instanceof BadSqlGrammarException
                || e.getMostSpecificCause() instanceof SQLSyntaxErrorException;
    }
}
