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
import cn.jfspeed.common.core.context.TenantScopeBridge;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.system.entity.SysConfig;
import cn.jfspeed.system.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统参数配置 服务实现
 *
 * <p><b>租户隔离由本类自己负责，基座不兜底。</b> Base 不注册
 * {@code TenantLineInnerInterceptor}，查询不会被自动追加 {@code tenant_id} 条件；
 * 因此这里的每一条读写都必须显式带上租户约束（见 {@link #applyTenantScope}），
 * 不能假设「SQL 层已经过滤」——这正是本类上一版只改缓存键、探针仍报
 * {@code 管理员跨租户读配置 leak=True} 的原因。</p>
 *
 * @author jffast
 */
@Slf4j
@Service
public class SysConfigService extends BaseService<SysConfigMapper, SysConfig> {

    /**
     * 平台配置行的租户取值：{@code tenant_id} 为 {@code NULL} 或平台占位 {@code 0}。
     * 见于种子数据（{@code sys_config.tenant_id=0}）与历史行（{@code NULL}）。
     */
    private static final Long PLATFORM_TENANT_ID = 0L;

    /**
     * 无登录态/无租户会话时的缓存桶标识（启动期、匿名、内部调用）。
     */
    private static final String NO_TENANT_SCOPE = "0";

    /**
     * 配置缓存：{@code <tenantScope>:<configKey>} -> configValue。
     *
     * <p><b>租户维度必须进键</b>：缓存键若只有 configKey，先被读到的那家配置会被所有租户复用。
     * 键只解决「不同租户不共用同一格」，<b>不产生数据过滤</b>——回源 SQL 必须另按租户约束，
     * 否则桶里装进去的仍是全租户并集（字典当初的「假隔离」就是这么来的）。
     */
    private static final Map<String, String> CONFIG_CACHE = new ConcurrentHashMap<>();

    /**
     * 请求级租户上下文桥（Pro 的多租户 Starter 才提供实现；Base / Cloud 为 {@code null}）。
     *
     * <p>为什么要它：Pro 的 {@code TenantLineInnerInterceptor} 按 <b>请求级租户上下文</b>
     * （{@code TenantContext}，超管可用 {@code X-Tenant-Id} 切视角）注入条件，而会话租户是登录时
     * 固定的另一个值。本类若只读会话租户，超管切视角时会出现「本类过滤 tenant_id=平台
     * AND 拦截器过滤 tenant_id=2」→ 恒空。这里优先取桥接的请求级租户，与拦截器同源；
     * Base / Cloud 没有该 Bean，退回会话租户。
     */
    @Autowired(required = false)
    private TenantScopeBridge tenantScopeBridge;

    /**
     * 初始化：<b>不做全量预热</b>。
     *
     * <p>启动期没有登录态、也没有请求级租户上下文，此时查询只能落平台视角，
     * 把多租户的行塞进同一个桶正是串租户的根源。改为首次读取时按租户懒加载：
     * {@link #getConfigValue} 未命中会走数据库，此时 SQL 已由本类显式按租户过滤，
     * 落桶口径与读取方一致。
     */
    @PostConstruct
    public void init() {
        log.info("系统配置缓存未预热：按租户首次读取时懒加载（避免启动期全量视角串租户）");
    }

    /**
     * 刷新配置缓存：只清理并重建<b>当前租户</b>的桶。
     *
     * <p>平台身份时平台行本身不落缓存（见 {@link #putCache}），因此只需清掉平台桶；
     * 不清其它租户的桶——它们的数据没有被本次刷新读到，清了也只是让各租户重新懒加载。
     */
    public void refreshCache() {
        Long tenantId = currentTenantId();
        if (isPlatform(tenantId)) {
            CONFIG_CACHE.keySet().removeIf(key -> key.startsWith(NO_TENANT_SCOPE + ":"));
            return;
        }
        String scope = String.valueOf(tenantId);
        CONFIG_CACHE.keySet().removeIf(key -> key.startsWith(scope + ":"));
        List<SysConfig> configs = list(scopedQuery(tenantId));
        for (SysConfig config : configs) {
            if (StrUtil.isNotBlank(config.getConfigKey()) && StrUtil.isNotBlank(config.getConfigValue())) {
                CONFIG_CACHE.put(cacheKeyOf(scope, config.getConfigKey()), config.getConfigValue());
            }
        }
    }

    /**
     * 分页查询参数配置（仅当前租户可见范围）
     */
    public PageRes<SysConfig> page(Integer pageNo, Integer pageSize, String configName, String configKey) {
        Long tenantId = currentTenantId();
        LambdaQueryWrapper<SysConfig> query = scopedQuery(tenantId);
        query.like(StrUtil.isNotBlank(configName), SysConfig::getConfigName, configName);
        query.like(StrUtil.isNotBlank(configKey), SysConfig::getConfigKey, configKey);
        query.orderByAsc(SysConfig::getSystemBuiltin).orderByDesc(SysConfig::getCreateTime);
        Page<SysConfig> page = this.page(new Page<>(pageNo, pageSize), query);
        return PageUtil.resultPageMp(page);
    }

    /**
     * 判断参数键名在当前租户视角下是否已存在（键的唯一性只在同租户内成立）
     */
    public boolean hasConfigKey(String configKey, Long id) {
        LambdaQueryWrapper<SysConfig> query = scopedQuery(currentTenantId());
        query.eq(SysConfig::getConfigKey, configKey);
        query.ne(id != null, SysConfig::getId, id);
        return baseMapper.selectCount(query) > 0;
    }

    /**
     * 按 id 读取配置（租户视角内；跨租户一律按「不存在」处理，不回显存在性）
     */
    @Override
    public SysConfig getById(Serializable id) {
        if (id == null) {
            return null;
        }
        LambdaQueryWrapper<SysConfig> query = scopedQuery(currentTenantId());
        query.eq(SysConfig::getId, id);
        return baseMapper.selectOne(query);
    }

    /**
     * 根据参数键名获取参数键值。
     *
     * <p>两条路径都受租户约束：命中缓存时桶已按租户分开（键 {@code <scope>:<key>}），
     * 未命中回源时 SQL 显式带 {@code tenant_id} 条件。
     */
    public String getConfigValue(String configKey) {
        if (StrUtil.isBlank(configKey)) {
            return null;
        }
        Long tenantId = currentTenantId();
        String cachedValue = CONFIG_CACHE.get(cacheKeyOf(scopeOf(tenantId), configKey));
        if (cachedValue != null) {
            return cachedValue;
        }
        LambdaQueryWrapper<SysConfig> query = scopedQuery(tenantId);
        query.eq(SysConfig::getConfigKey, configKey).last("limit 1");
        SysConfig config = baseMapper.selectOne(query);
        if (config == null) {
            return null;
        }
        putCache(tenantId, configKey, config.getConfigValue());
        return config.getConfigValue();
    }

    /**
     * 新增配置：租户列落当前会话/请求租户，平台身份落 {@code NULL}。
     */
    @Override
    public boolean save(SysConfig entity) {
        fillTenantOnCreate(entity);
        boolean result = super.save(entity);
        if (result && StrUtil.isNotBlank(entity.getConfigKey())) {
            putCache(entity.getTenantId(), entity.getConfigKey(), entity.getConfigValue());
        }
        return result;
    }

    /**
     * 修改配置：先校验目标行归属，再更新；键名变更时同时删除旧键缓存。
     */
    @Override
    public boolean updateById(SysConfig entity) {
        SysConfig old = entity.getId() == null ? null : baseMapper.selectById(entity.getId());
        if (old != null) {
            assertOwnedByCurrentTenant(old);
            // 租户列固定为行原值：实体上的 tenantId 不参与更新，避免把租户行改写成平台行
            entity.setTenantId(old.getTenantId());
        }
        boolean result = super.updateById(entity);
        if (!result) {
            return false;
        }
        if (old != null && StrUtil.isNotBlank(old.getConfigKey())
                && !old.getConfigKey().equals(entity.getConfigKey())) {
            evictCache(old.getConfigKey());
        }
        if (StrUtil.isNotBlank(entity.getConfigKey())) {
            putCache(currentTenantId(), entity.getConfigKey(), entity.getConfigValue());
        }
        return true;
    }

    /**
     * 主动清理指定配置键的本地缓存（只失效当前租户；平台动作时按所有桶的同名键失效）。
     */
    public void evictCache(String configKey) {
        if (StrUtil.isBlank(configKey)) {
            return;
        }
        Long tenantId = currentTenantId();
        if (isPlatform(tenantId)) {
            String suffix = ":" + configKey;
            CONFIG_CACHE.keySet().removeIf(key -> key.endsWith(suffix));
            return;
        }
        CONFIG_CACHE.remove(cacheKeyOf(scopeOf(tenantId), configKey));
    }

    /**
     * 删除配置：先校验目标行归属，再删除。
     */
    @Override
    public boolean removeById(Serializable id) {
        SysConfig config = id == null ? null : baseMapper.selectById(id);
        if (config != null) {
            assertOwnedByCurrentTenant(config);
        }
        boolean result = super.removeById(id);
        if (result && config != null && StrUtil.isNotBlank(config.getConfigKey())) {
            evictCache(config.getConfigKey());
        }
        return result;
    }

    /**
     * 当前租户：请求级上下文优先（与 Pro 的租户拦截器同源），其次会话租户，
     * 都没有即平台身份（{@code null}）。
     */
    private Long currentTenantId() {
        if (tenantScopeBridge != null) {
            Long requestTenantId = tenantScopeBridge.currentTenantId();
            if (requestTenantId != null) {
                return requestTenantId;
            }
        }
        LoginUser loginUser = LoginUtil.getLoginUser();
        return loginUser == null ? null : loginUser.getTenantId();
    }

    private static boolean isPlatform(Long tenantId) {
        return tenantId == null || Objects.equals(tenantId, PLATFORM_TENANT_ID);
    }

    private static String scopeOf(Long tenantId) {
        return isPlatform(tenantId) ? NO_TENANT_SCOPE : String.valueOf(tenantId);
    }

    private static String cacheKeyOf(String scope, String configKey) {
        return scope + ":" + configKey;
    }

    /**
     * 在这条查询上显式追加租户条件——<b>这是 Base 唯一的租户过滤手段</b>（无拦截器）。
     *
     * <p>租户身份：{@code tenant_id = 该租户}；平台身份：{@code tenant_id IS NULL OR = 0}。
     * 平台身份看不到任何租户行，租户也看不到别的租户，与 Pro 租户拦截器的严格相等口径一致。
     */
    private static LambdaQueryWrapper<SysConfig> scopedQuery(Long tenantId) {
        LambdaQueryWrapper<SysConfig> query = new LambdaQueryWrapper<>();
        applyTenantScope(query, tenantId);
        return query;
    }

    private static void applyTenantScope(LambdaQueryWrapper<SysConfig> query, Long tenantId) {
        if (isPlatform(tenantId)) {
            query.and(w -> w.isNull(SysConfig::getTenantId)
                    .or().eq(SysConfig::getTenantId, PLATFORM_TENANT_ID));
            return;
        }
        query.eq(SysConfig::getTenantId, tenantId);
    }

    /**
     * 写入租户列：只在为空时填，显式指定但与当前视角不符时拒绝（防止借请求体跨租户写）。
     */
    private void fillTenantOnCreate(SysConfig entity) {
        if (entity == null) {
            return;
        }
        Long tenantId = currentTenantId();
        if (isPlatform(tenantId)) {
            if (!isPlatform(entity.getTenantId())) {
                throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权为其它租户新增参数配置");
            }
            entity.setTenantId(null);
            return;
        }
        if (entity.getTenantId() != null && !Objects.equals(entity.getTenantId(), tenantId)) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权为其它租户新增参数配置");
        }
        entity.setTenantId(tenantId);
    }

    /**
     * 目标行归属校验：平台身份只能动平台行，租户只能动自己的行。
     */
    private void assertOwnedByCurrentTenant(SysConfig config) {
        Long tenantId = currentTenantId();
        boolean owned = isPlatform(tenantId)
                ? isPlatform(config.getTenantId())
                : Objects.equals(config.getTenantId(), tenantId);
        if (!owned) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权操作其它租户的参数配置");
        }
    }

    /**
     * 写缓存：平台行不入桶——平台视角的行集合是 «NULL 或 0»，与租户桶语义不同，
     * 混进固定桶就会被任意租户读到。平台身份每次回源，代价可接受。
     */
    private static void putCache(Long tenantId, String configKey, String configValue) {
        if (StrUtil.isBlank(configKey) || configValue == null || isPlatform(tenantId)) {
            return;
        }
        CONFIG_CACHE.put(cacheKeyOf(scopeOf(tenantId), configKey), configValue);
    }
}
