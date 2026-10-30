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

import cn.jfspeed.system.entity.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 用户信息对外视图对象。
 *
 * <p>开发规范 §10：出参永远不入 {@code Entity}。用户实体上带着 password / salt / dataScope /
 * role_id / role_ids / dept_ids / create_by 等内部字段，直接返回等于把内部结构摊给客户端。
 * 本 VO 是显式白名单：只保留前端真正消费的字段。
 *
 * <p>前端消费清单（jffast-ui）：
 * <ul>
 *   <li>个人中心（{@code pages/user/index.vue}，读 {@code /sys/user/getLoginUser}）：
 *       id / avatar / userName / nickName / realName / roleName / deptName / phone / email / state</li>
 *   <li>用户编辑弹窗（{@code pages/system/user/save.vue}，读 {@code /sys/user/detail/{id}}）：
 *       id / userName / realName / nickName / phone / email / state / deptId / roleList / deptList / postList</li>
 *   <li>用户下拉（{@code pages/system/notification/save.vue}，读 {@code /sys/user/all}）：id / userName</li>
 *   <li>登录态回填（{@code store/modules/user.ts} 读 {@code /sys/login}、{@code /sys/getUserInfo}）：
 *       id / userName / realName / nickName / avatar / roleList（roleList[].id、roleList[].roleKey 用于派生超管位）</li>
 * </ul>
 *
 * <p>刻意不映射：password、salt、dataScope、roleId（原始逗号串）、roleIds、deptIds、parentId、
 * isBusinessUser、tenantId、createBy、updateBy、updateTime、updatePwdTime。
 *
 * <p>注意：会话（Sa-Token）里仍然存完整的 {@code SysUser}，本 VO 只影响 HTTP 响应体。
 *
 * @author jffast
 */
@Data
@Schema(description = "用户信息")
public class SysUserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "登录账号")
    private String userName;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "昵称")
    private String nickName;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "状态（1正常 2停用）")
    private Integer state;

    @Schema(description = "最后登录时间")
    private String loginDate;

    @Schema(description = "归属部门ID")
    private Long deptId;

    @Schema(description = "归属部门名称")
    private String deptName;

    @Schema(description = "角色名称（多角色逗号分隔）")
    private String roleName;

    @Schema(description = "角色列表")
    private List<SysUserRoleVO> roleList;

    @Schema(description = "部门绑定列表")
    private List<SysUserDeptVO> deptList;

    @Schema(description = "岗位绑定列表")
    private List<SysUserPostVO> postList;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 实体 → VO：只映射白名单字段，绝不把 password / salt / dataScope 等内部字段带出去。
     */
    public static SysUserVO from(SysUser user) {
        if (user == null) {
            return null;
        }
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUserName(user.getUserName());
        vo.setRealName(user.getRealName());
        vo.setNickName(user.getNickName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setState(user.getState());
        vo.setLoginDate(user.getLoginDate());
        vo.setDeptId(user.getDeptId());
        vo.setDeptName(user.getDeptName());
        vo.setRoleName(user.getRoleName());
        vo.setCreateTime(user.getCreateTime());
        vo.setRoleList(user.getRoleList() == null ? null
                : user.getRoleList().stream()
                        .filter(Objects::nonNull)
                        .map(SysUserRoleVO::from)
                        .toList());
        vo.setDeptList(user.getDeptList() == null ? null
                : user.getDeptList().stream()
                        .filter(Objects::nonNull)
                        .map(SysUserDeptVO::from)
                        .toList());
        vo.setPostList(user.getPostList() == null ? null
                : user.getPostList().stream()
                        .filter(Objects::nonNull)
                        .map(SysUserPostVO::from)
                        .toList());
        return vo;
    }

    /**
     * 列表转换：逐项走 {@link #from(SysUser)}，同样只暴露白名单字段。
     */
    public static List<SysUserVO> fromList(List<SysUser> users) {
        if (users == null) {
            return null;
        }
        return users.stream()
                .filter(Objects::nonNull)
                .map(SysUserVO::from)
                .toList();
    }
}
