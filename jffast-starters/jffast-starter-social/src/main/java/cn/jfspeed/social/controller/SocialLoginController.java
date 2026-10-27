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
package cn.jfspeed.social.controller;

import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.social.domain.SocialBind;
import cn.jfspeed.social.domain.SocialLoginResult;
import cn.jfspeed.social.domain.dto.SocialBindRequest;
import cn.jfspeed.social.service.SocialLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 社交账号绑定端点。
 *
 * <p>authorize 是**绑定前**的匿名跳转（跳第三方平台，此时浏览器还没有登录态）；
 * callback（兑换授权码）与 bind / unbind / binds 是个人维度的绑定管理（需登录态、不设权限码）。
 * 故整类不加 {@code @SaCheckPermission}（白名单与理由见 jffast-boot 的 EndpointPermissionAnnotationTest）。
 *
 * @author jffast
 */
@Tag(name = "社交账号绑定")
@RestController
@RequestMapping("/social")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jffast.social.enabled", havingValue = "true")
public class SocialLoginController {

    private final SocialLoginService socialLoginService;

    @GetMapping("/authorize")
    @Operation(summary = "获取授权链接", description = "返回社交平台的 OAuth2 授权链接，前端跳转此链接进行授权")
    public R<String> authorize(@RequestParam String provider,
                               @RequestParam(required = false) String targetUri) {
        return R.success(socialLoginService.getAuthorizationUrl(provider, targetUri));
    }

    @GetMapping("/callback")
    @Operation(summary = "授权回调", description = "社交平台授权后回调，通过 state 解码获取平台标识，换取 access_token 并获取用户信息")
    public R<SocialLoginResult> callback(@RequestParam String code, @RequestParam String state) {
        return R.success(socialLoginService.callback(code, state));
    }

    @PostMapping("/bind")
    @Operation(summary = "绑定社交账号", description = "提交回调返回的 bindTicket，服务端验签后绑定到当前登录用户")
    @SLog(title = "社交账号-绑定")
    @RateLimit(value = "default")
    public R<Void> bind(@RequestBody @Valid SocialBindRequest request) {
        socialLoginService.bind(request.getTicket());
        return R.success();
    }

    @DeleteMapping("/unbind/{provider}")
    @Operation(summary = "解绑社交账号", description = "解除当前用户与指定平台社交账号的绑定")
    @SLog(title = "社交账号-解绑")
    public R<Void> unbind(@PathVariable String provider) {
        socialLoginService.unbind(provider);
        return R.success();
    }

    @GetMapping("/binds")
    @Operation(summary = "查询已绑定社交账号", description = "获取当前登录用户已绑定的所有社交账号列表")
    public R<List<SocialBind>> listBinds() {
        return R.success(socialLoginService.listBinds());
    }
}
