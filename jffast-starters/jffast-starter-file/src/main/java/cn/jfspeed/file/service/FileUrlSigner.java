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
package cn.jfspeed.file.service;

import cn.jfspeed.common.security.util.FileUrlSignUtil;
import cn.jfspeed.file.config.FileOwnershipProperties;
import org.springframework.stereotype.Component;

/**
 * 文件签名票据的签发与验签（纯计算，不碰数据库）。
 *
 * <p>放在 starter-file 是为了让**所有能上传文件的服务**都能签发票据：
 * 票据最终由 {@code /sys/file/view/{id}} 校验，而该端点可能部署在另一个服务上，
 * 因此签发方与校验方必须使用**同一把密钥**（{@code jffast.file.sign.secret}）。
 *
 * <p>⚠️ 本类只管「签名」，不管「能不能签」——归属校验由调用方负责：
 * 见 jffast-system 的 {@code FileUrlSignService}（严格归属 / 记录附件两种口径）。
 *
 * @author jffast
 */
@Component
public class FileUrlSigner {

    /** 匿名直显端点（API 根相对路径） */
    public static final String VIEW_PATH_PREFIX = "/sys/file/view/";

    private final FileOwnershipProperties properties;

    public FileUrlSigner(FileOwnershipProperties properties) {
        this.properties = properties;
    }

    /**
     * 签发票据。
     *
     * @param fileId   文件 ID
     * @param tenantId 归属租户（0 = 平台/单租户）
     * @return 票据（{@code base64url(payload).hex(HMAC)}）
     */
    public String sign(long fileId, long tenantId) {
        long expireAt = System.currentTimeMillis() / 1000L + properties.getSign().getTtlSeconds();
        return FileUrlSignUtil.sign(fileId, tenantId, expireAt, properties.getSign().getSecret());
    }

    /**
     * 验签并解析票据；签名错 / 过期 / 格式错一律抛业务异常（HTTP 200 + 业务码 400）。
     */
    public FileUrlSignUtil.FileUrlTicket verify(String ticket) {
        return FileUrlSignUtil.verify(ticket, properties.getSign().getSecret());
    }

    /** 签名有效期（秒） */
    public long ttlSeconds() {
        return properties.getSign().getTtlSeconds();
    }
}
