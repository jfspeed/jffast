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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * {@code sys_file} 记录读取（JDBC 直读，与 {@link SysFileRecorder} 同款做法）。
 *
 * <p>存在的意义：让**不依赖 jffast-system** 的服务也能按 id 读到文件归属，
 * 从而为「随业务记录展示的附件」换发签名 URL（例如 demo 微服务的示例图片）。
 *
 * @author jffast
 */
public class SysFileReader {

    private static final Logger log = LoggerFactory.getLogger(SysFileReader.class);

    private static final String SELECT_BY_ID = "SELECT id, tenant_id, create_by, file_name, url, size, mime "
            + "FROM sys_file WHERE id = ? LIMIT 1";

    private final JdbcTemplate jdbc;

    public SysFileReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 读取能力是否可用 */
    public boolean isAvailable() {
        return jdbc != null;
    }

    /**
     * 按 ID 读取记录；不存在或读取失败返回 {@code null}。
     */
    public SysFileRecord findById(Long id) {
        if (jdbc == null || id == null) {
            return null;
        }
        try {
            List<SysFileRecord> rows = jdbc.query(SELECT_BY_ID, (rs, rowNum) -> new SysFileRecord(
                    rs.getLong("id"),
                    rs.getObject("tenant_id") == null ? null : rs.getLong("tenant_id"),
                    rs.getObject("create_by") == null ? null : rs.getLong("create_by"),
                    rs.getString("file_name"),
                    rs.getString("url"),
                    rs.getObject("size") == null ? null : rs.getLong("size"),
                    rs.getString("mime")), id);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.warn("读取 sys_file 记录失败: id={}, {}", id, e.getMessage());
            return null;
        }
    }
}
