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

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.system.entity.SysConfig;
import cn.jfspeed.system.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统参数配置 服务实现
 *
 * @author jffast
 */
@Slf4j
@Service
public class SysConfigService extends BaseService<SysConfigMapper, SysConfig> {

    /**
     * 配置缓存：configKey -> configValue
     */
    private static final Map<String, String> CONFIG_CACHE = new ConcurrentHashMap<>();

    /**
     * 初始化时加载所有配置到缓存
     */
    @PostConstruct
    public void init() {
        refreshCache();
        log.info("系统配置缓存初始化完成，共加载 {} 条配置", CONFIG_CACHE.size());
    }

    /**
     * 刷新配置缓存
     */
    public void refreshCache() {
        CONFIG_CACHE.clear();
        List<SysConfig> configs = list();
        for (SysConfig config : configs) {
            if (StrUtil.isNotBlank(config.getConfigKey()) && StrUtil.isNotBlank(config.getConfigValue())) {
                CONFIG_CACHE.put(config.getConfigKey(), config.getConfigValue());
            }
        }
    }

    /**
     * 分页查询参数配置
     */
    public PageRes<SysConfig> page(Integer pageNo, Integer pageSize, String configName, String configKey) {
        LambdaQueryWrapper<SysConfig> query = new LambdaQueryWrapper<>();
        query.like(StrUtil.isNotBlank(configName), SysConfig::getConfigName, configName);
        query.like(StrUtil.isNotBlank(configKey), SysConfig::getConfigKey, configKey);
        query.orderByAsc(SysConfig::getSystemBuiltin).orderByDesc(SysConfig::getCreateTime);
        Page<SysConfig> page = this.page(new Page<>(pageNo, pageSize), query);
        return PageUtil.resultPageMp(page);
    }

    /**
     * 判断参数键名是否已存在
     */
    public boolean hasConfigKey(String configKey, Long id) {
        LambdaQueryWrapper<SysConfig> query = new LambdaQueryWrapper<>();
        query.eq(SysConfig::getConfigKey, configKey);
        query.ne(id != null, SysConfig::getId, id);
        return baseMapper.selectCount(query) > 0;
    }

    /**
     * 根据参数键名获取参数键值（优先从缓存读取）
     */
    public String getConfigValue(String configKey) {
        if (StrUtil.isBlank(configKey)) {
            return null;
        }
        // 先从缓存读取
        String cachedValue = CONFIG_CACHE.get(configKey);
        if (cachedValue != null) {
            return cachedValue;
        }
        // 缓存未命中，从数据库读取
        LambdaQueryWrapper<SysConfig> query = new LambdaQueryWrapper<>();
        query.eq(SysConfig::getConfigKey, configKey).last("limit 1");
        SysConfig config = baseMapper.selectOne(query);
        return config != null ? config.getConfigValue() : null;
    }

    /**
     * 新增配置后刷新缓存
     */
    @Override
    public boolean save(SysConfig entity) {
        boolean result = super.save(entity);
        if (result && StrUtil.isNotBlank(entity.getConfigKey())) {
            CONFIG_CACHE.put(entity.getConfigKey(), entity.getConfigValue());
        }
        return result;
    }

    /**
     * 修改配置后刷新缓存；键名变更时同时删除旧键，避免旧键继续命中过期值。
     */
    @Override
    public boolean updateById(SysConfig entity) {
        SysConfig old = entity.getId() != null ? getById(entity.getId()) : null;
        boolean result = super.updateById(entity);
        if (!result) {
            return false;
        }
        if (old != null && StrUtil.isNotBlank(old.getConfigKey())
                && !old.getConfigKey().equals(entity.getConfigKey())) {
            CONFIG_CACHE.remove(old.getConfigKey());
        }
        if (StrUtil.isNotBlank(entity.getConfigKey())) {
            CONFIG_CACHE.put(entity.getConfigKey(), entity.getConfigValue());
        }
        return true;
    }

    /**
     * 主动清理指定配置键的本地缓存。
     */
    public void evictCache(String configKey) {
        if (StrUtil.isNotBlank(configKey)) {
            CONFIG_CACHE.remove(configKey);
        }
    }

    /**
     * 删除配置后刷新缓存
     */
    @Override
    public boolean removeById(java.io.Serializable id) {
        SysConfig config = getById(id);
        boolean result = super.removeById(id);
        if (result && config != null && StrUtil.isNotBlank(config.getConfigKey())) {
            CONFIG_CACHE.remove(config.getConfigKey());
        }
        return result;
    }
}
