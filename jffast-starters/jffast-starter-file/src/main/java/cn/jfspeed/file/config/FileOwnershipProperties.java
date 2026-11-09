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
package cn.jfspeed.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件归属模型配置（上传登记 + 签名 URL）。
 *
 * <pre>
 * jffast:
 *   file:
 *     sign:
 *       secret: xxx          # HMAC 密钥；生产必填（空则启动 fail-closed），dev/test 缺省时生成一次性随机密钥
 *       ttl-seconds: 900     # 签名有效期（秒），默认 900
 *     record:
 *       required: true       # 是否强制上传登记；true 时无 DataSource / 无 JdbcTemplate 启动失败
 * </pre>
 *
 * <p>与 {@code jffast.file.storage.*}（存储类型/路径/大小限制）分属两套前缀：
 * 存储配置决定「文件放哪」，本配置决定「文件归谁、谁能读」。
 *
 * @author jffast
 */
@Data
@ConfigurationProperties(prefix = "jffast.file")
public class FileOwnershipProperties {

    /** 签名相关配置 */
    private Sign sign = new Sign();

    /** 上传登记相关配置 */
    private UploadRecord record = new UploadRecord();

    /**
     * 签名配置
     */
    @Data
    public static class Sign {

        /**
         * HMAC-SHA256 密钥。
         *
         * <p>非 dev/test profile 下为空 ⇒ 启动 fail-closed（多实例密钥不一致会让验签随机失败，
         * 因此不允许「自动生成」这种静默降级）。
         */
        private String secret;

        /** 签名有效期（秒），默认 900（15 分钟） */
        private long ttlSeconds = 900L;
    }

    /**
     * 上传登记配置
     */
    @Data
    public static class UploadRecord {

        /**
         * 是否强制「有上传即必须有登记」。
         *
         * <p>true（默认）：没有 JdbcTemplate（未接 DataSource）时启动直接失败；
         * 登记 SQL 失败时上传整体失败并补偿删除已落盘文件。
         *
         * <p>false（显式降级）：允许无登记上传（fileId 为 null），此时**不得**启用
         * {@code /sys/file/view/**} 匿名直显与受控下载。
         */
        private boolean required = true;
    }
}
