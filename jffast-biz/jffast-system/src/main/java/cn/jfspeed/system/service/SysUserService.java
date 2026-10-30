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

import cn.jfspeed.common.core.enums.AuthErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.config.JfFastSystemProperties;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.common.security.util.EncryptionUtil;
import cn.jfspeed.security.annotation.DataPermission;
import cn.jfspeed.system.constant.SystemErrorCode;
import cn.jfspeed.system.domain.dto.SysUserImportExcel;
import cn.jfspeed.system.domain.dto.SysUserImportResult;
import cn.jfspeed.system.domain.vo.SysUserVO;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysPost;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserDept;
import cn.jfspeed.system.entity.SysUserPost;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.event.UserAuthorizationChangedEvent;
import cn.jfspeed.system.mapper.SysSocialBindMapper;
import cn.jfspeed.system.mapper.SysUserDeptMapper;
import cn.jfspeed.system.mapper.SysUserPostMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author jffast
 */
@Slf4j
@Service
public class SysUserService extends BaseService<SysUserMapper, SysUser>{

    @Resource
    private SysDeptService sysDeptService;
    @Resource
    private SysRoleService sysRoleService;
    @Resource
    private SysUserDeptMapper sysUserDeptMapper;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;
    @Resource
    private SysUserPostMapper sysUserPostMapper;
    @Resource
    private SysPostService sysPostService;
    @Resource
    private SysSocialBindMapper sysSocialBindMapper;
    /**
     * 社交账号绑定开关（与 {@code JfFastSocialAutoConfiguration} 同一属性）。
     * <p>
     * {@code sys_social_bind} 由**可选**脚本 {@code sql/social_bind.sql} 创建，未启用社交功能时
     * 表可能根本不存在；开了开关也不等于导了那张脚本，故删号前还会探一次表存在性
     * （{@link SysSocialBindMapper#countSocialBindTable()}），不存在就跳过清理，避免删号 500。
     */
    @Value("${jffast.social.enabled:false}")
    private boolean socialEnabled;
    /** 初始密码（jffast.system.init-password）：导入用户写入的默认密码 */
    @Resource
    private JfFastSystemProperties jfFastSystemProperties;
    /** 受数据权限约束的目标存在性查询（独立 Bean，保证 @DataPermission 切面生效） */
    @Resource
    private SysUserScopeQuery sysUserScopeQuery;
    /** 用户授权字段变更事件：提交后由监听器撤销目标会话 */
    @Resource
    private ApplicationEventPublisher eventPublisher;

    /** 单次导入行数上限（同步/异步共用） */
    private static final int IMPORT_MAX_ROWS = 10000;

    @Transactional(rollbackFor = Exception.class)
    public void saveUser(SysUser user){
        saveUser(user, user.getRoleList() != null, user.getDeptList() != null);
    }

