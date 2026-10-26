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
package cn.jfspeed.oauth2.client.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.jfspeed.common.data.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth2 客户端接入配置表（本服务作为客户端去调用第三方的接入注册）。
 * <p>
 * 与授权服务器侧的 sys_oauth2_client 方向相反：本表描述"我要接入的第三方"。
 * provider 为唯一键，作为服务内定位配置与 token 缓存的关键字。
 *
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oauth2_client_config")
@Schema(title = "OAuth2客户端接入配置")
public class OAuth2ClientConfig extends BaseEntity {

    @Schema(title = "第三方唯一标识，如 github/custom")
    private String provider;

    @Schema(title = "接入名称")
    private String name;

    @Schema(title = "客户端ID")
    private String clientId;

    /** 客户端密钥：仅允许写入（请求体可携带），序列化时永不输出，避免明文泄露到前端/日志 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(title = "客户端密钥", hidden = true)
    private String clientSecret;

    @Schema(title = "授权类型，逗号分隔：client_credentials,authorization_code")
    private String grantTypes;

    @Schema(title = "授权地址 authorization_code 用")
    private String authorizationUrl;

    @Schema(title = "令牌地址")
    private String tokenUrl;

    @Schema(title = "回调地址 authorization_code 用")
    private String redirectUri;

    @Schema(title = "认证方式：client_secret_post / client_secret_basic")
    private String authMethod;

    @Schema(title = "授权范围，空格或逗号分隔")
    private String scopes;

    @Schema(title = "令牌默认有效期(秒)，空则按响应 expires_in")
    private Long ttl;

    @Schema(title = "扩展 JSON：URL 模板占位、token 响应键名映射等")
    @TableField("extra")
    private String extra;

    @Schema(title = "状态：0 禁用 1 启用")
    private Integer status;
}
