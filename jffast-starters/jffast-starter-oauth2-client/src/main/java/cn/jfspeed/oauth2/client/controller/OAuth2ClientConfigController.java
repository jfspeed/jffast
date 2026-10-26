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

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import cn.jfspeed.oauth2.client.domain.dto.OAuth2ClientConfigRequest;
import jakarta.validation.Valid;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.core.util.IdUtils;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.oauth2.client.domain.OAuth2ClientToken;
import cn.jfspeed.oauth2.client.domain.dto.OAuth2ClientConfigQueryRequest;
import cn.jfspeed.oauth2.client.entity.OAuth2ClientConfig;
import cn.jfspeed.oauth2.client.service.OAuth2ClientConfigService;
import cn.jfspeed.oauth2.client.service.OAuth2ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/**
 * OAuth2 客户端接入配置管理（sys_oauth2_client_config，页面可维护）。
 * <p>
 * 加 @ConditionalOnProperty：Starter 关闭时本类不注册（否则构造器注入的 Service Bean
 * 不存在会导致启动失败），业务方引入依赖但不启用时零影响。
 *
 * @author jffast
 */
@RestController
@ConditionalOnProperty(name = "jffast.oauth2.client.enabled", havingValue = "true")
@RequestMapping("/oauth2/client/config")
@Tag(name = "OAuth2客户端接入配置")
public class OAuth2ClientConfigController {

    private final OAuth2ClientConfigService configService;
    private final OAuth2ClientService clientService;

    /** 是否暴露调试令牌接口，默认关闭；用 @Value 而非注入 Properties Bean，避免 Starter 关闭时控制器装配失败 */
    @Value("${jffast.oauth2.client.expose-token:false}")
    private boolean exposeToken;

    public OAuth2ClientConfigController(OAuth2ClientConfigService configService, OAuth2ClientService clientService) {
        this.configService = configService;
        this.clientService = clientService;
    }

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:oauth2client:view")
    @Operation(summary = "接入配置分页")
    public R page(OAuth2ClientConfigQueryRequest req) {
        return R.success(configService.page(req));
    }

    @GetMapping("/list")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:oauth2client:view")
    @Operation(summary = "接入配置全量列表")
    public R list() {
        return R.success(configService.listAll());
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:oauth2client:view")
    @Operation(summary = "接入配置详情")
    public R detail(@PathVariable("id") Long id) {
        return R.success(configService.getById(id));
    }

    @SLog(title = "新增OAuth2客户端接入配置")
    @PostMapping("/create")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:oauth2client:add")
    @Operation(summary = "新增接入配置")
    public R create(@RequestBody @Valid OAuth2ClientConfigRequest req) {
        OAuth2ClientConfig cfg = BeanUtil.copyProperties(req, OAuth2ClientConfig.class);
        cfg.setId(IdUtils.snowflakeShort());
        configService.saveOrUpdate(cfg);
        return R.success();
    }

    @SLog(title = "修改OAuth2客户端接入配置")
    @PutMapping("/update")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:oauth2client:update")
    @Operation(summary = "修改接入配置")
    public R update(@RequestBody @Valid OAuth2ClientConfigRequest req) {
        OAuth2ClientConfig cfg = BeanUtil.copyProperties(req, OAuth2ClientConfig.class);
        // 记录旧 provider：改名场景下旧标识的令牌缓存也要清掉
        OAuth2ClientConfig old = cfg.getId() != null ? configService.getById(cfg.getId()) : null;
        configService.saveOrUpdate(cfg);
        if (old != null && old.getProvider() != null && !old.getProvider().equals(cfg.getProvider())) {
            clientService.evict(old.getProvider());
        }
        if (cfg.getProvider() != null) {
            clientService.evict(cfg.getProvider());
        }
        return R.success();
    }

    @SLog(title = "删除OAuth2客户端接入配置")
    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:oauth2client:delete")
    @Operation(summary = "删除接入配置")
    public R delete(@PathVariable("id") Long id) {
        OAuth2ClientConfig cfg = configService.getById(id);
        configService.removeById(id);
        // 删除后同步清理该 provider 的令牌缓存，避免 Redis 残留到 TTL 结束
        if (cfg != null && cfg.getProvider() != null) {
            clientService.evict(cfg.getProvider());
        }
        return R.success();
    }

    @GetMapping("/token/{provider}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:oauth2client:token")
    @Operation(summary = "获取某provider访问令牌（需 jffast.oauth2.client.expose-token=true）")
    public R token(@PathVariable("provider") String provider) {
        if (!exposeToken) {
            throw new BusinessException("调试令牌接口未开放，请配置 jffast.oauth2.client.expose-token=true");
        }
        OAuth2ClientToken token = clientService.getToken(provider);
        return R.success(token.getAccessToken());
    }

    @SLog(title = "清除OAuth2客户端令牌缓存")
    @DeleteMapping("/evict/{provider}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:oauth2client:update")
    @Operation(summary = "清除指定provider的令牌缓存")
    public R evict(@PathVariable("provider") String provider) {
        clientService.evict(provider);
        return R.success();
    }
}