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
package cn.jfspeed.oauth2.client.controller;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.oauth2.client.domain.OAuth2ClientToken;
import cn.jfspeed.oauth2.client.service.OAuth2ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * OAuth2 客户端授权流程端点（authorization_code）：
 * <p>
 * 1. GET /oauth2/client/authorize/{provider} —— 生成授权引导 URL
 * 2. 用户在第三方确认授权后回调 GET /oauth2/client/callback/{provider}?code=&state=
 * <p>
 * 令牌换发后会缓存到系统 Redis，之后业务方经 getToken(provider) 复用。
 * <p>
 * 加 @ConditionalOnProperty：本类位于 cn.jfspeed 扫描包内，组件扫描会无条件发现它，
 * 若 Starter 关闭（enabled=false）时不拦截，构造器注入的 OAuth2ClientService Bean 不存在会导致启动失败。
 */
@RestController
@ConditionalOnProperty(name = "jffast.oauth2.client.enabled", havingValue = "true")
@RequestMapping("/oauth2/client")
@Tag(name = "OAuth2客户端授权流程")
// 匿名端点：授权码流程的 authorize / callback 回调无法携带登录态 —— 已在
// {@code jffast.security.auth-excludes} 放行，故不加 {@code @SaCheckPermission}
// （白名单与理由见 jffast-boot 的 EndpointPermissionAnnotationTest）。
/**
 * @author jffast
 */
public class OAuth2ClientFlowController {

    private final OAuth2ClientService clientService;

    public OAuth2ClientFlowController(OAuth2ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping("/authorize/{provider}")
    @Operation(summary = "生成授权引导URL")
    public R authorize(@PathVariable("provider") String provider) {
        return R.success(clientService.buildAuthorizeUrl(provider));
    }

    @GetMapping("/callback/{provider}")
    @Operation(summary = "授权回调")
    public R callback(@PathVariable("provider") String provider,
                      @RequestParam(value = "code", required = false) String code,
                      @RequestParam(value = "state", required = false) String state,
                      @RequestParam(value = "error", required = false) String error) {
        if (StringUtils.hasText(error)) {
            throw new BusinessException("第三方授权失败: " + error);
        }
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("授权回调缺少 code");
        }
        OAuth2ClientToken token = clientService.handleCallback(provider, code, state);
        return R.success("授权成功，令牌已缓存");
    }
}