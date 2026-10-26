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
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.jfspeed.security.annotation.DataPermission;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysRoleDept;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.mapper.SysDeptMapper;
import cn.jfspeed.system.mapper.SysRoleDeptMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 部门 服务实现*
 *
 * @author jffast
 */
@Service
public class SysDeptService extends BaseService<SysDeptMapper, SysDept>{

    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysRoleDeptMapper sysRoleDeptMapper;

    /**
     * 列表
     */
    @DataPermission(deptField = "id")
    public List<SysDept> list(Map<String, Object> map){
        List<SysDept> result = baseMapper.list(map);
        Object deptName = map.get("deptName");
        if (deptName != null && StrUtil.isNotBlank(deptName.toString()) && !result.isEmpty()) {
            Map<Long, SysDept> allDepts = loadDeptIndex();
            Set<Long> existingIds = result.stream().map(SysDept::getId).collect(Collectors.toSet());
            for (SysDept dept : new ArrayList<>(result)) {
                appendAncestors(dept, allDepts, existingIds, result);
            }
            result.sort((a, b) -> {
                int levelCompare = a.getLevel().compareTo(b.getLevel());
                if (levelCompare != 0) return levelCompare;
                return a.getCreateTime().compareTo(b.getCreateTime());
            });
        }
        return result;
    }

    /**
     * 一次性载入部门索引，沿 parentId 链在内存中补全祖先，避免按行查询祖先。
     */
    private Map<Long, SysDept> loadDeptIndex() {
        QueryWrapper<SysDept> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "parent_id", "dept_name", "level", "create_time");
        return baseMapper.selectList(queryWrapper).stream()
                .collect(Collectors.toMap(SysDept::getId, dept -> dept, (a, b) -> a));
    }

    private void appendAncestors(SysDept dept, Map<Long, SysDept> allDepts, Set<Long> existingIds, List<SysDept> result) {
        Long cursor = dept.getParentId();
        while (cursor != null && existingIds.add(cursor)) {
            SysDept ancestor = allDepts.get(cursor);
            if (ancestor == null) {
                return;
            }
            result.add(ancestor);
            cursor = ancestor.getParentId();
        }
    }

    /**
     * 根据角色id查询部门id列表
     */
    public List<Long> selectIdListByRoleId(Map<String, Object> map){
        QueryWrapper<SysRoleDept> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("role_id",map.get("id"));
        queryWrapper.select("dept_id");
        List<SysRoleDept> list = sysRoleDeptMapper.selectList(queryWrapper);
        List<Long> idList = list.stream().map(SysRoleDept::getDeptId).collect(Collectors.toList());
        return idList;
    }


    /**
     * 根据parentId得到所有子节点的id和name，以逗号分隔
     */
    public Map<String,String> getChildIdAndName(Long deptId) {
        if (deptId == null){
            return new HashMap<>();
        }
        List<SysDept> list = childIdList(deptId);
        if (!list.isEmpty()){
            Map<String,String> map = new HashMap<>();
            List<Long> idList = list.stream().map(SysDept::getId).collect(Collectors.toList());
            List<String> nameList = list.stream().map(SysDept::getDeptName).collect(Collectors.toList());
            map.put("id",StrUtil.join(",", idList));
            map.put("name",StrUtil.join(",", nameList));
            return map;
        }
        return null;
    }

    /**
     * 递归查询，根据父级id获取子级列表（包括父级本身）
     */
    public List<SysDept> childIdList(Long parentId){
        return baseMapper.childList(parentId);
    }

    /**
     * 校验公司/部门名称是否唯一
     */
    public boolean checkDeptName(SysDept dept) {
        QueryWrapper<SysDept> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SysDept::getDeptName,dept.getDeptName());
        queryWrapper.lambda().eq(SysDept::getParentId,dept.getParentId());
        if (dept.getId()!=null){
            queryWrapper.lambda().ne(SysDept::getId,dept.getId());
        }
        long count = this.baseMapper.selectCount(queryWrapper);
        return count > 0;
    }

    /**
     * 公司是否存在子节点或部门
     */
    public boolean hasChildByDeptId(Long deptId) {
        QueryWrapper<SysDept> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id",deptId);
        return baseMapper.selectCount(queryWrapper) > 0;
    }

    /**
     * 查询部门是否存在用户
     * @param deptId 部门ID
     * @return 结果 true 存在 false 不存在
     */
    public boolean checkDeptExistUser(Long deptId) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dept_id",deptId);
        return sysUserMapper.selectCount(queryWrapper) > 0;
    }

    /**
     * 查询部门是否分配了角色
     * @param deptId 部门ID
     * @return 结果 true 存在 false 不存在
     */
    public boolean checkDeptExistRole(Long deptId) {
        QueryWrapper<SysRoleDept> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dept_id",deptId);
        return sysRoleDeptMapper.selectCount(queryWrapper) > 0;
    }

    /**
     * 根据名称获取部门
     */
    public Long getIdByName(String name){
        QueryWrapper<SysDept> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dept_name",name);
        queryWrapper.select("id");
        SysDept dept = baseMapper.selectOne(queryWrapper);
        if (dept != null){
            return dept.getId();
        }
        return null;
    }

    public List<Map<String, Object>> deptUserTree() {
        List<SysDept> deptList = baseMapper.selectTreeNodes();
        Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (SysDept dept : deptList) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", "dept_" + dept.getId());
            Long parentId = dept.getParentId();
            node.put("parentId", parentId == null || parentId == 0L ? null : "dept_" + parentId);
            node.put("label", dept.getDeptName());
            node.put("nodeType", "dept");
            node.put("value", dept.getId());
            node.put("children", new ArrayList<Map<String, Object>>());
            nodeMap.put("dept_" + dept.getId(), node);
        }
        QueryWrapper<SysUser> userQuery = new QueryWrapper<>();
        userQuery.select("id", "user_name", "dept_id");
        List<SysUser> users = sysUserMapper.selectList(userQuery);
        for (SysUser user : users) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", "user_" + user.getId());
            node.put("parentId", user.getDeptId() != null ? "dept_" + user.getDeptId() : null);
            node.put("label", user.getUserName());
            node.put("nodeType", "user");
            node.put("value", user.getId());
            nodeMap.put("user_" + user.getId(), node);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> node : nodeMap.values()) {
            String parentId = (String) node.get("parentId");
            if (parentId == null || !nodeMap.containsKey(parentId)) {
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get(parentId).get("children");
                children.add(node);
            }
        }
        return roots;
    }
}
