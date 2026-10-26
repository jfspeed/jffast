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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysUser;
import jakarta.annotation.Resource;

/**
 * 在请求线程构造 {@link SysUserImportContext}。同步导入与异步导入都必须在进入
 * worker 之前调用；这是导入链路中唯一允许读取登录态与数据权限的位置。
 *
 * @author jffast
 */
@Component
public class SysUserImportContextFactory {

    @Resource
    private SysDeptScopeQuery sysDeptScopeQuery;

    public SysUserImportContext create() {
        Object principal = LoginUtil.getLoginSysUser();
        if (!(principal instanceof SysUser loginUser) || loginUser.getId() == null) {
            throw new BusinessException(403, "登录状态无效，请重新登录");
        }
        boolean superAdmin = LoginUtil.isSuperAdmin();
        List<SysDept> depts = sysDeptScopeQuery.listVisibleDepts();
        Map<Long, String> visibleDeptNames = new HashMap<>();
        Map<String, Long> uniqueIdByName = new HashMap<>();
        Set<String> ambiguousDeptNames = new HashSet<>();
        if (depts != null) {
            for (SysDept dept : depts) {
                if (dept == null || dept.getId() == null) {
                    continue;
                }
                // 名称列可为 NULL（sys_dept.dept_name 允许空），Map.copyOf 不接受 null 值
                visibleDeptNames.put(dept.getId(),
                        dept.getDeptName() == null ? "" : dept.getDeptName());
                String name = StrUtil.trimToNull(dept.getDeptName());
                if (name == null) {
                    continue;
                }
                Long previous = uniqueIdByName.put(name, dept.getId());
                if (previous != null && !previous.equals(dept.getId())) {
                    ambiguousDeptNames.add(name);
                }
            }
        }
        return new SysUserImportContext(superAdmin, visibleDeptNames, ambiguousDeptNames, uniqueIdByName);
    }
}
