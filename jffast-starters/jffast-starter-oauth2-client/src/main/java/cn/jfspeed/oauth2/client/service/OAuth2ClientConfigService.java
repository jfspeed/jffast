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
package cn.jfspeed.oauth2.client.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.oauth2.client.domain.dto.OAuth2ClientConfigQueryRequest;
import cn.jfspeed.oauth2.client.entity.OAuth2ClientConfig;
import cn.jfspeed.oauth2.client.mapper.OAuth2ClientConfigMapper;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * OAuth2 客户端接入配置服务。配置存数据库 sys_oauth2_client_config 表，页面可维护。
 * <p>
 * 直接持 Mapper（不继承 ServiceImpl），便于在 gated AutoConfiguration 中构造器注入。
 *
 * @author jffast
 */
public class OAuth2ClientConfigService {

    private final OAuth2ClientConfigMapper mapper;

    public OAuth2ClientConfigService(OAuth2ClientConfigMapper mapper) {
        this.mapper = mapper;
    }

    public PageRes<OAuth2ClientConfig> page(OAuth2ClientConfigQueryRequest req) {
        Page<OAuth2ClientConfig> page = new Page<>(req.getPageNo(), req.getPageSize());
        LambdaQueryWrapper<OAuth2ClientConfig> w = new LambdaQueryWrapper<OAuth2ClientConfig>()
                .like(StringUtils.hasText(req.getName()), OAuth2ClientConfig::getName, req.getName())
                .like(StringUtils.hasText(req.getProvider()), OAuth2ClientConfig::getProvider, req.getProvider())
                .orderByAsc(OAuth2ClientConfig::getProvider);
        Page<OAuth2ClientConfig> result = mapper.selectPage(page, w);
        result.getRecords().forEach(this::maskSecret);
        return PageUtil.resultPageMp(result);
    }

    public List<OAuth2ClientConfig> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<OAuth2ClientConfig>().orderByAsc(OAuth2ClientConfig::getProvider));
    }

    public OAuth2ClientConfig getById(Long id) {
        return mapper.selectById(id);
    }

    /** 查询启用状态的配置（含密钥），供换发令牌使用 */
    public OAuth2ClientConfig getEnabledByProvider(String provider) {
        OAuth2ClientConfig cfg = mapper.selectOne(new LambdaQueryWrapper<OAuth2ClientConfig>()
                .eq(OAuth2ClientConfig::getProvider, provider)
                .last("limit 1"));
        if (cfg == null) {
            throw new BusinessException("未找到 provider=" + provider + " 的 OAuth2 接入配置");
        }
        if (cfg.getStatus() == null || cfg.getStatus() != 1) {
            throw new BusinessException("provider=" + provider + " 的接入配置已禁用");
        }
        return cfg;
    }

    public void saveOrUpdate(OAuth2ClientConfig entity) {
        // 以「记录是否已存在」区分插入/更新：create 即便带入脱敏/雪花 id，旧行不存在则仍走 insert
        OAuth2ClientConfig old = entity.getId() != null ? mapper.selectById(entity.getId()) : null;
        if (old != null) {
            // 更新场景未传 clientSecret（留空）则沿用原值，避免明文回写
            if (!StringUtils.hasText(entity.getClientSecret())) {
                entity.setClientSecret(old.getClientSecret());
            }
        } else if (countByProvider(entity.getProvider()) > 0) {
            throw new BusinessException("provider=" + entity.getProvider() + " 已存在，请更换唯一标识");
        }
        if (!StringUtils.hasText(entity.getClientSecret())) {
            throw new BusinessException("clientSecret 不能为空");
        }
        if (old != null) {
            mapper.updateById(entity);
        } else {
            mapper.insert(entity);
        }
    }

    public void removeById(Long id) {
        mapper.deleteById(id);
    }

    private long countByProvider(String provider) {
        Long c = mapper.selectCount(new LambdaQueryWrapper<OAuth2ClientConfig>()
                .eq(OAuth2ClientConfig::getProvider, provider));
        return c == null ? 0 : c;
    }

    /** 管理列表返回时脱敏 clientSecret */
    private void maskSecret(OAuth2ClientConfig cfg) {
        String v = cfg.getClientSecret();
        if (StringUtils.hasText(v) && v.length() > 8) {
            cfg.setClientSecret(v.substring(0, 4) + "****" + v.substring(v.length() - 4));
        }
    }
}