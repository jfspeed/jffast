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

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.jfspeed.system.domain.dto.SysDictQueryRequest;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.system.entity.SysDict;
import cn.jfspeed.system.mapper.SysDictMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 字典 服务实现*
 *
 * @author jffast
 */
@Service
public class SysDictService extends BaseService<SysDictMapper, SysDict>{

    private static final String CACHE_DICT_TYPE = "jffast:dict:type:";
    /** 无登录态（启动期 / 无鉴权服务 / 匿名基础数据）的租户维度占位 */
    private static final String NO_TENANT_SCOPE = "0";
    private static final long CACHE_DICT_TTL = 3600;

    /**
     * 字典类型列表
     */
    public List<SysDict> dictTypeList(Map<String,String> map){
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("is_type",1);
        if (StrUtil.isNotBlank(map.get("name"))){
            queryWrapper.like("name",map.get("name"));
        }
        queryWrapper.orderByDesc("parent_id,sort");
        return baseMapper.selectList(queryWrapper);
    }

    /**
     * 字典项列*
     */
    public PageRes<SysDict> page(SysDictQueryRequest sysDictQuery) {
        LambdaQueryWrapper<SysDict> queryWrapper = buildDictItemQuery(sysDictQuery);
        queryWrapper.orderByAsc(SysDict::getParentId,SysDict::getSort);
        Page<SysDict> page = this.page(sysDictQuery.getPage(), queryWrapper);
        return PageUtil.resultPageMp(page);
    }

