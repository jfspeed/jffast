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
package cn.jfspeed.system.notify;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.system.constant.NotificationConstant;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.mapper.SysUserMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 通知通道分发器：把一条通知按请求的通道（email/sms）投递给一批用户。
 * <p>
 * 站内信/落库/WebSocket 不走这里（见 {@code SysNotificationService}）；本组件只负责
 * 额外通道，且<b>尽力投递</b>：单个目标失败只记录日志，不影响站内信与其它收件人。
 * 业务模块可直接注入本组件做定向邮件/短信（如密码重置、验证码）。
 *
 * @author jffast
 */
@Slf4j
@Component
public class NotificationChannelDispatcher {

    private final SysUserMapper sysUserMapper;
    private final Map<String, NotificationChannel> channelMap;

    public NotificationChannelDispatcher(SysUserMapper sysUserMapper, List<NotificationChannel> channels) {
        this.sysUserMapper = sysUserMapper;
        this.channelMap = channels.stream()
                .collect(Collectors.toMap(NotificationChannel::getType, Function.identity()));
    }

    /**
     * 按通道投递通知。
     *
     * @param userIds  接收人用户 ID
     * @param title    标题
     * @param content  内容
     * @param channels 通道列表（逗号/分号/空格分隔，如 {@code email,sms}）；为空或只有 in_app 时直接返回
     */
    public void dispatch(Collection<Long> userIds, String title, String content, String channels) {
        Set<String> types = parseChannels(channels);
        if (types.isEmpty() || userIds == null || userIds.isEmpty()) {
            return;
        }
        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>().in(SysUser::getId, userIds));
        for (String type : types) {
            NotificationChannel channel = channelMap.get(type);
            if (channel == null) {
                log.warn("通知通道未知，跳过：type={}", type);
                continue;
            }
            if (!channel.isEnabled()) {
                log.warn("通知通道未启用，跳过：type={}（检查 jffast.system.notification.channel.* 配置）", type);
                continue;
            }
            for (SysUser user : users) {
                String target = resolveTarget(type, user);
                if (StrUtil.isBlank(target)) {
                    log.debug("用户无{}目标地址，跳过：userId={}", type, user.getId());
                    continue;
                }
                try {
                    channel.send(target, title, content);
                } catch (Exception e) {
                    log.error("通知通道投递失败：type={}, userId={}, target={}", type, user.getId(), target, e);
                }
            }
        }
    }

    /**
     * 解析通道列表：忽略 in_app（站内信固定发送）。
     */
    static Set<String> parseChannels(String channels) {
        if (StrUtil.isBlank(channels)) {
            return Set.of();
        }
        return Arrays.stream(channels.split("[,;\\s]+"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .filter(c -> !NotificationConstant.CHANNEL_IN_APP.equalsIgnoreCase(c))
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveTarget(String type, SysUser user) {
        if (NotificationConstant.CHANNEL_EMAIL.equals(type)) {
            return user.getEmail();
        }
        if (NotificationConstant.CHANNEL_SMS.equals(type)) {
            return user.getPhone();
        }
        return null;
    }
}
