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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        if (service.hasDictByNameAndCode(dict.getName(), null, dict.getId())){
            return R.error("字典名称已存在");
        }
        if (service.hasDictByNameAndCode(null, dict.getCode(), dict.getId())){
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
        if (service.hasDictByNameAndCode(dict.getName(), null, dict.getId())){
            return R.error("字典名称已存在");
        }
        if (service.hasDictByNameAndCode(null, dict.getCode(), dict.getId())){
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

    @GetMapping("/getByTypeCode")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dict:view")
    @Operation(summary = "根据字典类型代码查询字典项列表")
    public R getByTypeCode(String dictCode) {
        SysDict one = service.getByDictCode(dictCode);
        if (Objects.nonNull(one)){
            LambdaQueryWrapper<SysDict> query = new LambdaQueryWrapper<>();
            query.eq(SysDict::getParentId,one.getId());
            query.eq(SysDict::getIsType,"2");
            query.orderByAsc(SysDict::getSort);
            return R.success(service.list(query));
        }
        return R.success();
    }
}
