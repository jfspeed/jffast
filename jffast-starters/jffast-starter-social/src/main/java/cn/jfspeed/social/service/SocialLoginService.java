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
package cn.jfspeed.social.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.social.config.SocialProperties;
import cn.jfspeed.social.domain.SocialBind;
import cn.jfspeed.social.domain.SocialBindTicket;
import cn.jfspeed.social.domain.SocialLoginResult;
import cn.jfspeed.social.domain.SocialState;
import cn.jfspeed.social.domain.SocialUser;
import cn.jfspeed.social.mapper.SocialBindMapper;
import cn.jfspeed.social.provider.SocialLoginProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author jffast
 */
@Slf4j
public class SocialLoginService {

    /** 授权 state 有效期：10 分钟 */
    private static final long STATE_TTL_MILLIS = 10 * 60 * 1000L;

    /** 绑定票据有效期：5 分钟 */
    private static final long BIND_TICKET_TTL_MILLIS = 5 * 60 * 1000L;

    private final SocialProperties socialProperties;
    private final SocialBindMapper socialBindMapper;
    private final Map<String, SocialLoginProvider> providerMap = new HashMap<>();

    public SocialLoginService(SocialProperties socialProperties, SocialBindMapper socialBindMapper,
                              List<SocialLoginProvider> providers) {
        this.socialProperties = socialProperties;
        this.socialBindMapper = socialBindMapper;
        providers.forEach(p -> {
            providerMap.put(p.getProviderName(), p);
            log.info("注册社交登录 Provider: {}", p.getProviderName());
        });
    }

    public String getAuthorizationUrl(String provider, String targetUri) {
        String normalizedProvider = normalizeProvider(provider);
        SocialLoginProvider loginProvider = getProvider(normalizedProvider);
        SocialState state = new SocialState();
        state.setRandom(UUID.randomUUID().toString().replace("-", ""));
        state.setProvider(normalizedProvider);
        state.setTargetUri(targetUri);
        state.setExpiresAt(System.currentTimeMillis() + STATE_TTL_MILLIS);
        return loginProvider.getAuthorizationUrl(state.encode(resolveStateSecret()));
    }

