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

import java.util.List;

import cn.jfspeed.security.annotation.DataPermission;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.mapper.SysDeptMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 受数据权限约束的部门存在性查询。
 * <p>
 * 独立 Bean + 单条 SQL：{@code @DataPermission} 是 AOP 切面，同类自调用不经过代理；
 * 只有 {@code deptField}、没有 {@code userField}，因此只按部门维度过滤（scope=4 用户
 * 的可操作部门由 bmid 决定）。范围外与不存在都返回 null，调用方使用同一文案避免枚举。
 *
 * @author jffast
 */
@Component
public class SysDeptScopeQuery {

    @Resource
    private SysDeptMapper sysDeptMapper;

    /**
     * 在当前登录用户的数据权限范围内按主键查询部门；范围外或不存在返回 null
     */
    @DataPermission(deptField = "id")
    public SysDept findVisibleDept(Long id) {
        if (id == null) {
            return null;
        }
        return sysDeptMapper.selectById(id);
    }

    /**
     * 当前登录用户数据权限内的全部部门（只取 id 与名称，供导入按名称解析归属）
     */
    @DataPermission(deptField = "id")
    public List<SysDept> listVisibleDepts() {
        LambdaQueryWrapper<SysDept> query = new LambdaQueryWrapper<>();
        query.select(SysDept::getId, SysDept::getDeptName);
        return sysDeptMapper.selectList(query);
    }
}
