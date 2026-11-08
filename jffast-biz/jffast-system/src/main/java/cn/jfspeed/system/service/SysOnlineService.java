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

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.system.domain.vo.SysOnlineVO;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.mapper.SysRoleMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 在线用户 服务
 *
 * @author jffast
 */
@Service
public class SysOnlineService {

    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    /**
     * 查询全部在线用户（按登录账号去重，按登录时间倒序）。
     */
    public List<SysOnlineVO> onlineList() {
        List<String> tokenList = StpUtil.searchTokenValue("", 0, -1, false);
        if (CollUtil.isEmpty(tokenList)) {
            return List.of();
        }

        // 同一账号可能持有多个 token，先按 loginId 去重，避免重复读取会话
        Set<Long> loginIds = new LinkedHashSet<>();
        for (String token : tokenList) {
            Object loginIdObj = StpUtil.getLoginIdByToken(token);
            if (loginIdObj == null) {
                continue;
            }
            try {
                loginIds.add(Long.valueOf(loginIdObj.toString()));
            } catch (NumberFormatException ignored) {
                // 非数字 loginId 不属于本系统的在线用户
            }
        }

        List<OnlineEntry> entries = new ArrayList<>(loginIds.size());
        for (Long loginId : loginIds) {
            SaSession session;
            try {
                session = StpUtil.getSessionByLoginId(loginId);
            } catch (Exception e) {
                continue;
            }
            SysOnlineVO vo = new SysOnlineVO();
            vo.setLoginId(loginId);

            Object userObj = session.get("user");
            if (userObj instanceof SysUser user) {
                vo.setUserId(user.getId());
                vo.setUserName(user.getUserName());
                vo.setNickName(user.getNickName());
                vo.setAvatar(user.getAvatar());
                vo.setTenantId(user.getTenantId());
            }

            long createTime = session.getCreateTime();
            vo.setLoginTime(createTime > 0 ? DateUtil.formatDateTime(new Date(createTime)) : "");

            Object loginIpObj = session.get("loginIp");
            String loginIp = loginIpObj == null ? null : loginIpObj.toString();
            vo.setLoginIp(StrUtil.isNotBlank(loginIp) ? loginIp : "未知");
            entries.add(new OnlineEntry(vo, createTime));
        }

        entries.sort(Comparator.comparingLong(OnlineEntry::createTime).reversed());
        return entries.stream().map(OnlineEntry::vo).toList();
    }

    private record OnlineEntry(SysOnlineVO vo, long createTime) {
    }

    /**
     * 强制下线指定登录账号。
     *
     * <p>越权边界（与「可见范围」同一套现有概念，不新造）：
     * <ul>
     *   <li>超管：可踢任何人，但不可踢自己（会话自锁）。</li>
     *   <li>非超管：只能踢<b>同租户</b>且<b>非超管</b>的会话；踢超管、跨租户一律拒绝。</li>
     * </ul>
     * 修前只校验「不能踢自己」：持有 {@code system:online:kick} 的普通账号能把超管踢下线。
     */
    public void kick(Long loginId) {
        assertKickAllowed(loginId);
        StpUtil.kickout(loginId);
    }

    /**
     * 批量强制下线：先逐条校验，全部通过才执行，避免「踢了一半才发现越权」。
     */
    public void kickBatch(List<Long> loginIds) {
        if (CollUtil.isEmpty(loginIds)) {
            return;
        }
        for (Long loginId : loginIds) {
            assertKickAllowed(loginId);
        }
        for (Long loginId : loginIds) {
            StpUtil.kickout(loginId);
        }
    }

    /**
     * 踢人前的越权校验。
     */
    private void assertKickAllowed(Long loginId) {
        Long current = LoginUtil.getLoginUserId();
        if (loginId == null) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "登录账号不能为空");
        }
        if (Objects.equals(current, loginId)) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "不能强制下线当前登录账号");
        }
        if (LoginUtil.isSuperAdmin()) {
            return;
        }
        SysUser target = sessionUser(loginId);
        if (target == null) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "目标用户不存在或无权操作");
        }
        if (isSuperAdminUser(target.getId())) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权强制下线超级管理员");
        }
        LoginUser me = LoginUtil.getLoginUser();
        Long myTenantId = me == null ? null : me.getTenantId();
        if (!Objects.equals(myTenantId, target.getTenantId())) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权强制下线其它租户的用户");
        }
    }

    /**
     * 取目标登录账号的会话用户；取不到即视为不可操作（fail-closed）。
     */
    private SysUser sessionUser(Long loginId) {
        try {
            SaSession session = StpUtil.getSessionByLoginId(loginId);
            Object userObj = session == null ? null : session.get("user");
            return userObj instanceof SysUser user ? user : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 目标用户是否超管：口径与 {@code LoginUtil#isSuperAdmin()} 一致
     * （{@code role_key = super_admin} 或内置超管角色 ID）。
     */
    private boolean isSuperAdminUser(Long userId) {
        if (userId == null) {
            return false;
        }
        LambdaQueryWrapper<SysUserRole> query = new LambdaQueryWrapper<>();
        query.eq(SysUserRole::getUserId, userId);
        List<Long> roleIds = sysUserRoleMapper.selectList(query).stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .toList();
        if (roleIds.isEmpty()) {
            return false;
        }
        if (roleIds.contains(BaseConstant.ROLE_ID_SUPER_ADMIN_LONG)) {
            return true;
        }
        List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
        return roles != null && roles.stream()
                .anyMatch(role -> BaseConstant.ROLE_SUPER_ADMIN.equals(role.getRoleKey()));
    }
}
