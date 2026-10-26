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
package cn.jfspeed.system.service;

import cn.jfspeed.security.annotation.DataPermission;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 受数据权限约束的用户存在性查询。
 * <p>
 * 独立 Bean 的原因：{@code @DataPermission} 是 AOP 切面，同类内自调用不会经过代理；
 * 目标存在性校验必须真正带数据权限过滤，不能退化成按主键直查。方法内不调用其他 Mapper，
 * 保证切面注入的 {@code d.id}/{@code u.id} 片段只作用于这一条 SQL。
 *
 * @author jffast
 */
@Component
public class SysUserScopeQuery {

    @Resource
    private SysUserMapper sysUserMapper;

    /**
     * 在当前登录用户的数据权限范围内按主键查询用户；范围外或不存在返回 null
     */
    @DataPermission(deptField = "d.id", userField = "u.id")
    public SysUser findVisibleUser(Long id) {
        if (id == null) {
            return null;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        List<SysUser> users = sysUserMapper.list(params);
        return users.isEmpty() ? null : users.get(0);
    }
}
