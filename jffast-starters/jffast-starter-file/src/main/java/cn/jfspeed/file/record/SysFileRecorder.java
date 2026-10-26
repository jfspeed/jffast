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

import cn.jfspeed.common.core.util.IdUtils;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.file.config.FileStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code sys_file} 上传登记（JdbcTemplate 直连，不依赖 jffast-system）。
 * 只在 classpath 有 spring-jdbc 时装配（见自动配置的嵌套配置）。
 *
 * <p>登记是**尽力而为**：表未建、SQL 失败时只告警，不影响上传/删除本身的结果。
 *
 * @author jffast
 */
public class SysFileRecorder {

    private static final Logger log = LoggerFactory.getLogger(SysFileRecorder.class);

    private final JdbcTemplate jdbc;
    private final FileStorageProperties properties;

    /** {@code jdbc} 可为 null（有 spring-jdbc 但无 DataSource 时），此时登记静默跳过 */
    public SysFileRecorder(JdbcTemplate jdbc, FileStorageProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public void record(String prefix, String filename, String url, Long size, String mime) {
        if (jdbc == null) {
            return;
        }
        try {
            jdbc.update("INSERT INTO sys_file (id, file_name, url, size, mime, biz_type, storage_type, create_by, create_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                    IdUtils.snowflakeShort(), filename, url, size, mime,
                    prefix, properties.getType(), currentUserId());
        } catch (Exception e) {
            log.warn("sys_file 登记失败（不影响上传结果）: {}", e.getMessage());
        }
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
}