    public SocialLoginResult callback(String code, String state) {
        if (StrUtil.isBlank(code)) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "授权码不能为空");
        }
        SocialState socialState = SocialState.decode(state, resolveStateSecret());
        SocialLoginProvider loginProvider = getProvider(socialState.getProvider());

        String accessToken = loginProvider.getAccessToken(code);
        if (StrUtil.isBlank(accessToken)) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "获取 access_token 失败");
        }

        SocialUser socialUser = loginProvider.getUserInfoByCode(code, accessToken);
        if (socialUser == null || StrUtil.isBlank(socialUser.getOpenId())) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "获取用户信息失败");
        }
        socialUser.setProvider(socialState.getProvider().toLowerCase());

        SocialLoginResult result = new SocialLoginResult();
        result.setSocialUser(socialUser);
        result.setBindTicket(issueBindTicket(socialUser));
        result.setTargetUri(socialState.getTargetUri());

        SocialBind existBind = findByProviderAndOpenId(socialState.getProvider().toLowerCase(), socialUser.getOpenId());
        if (existBind != null) {
            result.setBound(true);
            result.setUserId(existBind.getUserId());
        } else {
            result.setBound(false);
        }

        return result;
    }

    /**
     * 绑定社交账号：只接受回调签发的一次性票据，不再信任客户端提交的 {@link SocialUser}。
     */
    public void bind(String ticket) {
        LoginUser user = LoginUtil.getLoginUser();
        if (user == null) {
            throw new BusinessException(AuthErrorCode.NOT_LOGIN);
        }

        SocialBindTicket bindTicket = SocialBindTicket.decode(ticket, resolveStateSecret());
        if (bindTicket.getUserId() != null && !bindTicket.getUserId().equals(user.getId())) {
            throw new BusinessException(403, "绑定票据与当前登录用户不匹配");
        }

        SocialUser socialUser = bindTicket.toSocialUser();
        SocialBind existBind = findByProviderAndOpenId(socialUser.getProvider(), socialUser.getOpenId());
        if (existBind != null) {
            if (existBind.getUserId().equals(user.getId())) {
                throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "该社交账号已绑定到当前用户");
            }
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "该社交账号已被其他用户绑定");
        }

        SocialBind selfBind = findByUserIdAndProvider(user.getId(), socialUser.getProvider());
        if (selfBind != null) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "当前用户已绑定该平台的社交账号，请先解绑");
        }

        SocialBind bind = new SocialBind();
        bind.setUserId(user.getId());
        bind.setProvider(socialUser.getProvider());
        bind.setOpenId(socialUser.getOpenId());
        bind.setUsername(socialUser.getUsername());
        bind.setNickname(socialUser.getNickname());
        bind.setAvatarUrl(socialUser.getAvatarUrl());
        socialBindMapper.insert(bind);

        log.info("用户 {} 绑定社交账号 {}({})", user.getUserName(), socialUser.getProvider(), socialUser.getOpenId());
    }

    public void unbind(String provider) {
        LoginUser user = LoginUtil.getLoginUser();
        if (user == null) {
            throw new BusinessException(AuthErrorCode.NOT_LOGIN);
        }

        SocialBind bind = findByUserIdAndProvider(user.getId(), provider.toLowerCase());
        if (bind == null) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "未绑定该平台的社交账号");
        }

        socialBindMapper.deleteById(bind.getId());
        log.info("用户 {} 解绑社交账号 {}", user.getUserName(), provider);
    }

    public List<SocialBind> listBinds() {
        LoginUser user = LoginUtil.getLoginUser();
        if (user == null) {
            throw new BusinessException(AuthErrorCode.NOT_LOGIN);
        }

        LambdaQueryWrapper<SocialBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SocialBind::getUserId, user.getId());
        return socialBindMapper.selectList(wrapper);
    }

    public SocialBind findByProviderAndOpenId(String provider, String openId) {
        LambdaQueryWrapper<SocialBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SocialBind::getProvider, provider);
        wrapper.eq(SocialBind::getOpenId, openId);
        return socialBindMapper.selectOne(wrapper);
    }

    public SocialBind findByUserIdAndProvider(Long userId, String provider) {
        LambdaQueryWrapper<SocialBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SocialBind::getUserId, userId);
        wrapper.eq(SocialBind::getProvider, provider);
        return socialBindMapper.selectOne(wrapper);
    }

    private SocialLoginProvider getProvider(String provider) {
        SocialLoginProvider loginProvider = providerMap.get(provider.toLowerCase());
        if (loginProvider == null) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "不支持的社交登录平台: " + provider + "，已注册: " + providerMap.keySet());
        }
        return loginProvider;
    }

    private String normalizeProvider(String provider) {
        if (StrUtil.isBlank(provider)) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "社交平台标识不能为空");
        }
        return provider.trim().toLowerCase();
    }

    private String issueBindTicket(SocialUser socialUser) {
        SocialBindTicket ticket = new SocialBindTicket();
        ticket.setProvider(socialUser.getProvider());
        ticket.setOpenId(socialUser.getOpenId());
        ticket.setUsername(socialUser.getUsername());
        ticket.setNickname(socialUser.getNickname());
        ticket.setAvatarUrl(socialUser.getAvatarUrl());
        ticket.setEmail(socialUser.getEmail());
        ticket.setUserId(LoginUtil.getLoginUserId());
        ticket.setExpiresAt(System.currentTimeMillis() + BIND_TICKET_TTL_MILLIS);
        return ticket.encode(resolveStateSecret());
    }

    /**
     * state 签名密钥：优先取 {@code jffast.social.state-secret}，未配置时由各 provider 的 client-secret
     * 按平台名排序派生——同一套配置在不同实例上得到相同密钥，避免负载均衡下回调落到别的实例验签失败。
     */
    private String resolveStateSecret() {
        if (StrUtil.isNotBlank(socialProperties.getStateSecret())) {
            return socialProperties.getStateSecret();
        }
        boolean hasClientSecret = socialProperties.getProviders().values().stream()
                .anyMatch(config -> StrUtil.isNotBlank(config.getClientSecret()));
        if (!hasClientSecret) {
            throw new BusinessException(500,
                    "社交登录未配置签名密钥，请设置 jffast.social.state-secret 或 provider client-secret");
        }
        return socialProperties.getProviders().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ":" + StrUtil.nullToEmpty(entry.getValue().getClientSecret()))
                .collect(Collectors.joining("|"));
    }
}
