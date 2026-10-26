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
package cn.jfspeed.oauth2.client.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OAuth2 客户端接入配置新增/修改请求体。
 * 与实体分离：不接收审计字段，避免客户端伪造写入。
 *
 * @author jffast
 */
@Data
@Schema(description = "OAuth2 客户端配置保存请求")
public class OAuth2ClientConfigRequest {

    /** ID（新增时留空由服务端生成，修改时必填） */
    @Schema(description = "ID（修改时必填）")
    private Long id;

    @NotBlank(message = "provider 不能为空")
    @Schema(description = "提供方标识，如 gitee / github / 自建")
    private String provider;

    @Schema(description = "显示名称")
    private String name;

    @NotBlank(message = "clientId 不能为空")
    @Schema(description = "客户端 ID")
    private String clientId;

    @Schema(description = "客户端密钥（回显时前端应脱敏展示）")
    private String clientSecret;

    @Schema(description = "支持的授权类型，逗号分隔，如 authorization_code,refresh_token")
    private String grantTypes;

    @Schema(description = "授权端点")
    private String authorizationUrl;

    @Schema(description = "令牌端点")
    private String tokenUrl;

    @Schema(description = "回调地址")
    private String redirectUri;

    @Schema(description = "客户端认证方式")
    private String authMethod;

    @Schema(description = "申请的权限范围")
    private String scopes;

    @Schema(description = "令牌缓存有效期（秒）")
    private Long ttl;

    @Schema(description = "扩展配置（JSON 字符串）")
    private String extra;

    @Schema(description = "状态：0-停用，1-启用")
    private Integer status;
}
