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
package cn.jfspeed.flow.resolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.jfspeed.flowable.constant.FlowConstant;
import cn.jfspeed.flowable.resolver.AssigneeResolver;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.service.SysRoleService;
import cn.jfspeed.system.service.SysUserService;

/**
 * jffast 审批人解析器实现。
 * <p>
 * 将 role/dept 类型的审批人配置解析为实际用户 ID 列表：
 * <ul>
 *   <li>{@code role}：value 为角色 key（如 {@code manager}）或角色 ID（逗号分隔）
 *       → 先归一为角色 ID，再按 {@code sys_user.role_id} 精确匹配</li>
 *   <li>{@code dept}：value 为部门 ID 列表（逗号分隔）→ 查 {@code sys_user.dept_id in (deptIds)} 的用户</li>
 * </ul>
 * SysUser 的 roleId 字段为字符串（可能多角色逗号分隔），用 {@code FIND_IN_SET} 精确匹配，
 * 不能用 LIKE：LIKE 的 1 会命中 11、21，导致审批人范围被放大。
 *
 * @author jffast
 */
@Component
public class JfFastAssigneeResolver implements AssigneeResolver {

    private static final Logger log = LoggerFactory.getLogger(JfFastAssigneeResolver.class);

    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;

    public JfFastAssigneeResolver(SysUserService sysUserService, SysRoleService sysRoleService) {
        this.sysUserService = sysUserService;
        this.sysRoleService = sysRoleService;
    }

    @Override
    public List<String> resolveUserIds(String type, String value) {
        List<String> values = split(value);
        if (values.isEmpty()) {
            return values;
        }
        List<String> userIds = new ArrayList<>();
        if (FlowConstant.ASSIGNEE_TYPE_ROLE.equals(type)) {
            // 文档约定写角色 key（manager），历史页面也可能存角色 ID，两种都归一为 ID。
            for (String roleValue : values) {
                Long roleId = resolveRoleId(roleValue);
                if (roleId == null) {
                    log.warn("审批人角色不存在，已跳过：{}", roleValue);
                    continue;
                }
                List<SysUser> users = sysUserService.list(new LambdaQueryWrapper<SysUser>()
                        .apply("FIND_IN_SET({0}, role_id)", roleId)
                        .eq(SysUser::getState, 1));
                for (SysUser user : users) {
                    String uid = String.valueOf(user.getId());
                    if (!userIds.contains(uid)) {
                        userIds.add(uid);
                    }
                }
            }
        } else if (FlowConstant.ASSIGNEE_TYPE_DEPT.equals(type)) {
            // 按部门查用户：sys_user.dept_id = deptId
            List<Long> deptIds = new ArrayList<>();
            for (String deptId : values) {
                try {
                    deptIds.add(Long.parseLong(deptId));
                } catch (NumberFormatException e) {
                    log.warn("部门 ID 格式错误：{}", deptId);
                }
            }
            if (!deptIds.isEmpty()) {
                List<SysUser> users = sysUserService.list(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getDeptId, deptIds)
                        .eq(SysUser::getState, 1));
                for (SysUser user : users) {
                    String uid = String.valueOf(user.getId());
                    if (!userIds.contains(uid)) {
                        userIds.add(uid);
                    }
                }
            }
        }
        return userIds;
    }

    /**
     * 角色 key（manager）→ 角色 ID；纯数字按 ID 原样返回（兼容存量配置）。
     */
    private Long resolveRoleId(String value) {
        if (value.chars().allMatch(Character::isDigit)) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.warn("角色 ID 格式错误：{}", value);
                return null;
            }
        }
        SysRole role = sysRoleService.getOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleKey, value)
                .last("limit 1"), false);
        return role == null ? null : role.getId();
    }

    private List<String> split(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String part : Arrays.asList(value.split(","))) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
