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
     * 判断字典名称和编码是否已存在
     */
    public boolean hasDictByNameAndCode(String dictName,String dictCode,Long id) {
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        if (StrUtil.isNotBlank(dictCode)){
            queryWrapper.eq("code",dictCode);
        }
        if (StrUtil.isNotBlank(dictName)){
            queryWrapper.eq("name",dictName);
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
     * 根据字典编码查询字典
     */
    public SysDict getByDictCode(String dictCode){
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("code",dictCode);
        return baseMapper.selectOne(queryWrapper);
    }

    /**
     * 根据字典类型编码查询字典项列表（带缓存）
     */
    @SuppressWarnings("unchecked")
    public List<SysDict> getListByDictCode(String dictCode){
        String cacheKey = CACHE_DICT_TYPE + dictCode;
        Object cached = RedisUtil.get(cacheKey);
        if (cached != null) {
            return (List<SysDict>) cached;
        }
        SysDict dict = getByDictCode(dictCode);
        if (dict == null) {
            return List.of();
        }
        QueryWrapper<SysDict> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id",dict.getId());
        queryWrapper.select("id,name");
        List<SysDict> list = baseMapper.selectList(queryWrapper);
        RedisUtil.set(cacheKey, list, CACHE_DICT_TTL);
        return list;
    }

    /**
     * 清除字典缓存
     */
    public void clearDictCache(String dictCode) {
        RedisUtil.del(CACHE_DICT_TYPE + dictCode);
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

    @Override
    public boolean save(SysDict entity) {
        boolean result = super.save(entity);
        if (result && entity.getParentId() != null) {
            // 新增字典项时，清除父级字典类型的缓存
            SysDict parent = getById(entity.getParentId());
            if (parent != null) {
                clearDictCache(parent.getCode());
            }
        }
        return result;
    }

    @Override
    public boolean updateById(SysDict entity) {
        SysDict oldDict = getById(entity.getId());
        boolean result = super.updateById(entity);
        if (result) {
            if (SysDict.isDictType(oldDict)) {
                // 修改字典类型时，清除该类型缓存
                clearDictCache(oldDict.getCode());
            } else if (oldDict != null && oldDict.getParentId() != null) {
                // 修改字典项时，清除父级字典类型缓存
                SysDict parent = getById(oldDict.getParentId());
                if (parent != null) {
                    clearDictCache(parent.getCode());
                }
            }
        }
        return result;
    }

    @Override
    public boolean removeById(java.io.Serializable id) {
        SysDict dict = getById(id);
        boolean result = super.removeById(id);
        if (result && dict != null) {
            if (SysDict.isDictType(dict)) {
                // 删除字典类型时，清除该类型缓存
                clearDictCache(dict.getCode());
            } else if (dict.getParentId() != null) {
                // 删除字典项时，清除父级字典类型缓存
                SysDict parent = getById(dict.getParentId());
                if (parent != null) {
                    clearDictCache(parent.getCode());
                }
            }
        }
        return result;
    }
}
