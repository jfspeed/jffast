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
import cn.jfspeed.common.core.annotation.SensitiveMask;
import cn.jfspeed.common.core.util.SensitiveMaskUtil;
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
        // merge 函数是必须的：业务侧扩展出两个 getType() 相同的 Bean 时，无 merge 的 toMap 会在
        // 构造期抛 IllegalStateException: Duplicate key，而异常里不含具体类名 —— 表现为「装了个
        // 自定义通道，整个应用起不来且日志指不出是谁」。这里显式保留第一个并告警指名。
        this.channelMap = channels.stream()
                .collect(Collectors.toMap(NotificationChannel::getType, Function.identity(), (first, second) -> {
                    log.warn("检测到重复的通知通道类型 {}：保留 {}，忽略 {}（同一 type 只允许一个实现）",
                            first.getType(), first.getClass().getName(), second.getClass().getName());
                    return first;
                }));
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
                    // target 是手机号/邮箱这类 PII，直接拼进 ERROR 日志等于把用户信息写进日志系统
                    // （日志通常比业务库流传更广、保留更久）。统一走 maskTarget 脱敏。
                    log.error("通知通道投递失败：type={}, userId={}, target={}", type, user.getId(), maskTarget(target), e);
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

    /**
     * 投递目标（手机号 / 邮箱）的日志脱敏。
     *
     * <p>先用 {@link SensitiveMaskUtil#maskText} 按正则识别手机号/邮箱；识别不出时
     * （自定义通道可能不是这两种格式）退化成中间遮掩，避免整串明文落盘。
     */
    private static String maskTarget(String target) {
        if (StrUtil.isBlank(target)) {
            return target;
        }
        String masked = SensitiveMaskUtil.maskText(target);
        return StrUtil.equals(masked, target)
                ? SensitiveMaskUtil.mask(target, SensitiveMask.MaskType.MIDDLE_MASK)
                : masked;
    }
}
