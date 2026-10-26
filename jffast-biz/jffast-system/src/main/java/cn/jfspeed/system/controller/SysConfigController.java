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
package cn.jfspeed.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.DistributedLock;
import cn.jfspeed.security.annotation.Idempotent;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.system.domain.dto.SysConfigRequest;
import cn.jfspeed.system.entity.SysConfig;
import cn.jfspeed.system.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

/**
 * @author jffast
 */
@RestController
@RequestMapping("/sys/config")
@Tag(name = "参数配置")
public class SysConfigController extends BaseController<SysConfigService, SysConfig> {

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:config:view")
    @Operation(summary = "分页查询")
    public R page(@RequestParam(defaultValue = "1") Integer pageNo,
                  @RequestParam(defaultValue = "10") Integer pageSize,
                  @RequestParam(required = false) String configName,
                  @RequestParam(required = false) String configKey) {
        return R.success(service.page(pageNo, pageSize, configName, configKey));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:config:view")
    @Operation(summary = "详情")
    public R detail(@PathVariable("id") Long id) {
        return R.success(service.getById(id));
    }

    @SLog(title = "新增参数配置")
    @PostMapping("/create")
    @RateLimit(value = "default")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:config:add")
    @Idempotent(expire = 5)
    @Operation(summary = "新增")
    public R create(@RequestBody @Valid SysConfigRequest req) {
        SysConfig entity = BeanUtil.copyProperties(req, SysConfig.class);
        if (service.hasConfigKey(entity.getConfigKey(), null)) {
            return R.error("参数键名已存在");
        }
        service.save(entity);
        return R.success();
    }

    @SLog(title = "修改参数配置")
    @PutMapping("/update")
    @DistributedLock(key = "'config:' + #req.id", expireTime = 10000)
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:config:update")
    @Idempotent(expire = 5)
    @Operation(summary = "修改")
    public R update(@RequestBody @Valid SysConfigRequest req) {
        if (req.getId() == null) {
            return R.error("参数配置 ID 不能为空");
        }
        SysConfig existing = service.getById(req.getId());
        if (existing == null) {
            return R.error("参数配置不存在");
        }
        SysConfig entity = BeanUtil.copyProperties(req, SysConfig.class);
        if (service.hasConfigKey(entity.getConfigKey(), entity.getId())) {
            return R.error("参数键名已存在");
        }
        // 键变更时旧缓存键必须清掉，否则按旧键读到的仍是过期值
        boolean keyChanged = !entity.getConfigKey().equals(existing.getConfigKey());
        if (!service.updateById(entity)) {
            return R.error("参数配置修改失败");
        }
        if (keyChanged) {
            service.evictCache(existing.getConfigKey());
        }
        return R.success();
    }

    @SLog(title = "删除参数配置")
    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:config:delete")
    @Operation(summary = "删除")
    public R delete(@PathVariable("id") Long id) {
        SysConfig config = service.getById(id);
        if (config != null && config.getSystemBuiltin() != null && config.getSystemBuiltin() == 1) {
            return R.error("系统内置参数不可删除");
        }
        service.removeById(id);
        return R.success();
    }

    @GetMapping("/getValue")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:config:view")
    @Operation(summary = "根据参数键名获取参数值")
    public R getValue(@RequestParam String configKey) {
        return R.success(service.getConfigValue(configKey));
    }
}