    /**
     * 保存用户；syncRoles/syncDepts 显式承载「是否提交集合」的意图：
     * null=不修改、[]=清空、非空=替换。role_id 缓存必须显式写入（含空串），
     * 不能依赖全局 update-strategy=NOT_EMPTY。
     *
     * <p>岗位同步段不走形参：sys_user 没有岗位缓存列，三态意图已由 DTO 映射进
     * {@code user.postList} 本身（null 与空集合不等价），方法内派生开关即可。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUser(SysUser user, boolean syncRoles, boolean syncDepts) {
        // N-20：非空 deptId 必须存在（含 0），sys_user.dept_id 无外键约束兜底
        if (user.getDeptId() != null && sysDeptService.getById(user.getDeptId()) == null) {
            throw new BusinessException("部门不存在");
        }
        boolean create = user.getId() == null;
        boolean superAdmin = BaseConstant.SUPER_ADMIN.equals(user.getUserName());

        String joinedRoleIds = null;
        List<Long> requestedRoleIds = null;
        if (syncRoles && !superAdmin) {
            List<SysRole> roleList = user.getRoleList() == null ? new ArrayList<>() : user.getRoleList();
            validateRoleIds(roleList);
            requestedRoleIds = normalizeRoleIds(roleList);
            joinedRoleIds = joinRoleIds(roleList);
            user.setRoleId(joinedRoleIds);
        }
        List<Long> requestedDeptIds = null;
        if (syncDepts) {
            List<SysDept> deptList = user.getDeptList() == null ? new ArrayList<>() : user.getDeptList();
            requestedDeptIds = deptList.stream()
                    .filter(Objects::nonNull)
                    .map(SysDept::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }
        List<Long> requestedPostIds = user.getPostList() == null ? null : normalizePostIds(user.getPostList());
        // D1：创建路径只给归属部门时补一条绑定；否则 sys_user.dept_id 不产生可见性，
        // 账号在自身范围内不可见、创建者也无法管理。更新路径保持 null=不改、[]=清空。
        if (create && (requestedDeptIds == null || requestedDeptIds.isEmpty())
                && user.getDeptId() != null) {
            requestedDeptIds = List.of(user.getDeptId());
        }

        // 事务内、写库前比较授权字段：集合等价与顺序无关
        boolean rolesChanged = !create && requestedRoleIds != null
                && !sameIdSet(listRoleIds(user.getId()), requestedRoleIds);

        SysUser existing = null;
        if (!create) {
            existing = this.getById(user.getId());
            if (existing == null) {
                throw new BusinessException(SystemErrorCode.USER_NOT_FOUND);
            }
        }

        // currentDeptIds 只查一次，供变化判定与不变量校验共用
        List<Long> currentDeptIds = create ? new ArrayList<>() : listDeptIds(user.getId());
        // INV-1：更新时不得把非空绑定提交为空（对所有操作者一致）
        if (!create && syncDepts && requestedDeptIds != null && requestedDeptIds.isEmpty()
                && !currentDeptIds.isEmpty()) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "部门绑定不能为空");
        }
        boolean deptsChanged = !create && requestedDeptIds != null
                && !sameIdSet(currentDeptIds, requestedDeptIds);
        boolean deptIdChanged = !create && user.getDeptId() != null
                && !Objects.equals(existing.getDeptId(), user.getDeptId());

        // INV-2：更新后归属部门必须落在生效绑定集合内（含仅改 deptId、未带 deptList 的直连更新）
        List<Long> effectiveDeptIds = requestedDeptIds != null ? requestedDeptIds : currentDeptIds;
        Long effectiveDeptId = user.getDeptId() != null ? user.getDeptId()
                : (create ? null : existing.getDeptId());
        if (!create && (deptIdChanged || deptsChanged) && effectiveDeptId != null
                && !effectiveDeptIds.contains(effectiveDeptId)) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "归属部门须包含在部门绑定中");
        }
        // INV-3：创建时显式绑定集合非空，必须包含提交的归属部门
        if (create && requestedDeptIds != null && !requestedDeptIds.isEmpty()
                && user.getDeptId() != null && !requestedDeptIds.contains(user.getDeptId())) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "归属部门须包含在部门绑定中");
        }

        if (create) {
            this.save(user);
        } else if (!this.updateById(user)) {
            throw new BusinessException("用户更新失败");
        }

        if (joinedRoleIds != null) {
            writeRoleIdCache(user.getId(), joinedRoleIds);
        }
        if (requestedDeptIds != null) {
            LambdaQueryWrapper<SysUserDept> deptLambdaQueryWrapper = new LambdaQueryWrapper<>();
            deptLambdaQueryWrapper.eq(SysUserDept::getUserId, user.getId());
            sysUserDeptMapper.delete(deptLambdaQueryWrapper);
            if (!requestedDeptIds.isEmpty()) {
                List<SysUserDept> sysUserDeptList = requestedDeptIds.stream().map(deptId -> {
                    SysUserDept sysUserDept = new SysUserDept();
                    sysUserDept.setUserId(user.getId());
                    sysUserDept.setDeptId(deptId);
                    return sysUserDept;
                }).collect(Collectors.toList());
                sysUserDeptMapper.insertBatch(sysUserDeptList);
            }
        }
        if (requestedRoleIds != null) {
            LambdaQueryWrapper<SysUserRole> roleLambdaQueryWrapper = new LambdaQueryWrapper<>();
            roleLambdaQueryWrapper.eq(SysUserRole::getUserId, user.getId());
            sysUserRoleMapper.delete(roleLambdaQueryWrapper);
            if (!requestedRoleIds.isEmpty()) {
                List<SysUserRole> sysUserRoleList = requestedRoleIds.stream().map(roleId -> {
                    SysUserRole sysUserRole = new SysUserRole();
                    sysUserRole.setUserId(user.getId());
                    sysUserRole.setRoleId(roleId);
                    return sysUserRole;
                }).collect(Collectors.toList());
                sysUserRoleMapper.insertBatch(sysUserRoleList);
            }
        }
        // 岗位段不设 !superAdmin 例外（与角色段有意不同）：岗位不参与授权，给内置超管挂岗位
        // 没有安全含义，而「跳过」会把前端提交的岗位静默丢弃，比清空更难排查。
        if (requestedPostIds != null) {
            // 先校验再删插：让「提交了不存在的岗位」在不触碰关系表的分支上失败
            validatePostIds(requestedPostIds);
            LambdaQueryWrapper<SysUserPost> postLambdaQueryWrapper = new LambdaQueryWrapper<>();
            postLambdaQueryWrapper.eq(SysUserPost::getUserId, user.getId());
            sysUserPostMapper.delete(postLambdaQueryWrapper);
            if (!requestedPostIds.isEmpty()) {
                List<SysUserPost> sysUserPostList = requestedPostIds.stream().map(postId -> {
                    SysUserPost sysUserPost = new SysUserPost();
                    sysUserPost.setUserId(user.getId());
                    sysUserPost.setPostId(postId);
                    return sysUserPost;
                }).collect(Collectors.toList());
                sysUserPostMapper.insertBatch(sysUserPostList);
            }
        }

        // 仅更新路径发布；创建不存在「旧授权仍留在会话」的问题
        if (!create && (rolesChanged || deptsChanged || deptIdChanged)) {
            eventPublisher.publishEvent(new UserAuthorizationChangedEvent(user.getId()));
        }
    }

    /**
     * 数据权限范围内的目标用户：不存在或越权统一拒绝；
     * 命中后校验角色/部门关系补全完整性，关系缺失时 fail-closed。
     *
     * <p>岗位关系刻意不装同样的完整性断言：角色/部门 fail-closed 是因为「授权集合不完整就放行
     * 管理动作」等于越权，岗位不参与授权。悬空岗位绑定由删除防护与详情回填自然消化，
     * 在这里抛 403 只会把「有人直接改库删了岗位行」放大成该用户的详情/编辑/重置/解锁全部不可用。
     */
    public SysUser getScopedUser(Long id) {
        if (sysUserScopeQuery.findVisibleUser(id) == null) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "用户不存在或无权操作");
        }
        SysUser user = getUser(id, null, null);
        Set<Long> roleIds = new HashSet<>(listRoleIds(id));
        if (!roleIds.isEmpty()
                && (user.getRoleList() == null || user.getRoleList().size() != roleIds.size())) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "用户角色关系不完整，拒绝操作");
        }
        Set<Long> deptIds = new HashSet<>(listDeptIds(id));
        if (!deptIds.isEmpty()
                && (user.getDeptList() == null || user.getDeptList().size() != deptIds.size())) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "用户部门关系不完整，拒绝操作");
        }
        return user;
    }

    /**
     * 删除用户并清理角色/部门/岗位/社交账号绑定；会话失效由调用方在事务提交后处理。
     *
     * <p>{@code sys_social_bind} 必须一起清：唯一键 {@code uk_provider_openid} 是
     * {@code (provider, open_id)}，行留着就永久占位——被删用户的社交账号既绑不到新账号，
     * 也无法再绑回自己，只能手工删库把行清掉。
     *
     * <p>但该表由**可选**脚本 {@code sql/social_bind.sql} 创建（不在 {@code system_sql.sql} 里）：
     * 开了 {@code jffast.social.enabled} 不等于导了那张脚本，表不存在时无条件 DELETE
     * 会让删号直接 500。故先探表存在性，不存在就跳过清理（此时本仓本就没有绑定数据可清）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        if (socialEnabled && sysSocialBindMapper.countSocialBindTable() > 0) {
            sysSocialBindMapper.deleteByUserId(userId);
        }

        LambdaQueryWrapper<SysUserRole> roleQuery = new LambdaQueryWrapper<>();
        roleQuery.eq(SysUserRole::getUserId, userId);
        sysUserRoleMapper.delete(roleQuery);

        LambdaQueryWrapper<SysUserDept> deptQuery = new LambdaQueryWrapper<>();
        deptQuery.eq(SysUserDept::getUserId, userId);
        sysUserDeptMapper.delete(deptQuery);

        LambdaQueryWrapper<SysUserPost> postQuery = new LambdaQueryWrapper<>();
        postQuery.eq(SysUserPost::getUserId, userId);
        sysUserPostMapper.delete(postQuery);

        if (!this.removeById(userId)) {
            throw new BusinessException(SystemErrorCode.USER_NOT_FOUND);
        }
    }

    /** 撤销单个用户会话：失败只记录告警，不影响已提交的业务变更 */
    public void kickoutUser(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            StpUtil.kickout(userId);
        } catch (Exception e) {
            log.warn("撤销用户会话失败，userId={}", userId, e);
        }
    }

    /**
     * 撤销绑定指定角色的全部用户会话：sys_user_role 与 sys_user.role_id 双源并查，
     * 历史数据可能只写了缓存列；任一源失败不阻断另一源。
     */
    public void kickoutByRoleId(Long roleId) {
        if (roleId == null) {
            return;
        }
        Set<Long> userIds = new HashSet<>();
        try {
            QueryWrapper<SysUserRole> query = new QueryWrapper<>();
            query.eq("role_id", roleId);
            for (SysUserRole binding : sysUserRoleMapper.selectList(query)) {
                if (binding.getUserId() != null) {
                    userIds.add(binding.getUserId());
                }
            }
        } catch (Exception e) {
            log.warn("按角色查询绑定用户失败（sys_user_role），roleId={}", roleId, e);
        }
        try {
            QueryWrapper<SysUser> query = new QueryWrapper<>();
            query.apply("FIND_IN_SET({0},role_id)", roleId);
            query.select("id");
            for (SysUser user : baseMapper.selectList(query)) {
                if (user.getId() != null) {
                    userIds.add(user.getId());
                }
            }
        } catch (Exception e) {
            log.warn("按角色查询绑定用户失败（sys_user.role_id），roleId={}", roleId, e);
        }
        for (Long userId : userIds) {
            kickoutUser(userId);
        }
    }

    private List<Long> listRoleIds(Long userId) {
        QueryWrapper<SysUserRole> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        return sysUserRoleMapper.selectList(query).stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Long> listDeptIds(Long userId) {
        QueryWrapper<SysUserDept> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        return sysUserDeptMapper.selectList(query).stream()
                .map(SysUserDept::getDeptId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 显式写 role_id 缓存：空串也必须落库，不能被 NOT_EMPTY 策略跳过 */
    private void writeRoleIdCache(Long userId, String roleIds) {
        LambdaUpdateWrapper<SysUser> update = new LambdaUpdateWrapper<>();
        update.eq(SysUser::getId, userId).set(SysUser::getRoleId, roleIds);
        if (baseMapper.update(null, update) <= 0) {
            throw new BusinessException("用户更新失败");
        }
    }

    private static List<Long> normalizeRoleIds(List<SysRole> roleList) {
        return roleList.stream()
                .filter(role -> role != null && role.getId() != null)
                .map(SysRole::getId)
                .distinct()
                .collect(Collectors.toList());
    }

    private static boolean sameIdSet(List<Long> current, List<Long> requested) {
        return new HashSet<>(current).equals(new HashSet<>(requested));
    }

    private void validateRoleIds(List<SysRole> roleList) {
        List<Long> roleIds = roleList.stream()
                .filter(role -> role != null && role.getId() != null)
                .map(SysRole::getId)
                .distinct()
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return;
        }
        if (sysRoleService.listByIds(roleIds).size() != roleIds.size()) {
            throw new BusinessException("角色不存在或已被删除");
        }
    }

    static String joinRoleIds(List<SysRole> roleList) {
        return roleList.stream()
                .filter(role -> role != null && role.getId() != null)
                .map(role -> String.valueOf(role.getId()))
                .collect(Collectors.joining(","));
    }

    private static List<Long> normalizePostIds(List<SysPost> postList) {
        return postList.stream()
                .filter(post -> post != null && post.getId() != null)
                .map(SysPost::getId)
                .distinct()
                .collect(Collectors.toList());
    }

    private void validatePostIds(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return;
        }
        if (sysPostService.listByIds(postIds).size() != postIds.size()) {
            throw new BusinessException("岗位不存在或已被删除");
        }
    }

    public SysUser getUser(Long id,String name,String phone){
        Map<String,Object> map = new HashMap<>();
        String msg = "用户不存在";
        if (id!=null){
            map.put("id",id);
        }
        if (StrUtil.isNotBlank(name)){
            map.put("userName",name);
        }
        if (StrUtil.isNotBlank(phone)){
            map.put("phone",phone);
            msg = phone+"手机号码不存在或未注册";
        }
        SysUser user;
        try {
            user = baseMapper.getById(map);
        } catch (TooManyResultsException e) {
            // 同名 / 同号重复时，mapper 的 `group by u.id` 会返回多行并抛 TooManyResultsException。
            // 那是数据异常，但必须翻译成可读的业务异常：一旦让它逃逸，登录接口只会兜底成
            // 裸 500，客户端看不到原因，sys_log 里也不会留下登录审计。
            // 新库由 sys_user 的唯一约束从源头杜绝；存量库重复时走这里给出可操作的提示。
            throw new BusinessException(SystemErrorCode.USER_NOT_FOUND.getCode(),
                    "用户数据异常（存在重复的用户名或手机号），请联系管理员处理");
        }
        if (null == user){
            throw new BusinessException(SystemErrorCode.USER_NOT_FOUND.getCode(), msg);
        }

        LambdaQueryWrapper<SysUserRole> roleLambdaQueryWrapper = new LambdaQueryWrapper<>();
        roleLambdaQueryWrapper.eq(SysUserRole::getUserId, user.getId());
        List<Long> roleIds = sysUserRoleMapper.selectList(roleLambdaQueryWrapper)
                .stream().map(t -> t.getRoleId()).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(roleIds)) {
            user.setRoleList(sysRoleService.listByIds(roleIds));
        }

        LambdaQueryWrapper<SysUserDept> deptLambdaQueryWrapper = new LambdaQueryWrapper<>();
        deptLambdaQueryWrapper.eq(SysUserDept::getUserId, user.getId());
        List<Long> deptIds = sysUserDeptMapper.selectList(deptLambdaQueryWrapper)
                .stream().map(t -> t.getDeptId()).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(deptIds)) {
            user.setDeptList(sysDeptService.listByIds(deptIds));
        }

        LambdaQueryWrapper<SysUserPost> postLambdaQueryWrapper = new LambdaQueryWrapper<>();
        postLambdaQueryWrapper.eq(SysUserPost::getUserId, user.getId());
        List<Long> bindPostIds = sysUserPostMapper.selectList(postLambdaQueryWrapper)
                .stream().map(t -> t.getPostId()).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(bindPostIds)) {
            user.setPostList(sysPostService.listByIds(bindPostIds));
        }
        return user;
    }

    @DataPermission(deptField = "d.id", userField = "u.id")
    public PageRes<SysUserVO> page(PageReq<Map<String, Object>> pageReq) {
        Map<String,Object> map = pageReq.getQueryBean();

        Object createTimeObj = map.get("createTime");
        if (createTimeObj instanceof List) {
            List<?> timeList = (List<?>) createTimeObj;
            if (timeList.size() >= 2 && timeList.get(0) != null && !"".equals(timeList.get(0))) {
                map.put("beginTime", timeList.get(0));
            }
            if (timeList.size() >= 2 && timeList.get(1) != null && !"".equals(timeList.get(1))) {
                map.put("endTime", timeList.get(1));
            }
        }

        // 出参走 VO 白名单（与 /all、/detail 同口径）：列表 SQL 是 `select u.*`，
        // 直接返回实体把 tenantId / updatePwdTime / isBusinessUser 等内部列一起发给前端。
        PageRes<SysUser> pageRes = PageUtil.resultPageMp(baseMapper.page(pageReq.getPage(), map));
        return new PageRes<>(pageRes.getTotal(), pageRes.getPages(), SysUserVO.fromList(pageRes.getList()));
    }

    public boolean checkUserNameAndPhone(String userName,String phone,Long id) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper();
        if (StrUtil.isNotBlank(userName)){
            queryWrapper.eq("user_name",userName);
        }
        if (StrUtil.isNotBlank(phone)){
            queryWrapper.eq("phone",phone);
        }
        if (id!=null){
            queryWrapper.ne("id",id);
        }
        long count = baseMapper.selectCount(queryWrapper);
        return count > 0;
    }

    /**
     * 全部用户（供 /all 下拉）：与列表同样受数据权限约束，避免越权看到范围外用户
     */
    @DataPermission(deptField = "d.id", userField = "u.id")
    public List<SysUser> listAll() {
        return baseMapper.list(buildQueryParams(null));
    }

    /**
     * 导出用列表：过滤条件与用户列表一致（用户名/姓名/手机号模糊、状态、创建时间范围），
     * 行数上限 maxRows 防止一次导出拖垮内存；与列表同样受数据权限约束
     */
    @DataPermission(deptField = "d.id", userField = "u.id")
    public List<SysUser> listForExport(Map<String, Object> map, int maxRows) {
        Map<String, Object> params = buildQueryParams(map);
        params.put("maxRows", maxRows);
        return baseMapper.list(params);
    }

    /**
     * 归一化查询条件：仅保留非空文本条件，并把 createTime 范围选择器原值折算为 beginTime/endTime。
     * 交给 Mapper 的 {@code list} 查询使用（联表别名 u/d，与数据权限字段一致）。
     */
    private Map<String, Object> buildQueryParams(Map<String, Object> map) {
        Map<String, Object> params = new HashMap<>();
        if (map == null) {
            return params;
        }
        putIfText(params, "userName", map.get("userName"));
        putIfText(params, "realName", map.get("realName"));
        putIfText(params, "phone", map.get("phone"));
        putIfText(params, "state", map.get("state"));
        // 列表页按角色/部门筛选时，导出必须带同样的条件，否则导出范围大于用户看到的结果
        putIfText(params, "roleId", map.get("roleId"));
        putIfText(params, "deptId", map.get("deptId"));

        // 创建时间范围：优先取 beginTime/endTime，其次兼容 createTime 数组（范围选择器原值）
        String beginTime = asText(map.get("beginTime"));
        String endTime = asText(map.get("endTime"));
        Object createTimeObj = map.get("createTime");
        if (createTimeObj instanceof List<?> timeList && timeList.size() >= 2) {
            if (StrUtil.isBlank(beginTime)) {
                beginTime = asText(timeList.get(0));
            }
            if (StrUtil.isBlank(endTime)) {
                endTime = asText(timeList.get(1));
            }
        }
        putIfText(params, "beginTime", beginTime);
        putIfText(params, "endTime", endTime);
        return params;
    }

    private static void putIfText(Map<String, Object> params, String key, Object value) {
        String text = asText(value);
        if (StrUtil.isNotBlank(text)) {
            params.put(key, text);
        }
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 导入用户：逐行校验（用户名必填、文件内不重复、不与库内冲突、归属部门可解析），
     * 坏行跳过并记录原因；新用户密码统一写入初始密码（jffast.system.init-password），
     * 归属部门非空时同时写一条 sys_user_dept 绑定
     */
    @Transactional(rollbackFor = Exception.class)
    public SysUserImportResult importUsers(List<SysUserImportExcel> rows, SysUserImportContext ctx) {
        return importUsers(rows, 0, ctx);
    }

    /**
     * 导入用户（带行号偏移）：分批导入时 rowOffset 为前面已处理的行数，
     * 行号 = rowOffset + i + 2（含表头行）；校验与写库逻辑同单批导入。
     * ctx 必须由请求线程的 {@link SysUserImportContextFactory} 预先构造，worker 内只读。
     */
    @Transactional(rollbackFor = Exception.class)
    public SysUserImportResult importUsers(List<SysUserImportExcel> rows, int rowOffset, SysUserImportContext ctx) {
        if (rows.size() > IMPORT_MAX_ROWS) {
            throw new BusinessException("单次导入不能超过 " + IMPORT_MAX_ROWS + " 行");
        }
        SysUserImportResult result = new SysUserImportResult();
        result.setTotal(rows.size());

        // 初始密码未配置时拒绝导入：不为新用户写入一个可能已随仓库公开的固定口令，
        // 也不静默写入空口令。请先配置 jffast.system.init-password 后重试。
        String initPassword = jfFastSystemProperties.getInitPassword();
        if (StrUtil.isBlank(initPassword)) {
            throw new BusinessException(SystemErrorCode.INIT_PASSWORD_NOT_CONFIGURED.getCode(),
                    "未配置 jffast.system.init-password，无法为导入用户设置初始密码");
        }
        String encryptedInitPassword = EncryptionUtil.getEncryptPassword(initPassword);
        List<String> failures = new ArrayList<>();
        // 文件内去重：user:用户名 / phone:手机号
        Set<String> seen = new HashSet<>();
        List<PendingImportRow> pending = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            SysUserImportExcel row = rows.get(i);
            int rowNo = rowOffset + i + 2; // 表头占第 1 行
            String userName = StrUtil.trimToNull(row.getUserName());
            if (userName == null) {
                failures.add("第" + rowNo + "行：用户名不能为空");
                continue;
            }
            if (!seen.add("user:" + userName)) {
                failures.add("第" + rowNo + "行：用户名重复 " + userName);
                continue;
            }
            String phone = StrUtil.trimToNull(row.getPhone());
            if (phone != null && !seen.add("phone:" + phone)) {
                failures.add("第" + rowNo + "行：手机号重复 " + phone);
                continue;
            }
            // 姓名同样是 NOT NULL 列：不校验就会把 null 带进 insert，
            // 触发 NOT NULL 违例后整批 saveBatch 回滚——一个空单元格废掉整个文件，
            // 而且失败明细里看不到是哪一行的问题。
            String realName = StrUtil.trimToNull(row.getRealName());
            if (realName == null) {
                failures.add("第" + rowNo + "行：姓名不能为空");
                continue;
            }
            if (realName.length() > 20) {
                failures.add("第" + rowNo + "行：姓名长度不能超过 20 个字符");
                continue;
            }
            pending.add(new PendingImportRow(rowNo, userName, phone,
                    realName, StrUtil.trimToNull(row.getEmail()),
                    parseStateText(row.getStateText()), row.getDeptText()));
        }

        // 一次查出库内已存在的用户名/手机号，避免逐行 selectCount
        Set<String> existingNames = new HashSet<>();
        Set<String> existingPhones = new HashSet<>();
        if (!pending.isEmpty()) {
            Set<String> names = pending.stream().map(PendingImportRow::userName).collect(Collectors.toSet());
            Set<String> phones = pending.stream().map(PendingImportRow::phone)
                    .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            LambdaQueryWrapper<SysUser> existQuery = new LambdaQueryWrapper<>();
            existQuery.select(SysUser::getUserName, SysUser::getPhone);
            if (phones.isEmpty()) {
                existQuery.in(SysUser::getUserName, names);
            } else {
                existQuery.and(w -> w.in(SysUser::getUserName, names)
                        .or().in(SysUser::getPhone, phones));
            }
            for (SysUser existing : baseMapper.selectList(existQuery)) {
                if (existing.getUserName() != null) {
                    existingNames.add(existing.getUserName());
                }
                if (existing.getPhone() != null) {
                    existingPhones.add(existing.getPhone());
                }
            }
        }

        List<SysUser> toInsert = new ArrayList<>(pending.size());
        for (PendingImportRow row : pending) {
            if (existingNames.contains(row.userName())
                    || (row.phone() != null && existingPhones.contains(row.phone()))) {
                failures.add("第" + row.rowNo() + "行：用户名或手机号已存在");
                continue;
            }
            SysUserImportContext.DeptResolution deptResolution = ctx.resolveDeptId(row.deptText());
            if (!deptResolution.ok()) {
                failures.add("第" + row.rowNo() + "行：" + deptResolution.message());
                continue;
            }
            SysUser user = new SysUser();
            user.setUserName(row.userName());
            user.setRealName(row.realName());
            user.setPhone(row.phone());
            user.setEmail(row.email());
            user.setState(row.state());
            user.setDeptId(deptResolution.deptId());
            user.setPassword(encryptedInitPassword);
            toInsert.add(user);
        }
        if (!toInsert.isEmpty()) {
            saveBatch(toInsert);
            List<SysUserDept> bindings = toInsert.stream()
                    .filter(user -> user.getId() != null && user.getDeptId() != null)
                    .map(user -> new SysUserDept(user.getId(), user.getDeptId()))
                    .collect(Collectors.toList());
            // 超管整批留空归属时不产生绑定，避免空集合批次
            if (!bindings.isEmpty()) {
                sysUserDeptMapper.insertBatch(bindings);
            }
        }

        result.setSuccessCount(toInsert.size());
        result.setFailureCount(failures.size());
        result.setFailures(failures);
        return result;
    }

    private record PendingImportRow(int rowNo, String userName, String phone,
                                    String realName, String email, int state, String deptText) {
    }

    /**
     * 状态文本解析：「停用 / 禁用 / 2」按停用处理，其余（含空）按正常
     */
    static int parseStateText(String stateText) {
        String value = StrUtil.trimToNull(stateText);
        if (value == null) {
            return 1;
        }
        return "停用".equals(value) || "禁用".equals(value) || "2".equals(value) ? 2 : 1;
    }

    public void setDataScope(SysUser user){
        Map<String,Object> dataScope = new HashMap<>();
        List<SysRole> roleList = user.getRoleList();
        roleList = roleList == null ? new ArrayList<>() : roleList;
        List<Integer> collect = roleList.stream().map(t -> t.getDataScope()).collect(Collectors.toList());

        // 全部数据：显式空 map（清掉对象复用时的陈旧值）
        if (collect.contains(1)) {
            user.setDataScope(new HashMap<>());
            return;
        }

        // 本部门及以下：child 解析为空时不回落本部门，最终落 none
        if (collect.contains(2) && user.getDeptId() != null) {
            Map<String, String> child = sysDeptService.getChildIdAndName(user.getDeptId());
            if (child != null && StrUtil.isNotBlank(child.get("id"))) {
                dataScope.put(BaseConstant.DATA_SCOPE_DEPT_PARENT_ID, child.get("id"));
                dataScope.put(BaseConstant.DATA_SCOPE_DEPT_PARENT_NAME, child.get("name"));
            }
        }
        // 本部门：只有消费本部门范围的角色且归属非空时才写 bmid/bmmc
        if (collect.contains(3) && user.getDeptId() != null) {
            dataScope.put(BaseConstant.DATA_SCOPE_DEPT_ID, String.valueOf(user.getDeptId()));
            dataScope.put(BaseConstant.DATA_SCOPE_DEPT_NAME, user.getDeptName());
        }
        if (collect.contains(4)) {
            dataScope.put(BaseConstant.DATA_SCOPE_SELF, user.getId());
            if (user.getDeptId() != null) {
                dataScope.put(BaseConstant.DATA_SCOPE_DEPT_ID, String.valueOf(user.getDeptId()));
                dataScope.put(BaseConstant.DATA_SCOPE_DEPT_NAME, user.getDeptName());
            }
        }
        if (collect.contains(5)) {
            List<Long> allDeptIds = new ArrayList<>();
            List<String> allDeptNames = new ArrayList<>();
            for (int i = 0; i < collect.size(); i++) {
                if (collect.get(i) == 5) {
                    Long roleId = roleList.get(i).getId();
                    Map<String,Object> query = new HashMap<>();
                    query.put("id",roleId);
                    List<Long> idList = sysDeptService.selectIdListByRoleId(query);
                    if (!idList.isEmpty()) {
                        allDeptIds.addAll(idList);
                        List<SysDept> deptList = sysDeptService.listByIds(idList);
                        List<String> names = deptList.stream().map(SysDept::getDeptName).collect(Collectors.toList());
                        allDeptNames.addAll(names);
                    }
                }
            }
            if (!allDeptNames.isEmpty()) {
                dataScope.put(BaseConstant.DATA_SCOPE_CUSTOM_NAME, StrUtil.join(",", allDeptNames));
            }
            if (!allDeptIds.isEmpty()) {
                dataScope.put(BaseConstant.DATA_SCOPE_CUSTOM_ID, StrUtil.join(",", allDeptIds));
            }
        }
        // 无角色 / 无可用范围：null 值不能出现在 map 里，统一落 none（fail-closed）
        if (dataScope.isEmpty()) {
            dataScope.put(BaseConstant.DATA_SCOPE_NONE, true);
        }
        user.setDataScope(dataScope);
    }
}
