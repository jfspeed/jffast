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
package cn.jfspeed.system.controller;


import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import jakarta.validation.Valid;
import cn.jfspeed.system.domain.dto.SysDictRequest;
import cn.jfspeed.common.core.util.IdUtils;
import cn.jfspeed.system.util.PinyinCodeUtil;
import cn.jfspeed.system.domain.dto.SysDictQueryRequest;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.DistributedLock;
import cn.jfspeed.security.annotation.Idempotent;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.common.core.util.TreeUtil;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.system.entity.SysDict;
import cn.jfspeed.system.service.SysDictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * @author jffast
 */
@RestController
@RequestMapping("/sys/dict")
@Tag(name = "字典管理")
public class SysDictController extends BaseController<SysDictService,SysDict> {

    @GetMapping("/dictTypeList")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dict:view")
    @Operation(summary = "字典类型树形列表")
    public R dictTypeList(@RequestParam Map<String,String> map) {
        List<SysDict> list = service.dictTypeList(map);
        return R.success(TreeUtil.buildTreeIsRoot(list,BaseConstant.PARENT_ID));
    }

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dict:view")
    @Operation(summary = "字典项列表")
    public R page(SysDictQueryRequest sysDictQuery) {
        return R.success(service.page(sysDictQuery));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dict:view")
    @Operation(summary = "根据id获取信息")
    public R getById(@PathVariable("id") Long id) {
        return R.success(service.getById(id));
    }

    @SLog(title = "生成字典编码")
    @GetMapping("/createDictCode")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dict:view")
    @Operation(summary = "生成字典编码")
    public R createDictCode(String dictName) {
        return R.success(PinyinCodeUtil.firstLetterCode(dictName));
    }

    @GetMapping("/hasDictByName")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dict:view")
    @Operation(summary = "判断字典名称是否重复")
    public R hasDictByName(String dictName, Long id){
        boolean flag = service.hasDictByNameAndCode(dictName, null,id);
        Map<String,Object> map = new HashMap<>();
        map.put("isRepeat",flag);
        if (flag){
            dictName = service.createName(dictName);
            String dictCode = PinyinCodeUtil.firstLetterCode(dictName);
            map.put("dictName",dictName);
            map.put("dictCode",dictCode);
        }
        return R.success(map);
    }

    @GetMapping("/hasDictByCode")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dict:view")
    @Operation(summary = "判断字典代码是否重复")
    public R hasDictByCode(String dictCode, Long id){
        boolean flag = service.hasDictByNameAndCode(null, dictCode,id);
        Map<String,Object> map = new HashMap<>();
        map.put("isRepeat",flag);
        if (flag){
            map.put("dictCode",service.createCode(dictCode));
        }
        return R.success(map);
    }

    @SLog(title = "新增字典")
    @PostMapping("/create")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dict:add")
    @RateLimit(value = "default")
    @Idempotent(expire = 5)
    @Operation(summary = "新增字典")
    public R insert(@RequestBody @Valid SysDictRequest req) {
        SysDict dict = BeanUtil.copyProperties(req, SysDict.class);
        // 查重按「同父级 + 同类别」范围：不同字典类型下的字典项允许同名/同编码
        if (service.hasDictByNameAndCode(dict.getName(), null, dict.getId(), dict.getParentId(), dict.getIsType())){
            return R.error("字典名称已存在");
        }
        if (service.hasDictByNameAndCode(null, dict.getCode(), dict.getId(), dict.getParentId(), dict.getIsType())){
            return R.error("字典编码已存在");
        }
        dict.setId(IdUtils.snowflakeShort());
        service.save(dict);
        service.clearDictCache(dict.getCode());
        return R.success();
    }

    @SLog(title = "修改字典")
    @PutMapping("/update")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dict:update")
    @DistributedLock(key = "'dict:' + #req.id", expireTime = 10000)
    @Idempotent(expire = 5)
    @Operation(summary = "修改")
    public R update(@RequestBody @Valid SysDictRequest req) {
        SysDict dict = BeanUtil.copyProperties(req, SysDict.class);
        // 查重按「同父级 + 同类别」范围：不同字典类型下的字典项允许同名/同编码。
        // 请求未带 parentId / isType 时按「保持原值」处理（updateById 不更新 null 字段），
        // 查重作用域也要跟着取库里的原值，否则会把「改的是字典类型」当成字典项去查。
        SysDict existing = req.getId() == null ? null : service.getById(req.getId());
        Long scopeParentId = dict.getParentId() != null ? dict.getParentId()
                : (existing == null ? null : existing.getParentId());
        Integer scopeIsType = dict.getIsType() != null ? dict.getIsType()
                : (existing == null ? null : existing.getIsType());
        if (service.hasDictByNameAndCode(dict.getName(), null, dict.getId(), scopeParentId, scopeIsType)){
            return R.error("字典名称已存在");
        }
        if (service.hasDictByNameAndCode(null, dict.getCode(), dict.getId(), scopeParentId, scopeIsType)){
            return R.error("字典编码已存在");
        }
        service.updateById(dict);
        service.clearDictCache(dict.getCode());
        return R.success();
    }

    @SLog(title = "删除字典")
    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dict:delete")
    @Operation(summary = "删除")
    public R delete(@PathVariable("id") Long id) {
        SysDict byId = service.getById(id);
        if (SysDict.isDictType(byId)) {
            if (service.hasDictByParent(byId.getId())){
                return R.error("删除失败，该字典类型还存在子项");
            }
        }
        service.removeById(id);
        if (byId != null) {
            service.clearDictCache(byId.getCode());
        }
        return R.success();
    }

    /**
     * 按字典类型编码取字典项。
     *
     * <p>**只要求登录态、不要求字典管理权限**：这是给前端通用下拉框/单选组取字典项的基础接口，
     * 任何登录用户都会用到（例如个人中心的状态选项）。此前挂 `system:dict:view` 意味着除超管外
     * 的账号一律 403，与「业务字典是全局只读数据」的定位冲突；同时它与字典管理页的 `/page`
     * 是两回事——那边带分页、检索与管理字段，仍受权限码保护。
     */
    @GetMapping("/getByTypeCode")
    @Operation(summary = "根据字典类型代码查询字典项列表", description = "登录即可访问（前端下拉框等基础组件取字典项）")
    public R<List<SysDict>> getByTypeCode(String dictCode) {
        return R.success(service.getListByDictCode(dictCode));
    }
}
