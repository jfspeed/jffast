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
import cn.jfspeed.system.domain.vo.SysOnlineVO;
import cn.jfspeed.system.entity.SysUser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 在线用户 服务
 *
 * @author jffast
 */
@Service
public class SysOnlineService {

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
     * 强制下线指定登录账号
     */
    public void kick(Long loginId) {
        StpUtil.kickout(loginId);
    }

    /**
     * 批量强制下线
     */
    public void kickBatch(List<Long> loginIds) {
        for (Long loginId : loginIds) {
            StpUtil.kickout(loginId);
        }
    }
}