    /**
     * 字典项列表的查询条件（字典类型 = {@code is_type} 1，字典项 = 其它）。
     *
     * <p>{@code is_type} 列 DDL 允许 NULL（{@code DEFAULT NULL}），而 SQL 里 {@code is_type <> 1}
     * 对 NULL 行**不成立**（NULL 参与比较的结果是 UNKNOWN，不是 TRUE）⇒ 存量空值行会被整条
     * 过滤掉：它们在字典管理页里<b>完全看不见</b>——看不见就改不了，只能去库里手工处理，
     * 而代码另一处（{@link SysDict#isDictType}）又把 NULL 当作「不是字典类型」照常处理，
     * 两处口径不一致。
     *
     * <p>所以这里显式把 NULL 行纳入字典项，与 {@link SysDict#isDictType} 的判定保持一致：
     * NULL 既不是字典类型，就按字典项对待。
     */
    static LambdaQueryWrapper<SysDict> buildDictItemQuery(SysDictQueryRequest sysDictQuery) {
        LambdaQueryWrapper<SysDict> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(sysDictQuery.getName()),SysDict::getName, sysDictQuery.getName());
        queryWrapper.like(StrUtil.isNotBlank(sysDictQuery.getCode()),SysDict::getCode, sysDictQuery.getCode());
        queryWrapper.eq(sysDictQuery.getParentId()!=null,SysDict::getParentId, sysDictQuery.getParentId());
        queryWrapper.and(w -> w.ne(SysDict::getIsType, 1).or().isNull(SysDict::getIsType));
        return queryWrapper;
    }

    private List<SysDict> list(String dictName, String dictCode){
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        if (StrUtil.isNotBlank(dictName)){
            queryWrapper.likeRight("name",dictName);
        }
        if (StrUtil.isNotBlank(dictCode)){
            queryWrapper.likeRight("code",dictCode);
        }
        queryWrapper.select("name,code");
        queryWrapper.orderByDesc("create_time");
        return baseMapper.selectList(queryWrapper);
    }

    /**
     * 生成字段名称或字典代*
     */
    public String createNameOrCode(String nameOrCode,String create){
        int strIndex = nameOrCode.lastIndexOf("_");
        if (strIndex != -1){
            nameOrCode = nameOrCode.substring(strIndex+1);
            boolean isInt = NumberUtil.isInteger(nameOrCode);
            if (isInt){
                int i = Integer.parseInt(nameOrCode)+1;
                create = create + "_" + i;
            }else {
                create =  create+ "_" + 1;
            }
        }else {
            create =  create+ "_" + 1;
        }
        return create;
    }

    /**
     * 生成字典名称
     */
    public String createName(String dictName){
        int index = dictName.lastIndexOf("_");
        if (index != -1){
            dictName = dictName.substring(0,index);
        }
        List<SysDict> list = list(dictName, null);
        if (!list.isEmpty()) {
            return createNameOrCode(list.get(0).getName(),dictName);
        }
        return dictName;
    }

    /**
     * 生成字典代码
     */
    public String createCode(String dictCode){
        int index = dictCode.lastIndexOf("_");
        if (index != -1){
            dictCode = dictCode.substring(0,index);
        }
        List<SysDict> list = list(null, dictCode);
        if (!list.isEmpty()) {
            return createNameOrCode(list.get(0).getCode(),dictCode);
        }
        return dictCode;
    }

    /**
     * 判断字典名称和编码是否已存在（**全表范围**：字典类型与字典项一起比）。
     *
     * <p>保留此重载是为了兼容「我要不要换个名字」的探测接口——它只回答「这个名字/编码在
     * 字典里出现过没有」，不区分类型与字典项。写入路径请用带作用域的重载。
     */
    public boolean hasDictByNameAndCode(String dictName, String dictCode, Long id) {
        return countByNameAndCode(dictName, dictCode, id, null, null, false);
    }

    /**
     * 判断字典名称和编码是否已存在（**同父级 + 同类别**范围）。
     *
     * <p>字典项归属于某个字典类型，不同类型下允许出现同名/同编码的字典项；字典类型都在根
     * （{@code parent_id = 0}）下，类型名/类型编码全局唯一。此前查重不带作用域，于是
     * 「A 类型下的字典项『启用』」会把「B 类型下的字典项『启用』」也判成重复
     * ——两个本该共存的数据互相挡路。
     *
     * <p>{@code is_type} 的口径与 {@link SysDict#isDictType} 一致：{@code 1} 是字典类型，
     * 其余（含 {@code NULL} 存量行）是字典项；查字典项时用 {@code is_type <> 1 OR is_type IS NULL}
     * 把 {@code NULL} 行一并纳入（SQL 里 {@code <>} 对 NULL 不成立，漏掉就是查不出存量空值行）。
     */
    public boolean hasDictByNameAndCode(String dictName, String dictCode, Long id, Long parentId, Integer isType) {
        return countByNameAndCode(dictName, dictCode, id, parentId, isType, true);
    }

    private boolean countByNameAndCode(String dictName, String dictCode, Long id,
                                       Long parentId, Integer isType, boolean scoped) {
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        if (StrUtil.isNotBlank(dictCode)){
            queryWrapper.eq("code",dictCode);
        }
        if (StrUtil.isNotBlank(dictName)){
            queryWrapper.eq("name",dictName);
        }
        if (scoped) {
            if (parentId != null) {
                queryWrapper.eq("parent_id", parentId);
            }
            if (Integer.valueOf(1).equals(isType)) {
                queryWrapper.eq("is_type", 1);
            } else {
                queryWrapper.and(w -> w.ne("is_type", 1).or().isNull("is_type"));
            }
        }
        queryWrapper.ne(id!=null,"id",id);
        long count = baseMapper.selectCount(queryWrapper);
        return count > 0;
    }

    /**
     * 判断字典类型是否存在子级
     */
    public boolean hasDictByParent(Long id) {
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id",id);
        long count = baseMapper.selectCount(queryWrapper);
        return count > 0;
    }

    /**
     * 根据字典编码查询**字典类型**。
     *
     * <p>限定 {@code is_type = 1}：同一张表里既有字典类型（{@code is_type = 1}）也有字典项
     * （其余，含存量 {@code NULL} 行），而字典项的编码由名称拼音生成，与字典类型的编码相撞
     * 是常态。此前这里不限定 {@code is_type}，撞码时 {@code selectOne} 直接抛
     * {@code TooManyResultsException} ⇒ 字典下拉接口 500。
     *
     * <p>取不到就返回 {@code null}；同名类型若有多条（存量脏数据），按 id 升序取第一条，
     * 不抛异常。
     */
    public SysDict getByDictCode(String dictCode){
        if (StrUtil.isBlank(dictCode)) {
            return null;
        }
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("code",dictCode);
        queryWrapper.eq("is_type",1);
        queryWrapper.orderByAsc("id");
        List<SysDict> list = baseMapper.selectList(queryWrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 根据字典类型编码查询字典项列表（带缓存）。
     *
     * <p>筛选口径与字典管理页的 {@link #buildDictItemQuery} 保持一致：{@code is_type} 为 NULL 的
     * 存量行按字典项处理。此前这里额外用 {@code is_type = '2'} 过滤，NULL 行同样被整条过滤掉，
     * 于是「管理页看得到、下拉框里没有」。
     */
    @SuppressWarnings("unchecked")
    public List<SysDict> getListByDictCode(String dictCode){
        if (StrUtil.isBlank(dictCode)) {
            return List.of();
        }
        String cacheKey = cacheKeyOf(dictCode);
        Object cached = RedisUtil.get(cacheKey);
        if (cached != null) {
            return (List<SysDict>) cached;
        }
        SysDict dict = getByDictCode(dictCode);
        if (dict == null) {
            return List.of();
        }
        LambdaQueryWrapper<SysDict> query = new LambdaQueryWrapper<>();
        query.eq(SysDict::getParentId, dict.getId());
        query.and(w -> w.ne(SysDict::getIsType, 1).or().isNull(SysDict::getIsType));
        // 只取渲染下拉框必需的字段：字典项含录入人/租户等审计列，不该随基础接口下发
        // （value 是字典项的业务值本身，下拉框要拿它当选中值，必须带上）
        query.select(SysDict::getId, SysDict::getName, SysDict::getCode, SysDict::getValue, SysDict::getSort);
        query.orderByAsc(SysDict::getSort);
        List<SysDict> list = baseMapper.selectList(query);
        RedisUtil.set(cacheKey, list, CACHE_DICT_TTL);
        return list;
    }

    /**
     * 清除字典缓存
     */
    public void clearDictCache(String dictCode) {
        RedisUtil.del(cacheKeyOf(dictCode));
    }

    /**
     * 字典项缓存键：{@code jffast:dict:type:<tenantId>:<code>}。
     *
     * <p><b>租户维度必须进键</b>：字典行本身按租户隔离（多租户形态下拦截器给查询追加
     * {@code tenant_id}），而缓存键不含租户时，先被读到的那家字典会被所有租户复用——
     * 属于跨租户数据串用。单租户口径下维度恒为 {@value #NO_TENANT_SCOPE}，键值与旧版相比
     * 只多了一段固定前缀。
     *
     * <p>租户标识取自登录用户会话（{@code LoginUser#tenantId}），无登录态时落
     * {@value #NO_TENANT_SCOPE}（启动期、无鉴权服务、匿名基础数据共用一桶，本就不参与租户过滤）。
     * 若多租户形态另有「请求级租户上下文」（例如超管按请求头切换租户视角），缓存桶仍以**会话租户**为准：
     * 最坏只是该账号在自己桶里留存上一次切换视角的条目（TTL 1 小时），不会把某租户的字典发给别的租户。
     */
    private static String cacheKeyOf(String dictCode) {
        LoginUser loginUser = LoginUtil.getLoginUser();
        Long tenantId = loginUser == null ? null : loginUser.getTenantId();
        String scope = tenantId == null ? NO_TENANT_SCOPE : String.valueOf(tenantId);
        return CACHE_DICT_TYPE + scope + ":" + dictCode;
    }

    /**
     * 清除所有字典缓存
     */
    public void clearAllDictCache() {
        String[] keys = RedisUtil.allKey(CACHE_DICT_TYPE + "*");
        if (keys != null && keys.length > 0) {
            RedisUtil.del(keys);
        }
    }

    /**
     * 清除一条字典行影响到的缓存。
     *
     * <p>键是**字典类型自己的 code**，所以字典项的任何增删改都要回溯到父级；而字典类型自身
     * 也在自己的键下缓存了成员列表。故这里既清它自己的 code，也清父级的 code。
     *
     * <p>改 / 删时**新旧父级都要清**（调用方传两遍）：字典项从 A 类型挪到 B 类型，只清 B
     * 会让 A 的缓存继续带着这个已经离开的成员。
     */
    private void clearCacheOf(SysDict... dicts) {
        for (SysDict dict : dicts) {
            if (dict == null) {
                continue;
            }
            if (StrUtil.isNotBlank(dict.getCode())) {
                clearDictCache(dict.getCode());
            }
            if (dict.getParentId() != null) {
                SysDict parent = getById(dict.getParentId());
                if (parent != null) {
                    clearDictCache(parent.getCode());
                }
            }
        }
    }

    @Override
    public boolean save(SysDict entity) {
        boolean result = super.save(entity);
        if (result) {
            clearCacheOf(entity);
        }
        return result;
    }

    @Override
    public boolean updateById(SysDict entity) {
        SysDict oldDict = getById(entity.getId());
        boolean result = super.updateById(entity);
        if (result) {
            // 新旧各清一遍：换父级时只清新的那个，旧父级的缓存会一直带着已迁走的成员
            clearCacheOf(oldDict, entity);
        }
        return result;
    }

    @Override
    public boolean removeById(java.io.Serializable id) {
        SysDict dict = getById(id);
        boolean result = super.removeById(id);
        if (result) {
            clearCacheOf(dict);
        }
        return result;
    }
}
