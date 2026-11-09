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
package cn.jfspeed.file.record;

import cn.jfspeed.common.core.context.TenantIds;
import cn.jfspeed.common.core.context.TenantScopeBridge;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.core.util.IdUtils;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.file.config.FileStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code sys_file} 上传登记（JdbcTemplate 直连，不依赖 jffast-system）。
 * 只在 classpath 有 spring-jdbc 时装配（见自动配置的嵌套配置）。
 *
 * <p><b>登记是 fail-closed 的</b>：表未建 / SQL 失败 / 无 JdbcTemplate 时一律抛
 * {@link BusinessException}，由装饰器补偿删除已落盘文件。上传与登记必须同生共死——
 * 只落盘不登记的文件既不在文件管理页可见，又随时可能被当成垃圾清理，留下的是不可追溯的孤儿。
 *
 * <p>登记同时写入 {@code tenant_id}（归属租户，0 = 平台/单租户，与 {@code sys_role} 同口径）：
 * 来源优先 {@link TenantScopeBridge}（Pro 的租户上下文），退回登录用户的 {@code tenantId}，
 * 皆空落 0。
 *
 * @author jffast
 */
public class SysFileRecorder {

    private static final Logger log = LoggerFactory.getLogger(SysFileRecorder.class);

    /** 平台 / 单租户口径：与 {@code SysConfigService.PLATFORM_TENANT_ID} 一致 */
    public static final long PLATFORM_TENANT_ID = 0L;

    private final JdbcTemplate jdbc;
    private final FileStorageProperties properties;

    /** 租户上下文桥；Base 单租户无实现，Pro 的租户 Starter 提供 */
    private final ObjectProvider<TenantScopeBridge> tenantScopeBridge;

    /** {@code jdbc} 可为 null（有 spring-jdbc 但无 DataSource 时），此时登记直接失败 */
    public SysFileRecorder(JdbcTemplate jdbc, FileStorageProperties properties) {
        this(jdbc, properties, null);
    }

    public SysFileRecorder(JdbcTemplate jdbc, FileStorageProperties properties,
                           ObjectProvider<TenantScopeBridge> tenantScopeBridge) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.tenantScopeBridge = tenantScopeBridge;
    }

    /**
     * 登记能力是否可用（是否有可用的 JdbcTemplate）。
     *
     * <p>自动配置用它做启动期裁定：{@code jffast.file.record.required=true} 却拿不到可用登记能力 ⇒ 启动失败。
     */
    public boolean isAvailable() {
        return jdbc != null;
    }

    /**
     * 登记一条文件记录。
     *
     * @return 新生成的 sys_file 主键（雪花 ID）
     * @throws BusinessException 无 JdbcTemplate 或 INSERT 失败时抛出（fail-closed）
     */
    public Long record(String prefix, String filename, String url, Long size, String mime) {
        if (jdbc == null) {
            throw new BusinessException("上传登记能力未启用：缺少 JdbcTemplate");
        }
        Long id = IdUtils.snowflakeShort();
        try {
            jdbc.update("INSERT INTO sys_file (id, file_name, url, size, mime, biz_type, storage_type, create_by, create_time, tenant_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?)",
                    id, filename, url, size, mime,
                    prefix, properties.getType(), currentUserId(), currentTenantId());
        } catch (Exception e) {
            log.error("sys_file 登记失败: {}", e.getMessage(), e);
            throw new BusinessException("上传登记失败，请重试");
        }
        return id;
    }

    public void remove(String url) {
        if (jdbc == null) {
            return;
        }
        try {
            jdbc.update("DELETE FROM sys_file WHERE url = ?", url);
        } catch (Exception e) {
            log.warn("sys_file 记录清理失败（不影响删除结果）: {}", e.getMessage());
        }
    }

    private Long currentUserId() {
        try {
            return LoginUtil.getLoginUserId();
        } catch (Exception e) {
            // 非登录上下文（脚本/异步任务等）上传：上传人留空
            return null;
        }
    }

    /**
     * 当前归属租户（统一口径见 {@code TenantIds.resolve}）。
     *
     * <p>⚠️ 不做「隐式租户兜底」：查不到租户就落 0 意味着多租户环境里文件被归到平台，
     * 租户侧看不到自己上传的文件——这比报错更难排查，因此这里只认明确来源。
     */
    private Long currentTenantId() {
        TenantScopeBridge bridge = null;
        try {
            if (tenantScopeBridge != null) {
                bridge = tenantScopeBridge.getIfAvailable();
            }
        } catch (Exception e) {
            log.debug("取租户上下文失败，退回登录用户租户: {}", e.getMessage());
        }
        return TenantIds.resolve(bridge);
    }
}
