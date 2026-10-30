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
package cn.jfspeed.system.domain.vo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysPost;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysUser;
import tools.jackson.databind.json.JsonMapper;

/**
 * 出参白名单测试：VO 是控制响应体字段的唯一闸门，字段集合一旦放宽就是泄露口子。
 *
 * @author jffast
 */
@DisplayName("SysUserVO 出参白名单测试")
class SysUserVOTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static SysUser sampleUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUserName("admin");
        user.setRealName("管理员");
        user.setNickName("超管");
        user.setPhone("13800001111");
        user.setEmail("admin@example.com");
        user.setAvatar("/avatar/admin.png");
        user.setState(1);
        user.setLoginDate("2026-10-26 10:00:00");
        user.setDeptId(10L);
        user.setDeptName("研发部");
        user.setRoleName("超级管理员");
        // 内部字段：VO 一律不映射
        user.setPassword("hashed-password");
        user.setSalt("random-salt");
        user.setRoleId("1,2");
        user.setRoleIds("1,2");
        user.setDeptIds("10,11");
        user.setCreateBy(99L);
        user.setTenantId(7L);
        user.setDataScope(Map.of("deptIds", "10"));

        SysRole role = new SysRole();
        role.setId(2L);
        role.setRoleKey("super_admin");
        role.setRoleName("超级管理员");
        role.setDataScope(1);
        role.setMenuIds(new Long[]{1L, 2L});
        user.setRoleList(List.of(role));

        SysDept dept = new SysDept();
        dept.setId(10L);
        dept.setDeptName("研发部");
        dept.setParentId(1L);
        user.setDeptList(List.of(dept));

        SysPost post = new SysPost();
        post.setId(7L);
        post.setPostCode("arch");
        post.setPostName("架构师");
        post.setTenantId(7L);
        user.setPostList(List.of(post));
        return user;
    }

    @Test
    @DisplayName("VO 字段集合固定为白名单：多一个字段就是泄露口子")
    void voFieldsAreFixedWhitelist() {
        Set<String> fields = Arrays.stream(SysUserVO.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("id", "userName", "realName", "nickName", "phone", "email", "avatar", "state",
                "loginDate", "deptId", "deptName", "roleName", "roleList", "deptList", "postList",
                "createTime"), fields);
    }

    @Test
    @DisplayName("嵌套 VO 同样只留前端消费的字段")
    void nestedVoFieldsAreTrimmed() {
        assertEquals(Set.of("id", "roleKey", "roleName"), fieldNames(SysUserRoleVO.class));
        assertEquals(Set.of("id", "deptName"), fieldNames(SysUserDeptVO.class));
        assertEquals(Set.of("id", "postName"), fieldNames(SysUserPostVO.class));
    }

    @Test
    @DisplayName("序列化后不含 password/salt/dataScope/roleId 等内部字段")
    void serializedPayloadHasNoInternalFields() {
        String json = MAPPER.writeValueAsString(SysUserVO.from(sampleUser()));
        assertFalse(json.contains("password"));
        assertFalse(json.contains("salt"));
        assertFalse(json.contains("dataScope"));
        assertFalse(json.contains("roleId"));
        assertFalse(json.contains("deptIds"));
        assertFalse(json.contains("createBy"));
        assertFalse(json.contains("tenantId"));
        assertFalse(json.contains("menuIds"));
        assertFalse(json.contains("parentId"));
        assertFalse(json.contains("postCode"));
    }

    @Test
    @DisplayName("前端消费的字段必须仍在响应里")
    void serializedPayloadKeepsFrontendFields() {
        String json = MAPPER.writeValueAsString(SysUserVO.from(sampleUser()));
        assertTrue(json.contains("\"userName\":\"admin\""));
        assertTrue(json.contains("\"realName\""));
        assertTrue(json.contains("\"nickName\""));
        assertTrue(json.contains("\"avatar\""));
        assertTrue(json.contains("\"phone\""));
        assertTrue(json.contains("\"email\""));
        assertTrue(json.contains("\"state\":1"));
        assertTrue(json.contains("\"deptId\""));
        assertTrue(json.contains("\"deptName\""));
        assertTrue(json.contains("\"roleName\""));
        // 登录态回填靠 roleList[].id / roleList[].roleKey 派生超管位
        assertTrue(json.contains("\"roleKey\":\"super_admin\""));
        assertTrue(json.contains("\"roleList\""));
        assertTrue(json.contains("\"deptList\""));
        assertTrue(json.contains("\"postList\""));
    }

    @Test
    @DisplayName("from 映射白名单字段并裁掉内部字段")
    void fromShouldMapWhitelistOnly() {
        SysUserVO vo = SysUserVO.from(sampleUser());
        assertEquals(1L, vo.getId());
        assertEquals("admin", vo.getUserName());
        assertEquals("管理员", vo.getRealName());
        assertEquals(10L, vo.getDeptId());
        assertEquals("研发部", vo.getDeptName());
        assertEquals("超级管理员", vo.getRoleName());
        assertEquals(1, vo.getRoleList().size());
        assertEquals(2L, vo.getRoleList().get(0).getId());
        assertEquals("super_admin", vo.getRoleList().get(0).getRoleKey());
        assertEquals(1, vo.getDeptList().size());
        assertEquals(10L, vo.getDeptList().get(0).getId());
        assertEquals(1, vo.getPostList().size());
        assertEquals(7L, vo.getPostList().get(0).getId());
        assertEquals("架构师", vo.getPostList().get(0).getPostName());
    }

    @Test
    @DisplayName("from(null) 返回 null；fromList 逐项裁剪")
    void nullAndListShouldBeSafe() {
        assertNull(SysUserVO.from(null));
        assertNull(SysUserVO.fromList(null));
        List<SysUserVO> list = SysUserVO.fromList(java.util.Arrays.asList(sampleUser(), null));
        assertEquals(1, list.size());
        assertEquals("admin", list.get(0).getUserName());
    }

    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }
}
