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
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.security.spi.LoginPolicyProvider;
import cn.jfspeed.common.security.util.PasswordGenerator;
import cn.jfspeed.common.security.util.RSAUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.jfspeed.system.entity.SysLog;
import cn.jfspeed.system.service.SysLogService;
import cn.jfspeed.system.domain.dto.SysPasswordRequest;
import cn.jfspeed.system.domain.dto.SysResetPasswordRequest;
import cn.jfspeed.system.domain.dto.SysUserExcel;
import cn.jfspeed.system.domain.dto.SysUserImportExcel;
import cn.jfspeed.system.domain.dto.SysUserImportResult;
import cn.jfspeed.system.domain.dto.SysUserProfileRequest;
import cn.jfspeed.system.domain.dto.SysUserSaveRequest;
import cn.jfspeed.system.domain.vo.SysUserVO;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysTask;
import cn.jfspeed.system.handler.SysUserImportTemplateHandler;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.config.JfFastSystemProperties;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.file.service.FileStorageService;
import cn.jfspeed.security.util.DataPermissionSqlBuilder;
import cn.jfspeed.system.service.SysDeptScopeQuery;
import cn.jfspeed.system.service.SysDeptService;
import cn.jfspeed.system.service.SysRoleService;
import cn.jfspeed.system.service.SysSafeService;
import cn.jfspeed.system.service.SysTaskContext;
import cn.jfspeed.system.service.SysTaskRunner;
import cn.jfspeed.system.service.SysTaskService;
import cn.jfspeed.system.service.SysUserImportContext;
import cn.jfspeed.system.service.SysUserImportContextFactory;
import cn.jfspeed.system.service.SysUserService;
import cn.jfspeed.excel.util.ExcelUtil;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.DistributedLock;
import cn.jfspeed.security.annotation.Idempotent;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.common.security.util.EncryptionUtil;
import cn.jfspeed.common.web.domain.dto.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author jffast
 */
@Slf4j
@RestController
@RequestMapping("/sys/user")
@Tag(name = "用户管理")
public class SysUserController extends BaseController<SysUserService,SysUser> {

    /** 导出行数上限：防止一次导出拖垮内存 */
    private static final int EXPORT_MAX_ROWS = 10000;

    /** 异步导出行数上限 */
    private static final int ASYNC_EXPORT_MAX_ROWS = 100000;

    /** 异步处理批大小（分批转 Excel / 分批入库并回报进度） */
    private static final int ASYNC_BATCH_SIZE = 500;

    /** 单次导入行数上限：同步/异步共用，防止超大文件拖垮内存与数据库 */
    private static final int IMPORT_MAX_ROWS = 10000;

    @Resource
    private SysSafeService sysSafeService;
    @Resource
    private FileStorageService fileStorageService;
    @Resource
    private SysLogService sysLogService;
    @Resource
    private SysTaskService sysTaskService;
    @Resource
    private SysTaskRunner sysTaskRunner;
    @Resource
    private SysRoleService sysRoleService;
    @Resource
    private SysDeptService sysDeptService;
    @Resource
    private SysDeptScopeQuery sysDeptScopeQuery;
    @Resource
    private SysUserImportContextFactory sysUserImportContextFactory;
    /** 初始密码（jffast.system.init-password）：重置密码写入值 + 新密码禁用值的比对基准 */
    @Resource
    private JfFastSystemProperties jfFastSystemProperties;

    /**
     * 密码策略扩展点（可选）：可插拔安全策略 Starter 提供实现后生效，
     * Base 单独运行时为空实现，改密流程行为与改造前一致。
     */
    private final ObjectProvider<LoginPolicyProvider> loginPolicyProvider;

    public SysUserController(ObjectProvider<LoginPolicyProvider> loginPolicyProvider) {
        this.loginPolicyProvider = loginPolicyProvider;
    }

    // 登录态即可：本人信息（userId 取自登录态），任何登录用户都可见，故不设权限码
    @GetMapping("/getLoginUser")
    @Operation(summary = "获取当前登录用户信息")
    public R getLoginUserInfo() {
        Map<String,Object> map = new HashMap<>();
        SysUser user = (SysUser) LoginUtil.getLoginSysUser();
        if ("1".equals(user.getIsBusinessUser())) {
            map.put("user", SysUserVO.from(user));
            map.put("permissions", LoginUtil.getPermList(Convert.toList(Long.class,user.getRoleId())));
        } else {
            if (user.getUpdatePwdTime() == null){
                SysUser byId = service.getById(user.getId());
                if (byId == null) {
                    return R.error("用户不存在");
                }
                user.setUpdatePwdTime(byId.getUpdatePwdTime());
            }
            sysSafeService.getPwdCycle(map,user.getUpdatePwdTime());
            map.put("user", SysUserVO.from(user));
            List<Long> roleIdList = user.getRoleList() == null ? new ArrayList<>() : user.getRoleList().stream().map(t->t.getId()).collect(Collectors.toList());
            map.put("permissions", LoginUtil.getPermList(roleIdList));
        }
        return R.success(map);
    }

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:view")
    @Operation(summary = "用户列表")
    public R list(@RequestParam Map<String,Object> map, @ParameterObject PageReq pageReq) {
        pageReq.setQueryBean(map);
        return R.success(service.page(pageReq));
    }

    @GetMapping("/all")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:view")
    @Operation(summary = "获取全部用户")
    public R<List<SysUserVO>> all() {
        List<SysUser> users = service.listAll();
        // 出参走 VO 白名单：password / salt / dataScope 等内部字段不再随响应外泄
        return R.success(SysUserVO.fromList(users));
    }

    @GetMapping("/export")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:view")
    @Operation(summary = "导出用户Excel")
    public void export(HttpServletResponse response, @RequestParam Map<String, Object> map) {
        List<SysUser> list = service.listForExport(map, EXPORT_MAX_ROWS);
        List<SysUserExcel> excelList = list.stream().map(user -> {
            SysUserExcel excel = new SysUserExcel();
            excel.setUserName(user.getUserName());
            excel.setRealName(user.getRealName());
            excel.setPhone(user.getPhone());
            excel.setEmail(user.getEmail());
            excel.setStateText(user.getState() != null && user.getState() == 2 ? "停用" : "正常");
            return excel;
        }).collect(Collectors.toList());
        ExcelUtil.export(response, "用户数据.xlsx", excelList, SysUserExcel.class);
    }

    @GetMapping("/import-template")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:add")
    @Operation(summary = "导入模板下载")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtil.export(response, "用户导入模板.xlsx", List.of(), SysUserImportExcel.class,
                new SysUserImportTemplateHandler());
    }

    @SLog(title = "导入用户")
    @PostMapping("/import")
    @RateLimit(value = "default")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:add")
    @Operation(summary = "导入用户Excel（密码为初始密码）")
    public R<SysUserImportResult> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            List<SysUserImportExcel> rows = ExcelUtil.read(in, SysUserImportExcel.class);
            if (rows.size() > IMPORT_MAX_ROWS) {
                return R.error("单次导入不能超过 " + IMPORT_MAX_ROWS + " 行");
            }
            // 请求线程内固化可见部门：导入服务与异步 worker 只读快照，不查询登录态
            SysUserImportContext ctx = sysUserImportContextFactory.create();
            return R.success(service.importUsers(rows, ctx));
        }
    }

    @PostMapping("/export-async")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:view")
    @RateLimit(value = "default")
    @Idempotent(expire = 5)
    @Operation(summary = "异步导出用户Excel")
    public R exportAsync(@RequestParam Map<String, Object> map) {
        // 异步线程没有登录态：提交前按注解元数据固化数据权限片段，解析失败即中止且不创建任务
        String dataScopeSql = DataPermissionSqlBuilder.resolve(
                SysUserService.class, "listForExport", Map.class, int.class);
        Long operatorId = LoginUtil.getLoginUserId();
        SysTask task = sysTaskService.createTask("export", "user", "用户异步导出");
        sysTaskRunner.submit(new SysTaskContext(operatorId, dataScopeSql), () -> {
            sysTaskService.markRunning(task.getId());
            try {
                List<SysUser> list = service.listForExport(map, ASYNC_EXPORT_MAX_ROWS);
                sysTaskService.markTotal(task.getId(), list.size());
                List<SysUserExcel> excelList = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++) {
                    SysUser user = list.get(i);
                    SysUserExcel excel = new SysUserExcel();
                    excel.setUserName(user.getUserName());
                    excel.setRealName(user.getRealName());
                    excel.setPhone(user.getPhone());
                    excel.setEmail(user.getEmail());
                    excel.setStateText(user.getState() != null && user.getState() == 2 ? "停用" : "正常");
                    excelList.add(excel);
                    if ((i + 1) % ASYNC_BATCH_SIZE == 0) {
                        sysTaskService.markProgress(task.getId(), (i + 1) * 100 / list.size());
                    }
                }
                Path file = sysTaskRunner.taskDir().resolve("export-" + task.getId() + ".xlsx");
                try (OutputStream out = Files.newOutputStream(file)) {
                    ExcelUtil.export(out, excelList, SysUserExcel.class);
                }
                sysTaskService.markSuccess(task.getId(), file.toString(), "用户数据.xlsx", list.size(), list.size(), 0, null);
            } catch (Exception e) {
                log.error("异步导出用户失败", e);
                sysTaskService.markFail(task.getId(), e.getMessage());
            }
        });
        return R.success(task);
    }

    @SLog(title = "异步导入用户")
    @PostMapping("/import-async")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:add")
    @RateLimit(value = "default")
    @Idempotent(expire = 5)
    @Operation(summary = "异步导入用户Excel（密码为初始密码）")
    public R importAsync(@RequestParam("file") MultipartFile file) throws IOException {
        // 必须在请求线程固化导入上下文：worker 没有登录态，届时查部门会退化为全量部门
        SysUserImportContext ctx = sysUserImportContextFactory.create();
        SysTask task = sysTaskService.createTask("import", "user", "用户异步导入");
        Path temp = sysTaskRunner.taskDir().resolve("import-" + task.getId() + ".xlsx");
        file.transferTo(temp.toFile());
        sysTaskRunner.submit(new SysTaskContext(LoginUtil.getLoginUserId(), null), () -> {
            sysTaskService.markRunning(task.getId());
            try {
                List<SysUserImportExcel> rows;
                try (InputStream in = Files.newInputStream(temp)) {
                    rows = ExcelUtil.read(in, SysUserImportExcel.class);
                }
                // 服务层只校验单个分片，整份文件的总行数上限必须在这里拦（与同步导入同文案）
                if (rows.size() > IMPORT_MAX_ROWS) {
                    sysTaskService.markFail(task.getId(), "单次导入不能超过 " + IMPORT_MAX_ROWS + " 行");
                    return;
                }
                sysTaskService.markTotal(task.getId(), rows.size());
                int success = 0;
                int fail = 0;
                StringBuilder failDetail = new StringBuilder();
                for (int start = 0; start < rows.size(); start += ASYNC_BATCH_SIZE) {
                    int end = Math.min(start + ASYNC_BATCH_SIZE, rows.size());
                    SysUserImportResult result = service.importUsers(rows.subList(start, end), start, ctx);
                    success += result.getSuccessCount();
                    fail += result.getFailureCount();
                    for (String failure : result.getFailures()) {
                        if (failDetail.length() > 0) {
                            failDetail.append('\n');
                        }
                        failDetail.append(failure);
                    }
                    sysTaskService.markProgress(task.getId(), end * 100 / rows.size());
                }
                sysTaskService.markSuccess(task.getId(), null, null, rows.size(), success, fail,
                        failDetail.length() == 0 ? null : failDetail.toString());
            } catch (Exception e) {
                log.error("异步导入用户失败", e);
                sysTaskService.markFail(task.getId(), e.getMessage());
            } finally {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    log.warn("删除导入临时文件失败：{}", temp, e);
                }
            }
        });
        return R.success(task);
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:view")
    @Operation(summary = "根据id获取用户")
    public R<SysUserVO> getById(@PathVariable("id")Long id) {
        SysUser user;
        try {
            user = service.getScopedUser(id);
        } catch (BusinessException e) {
            return R.error(e.getCode(), e.getMessage());
        }
        // 出参走 VO 白名单：password / salt / dataScope / role_id 等内部字段不再随响应外泄
        return R.success(SysUserVO.from(user));
    }

    @SLog(title = "新增用户")
    @PostMapping("/create")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:add")
    @RateLimit(value = "default")
    // 按用户名加锁：应用层的唯一性检查是「先查后插」，两个管理员并发建同名用户会同时
    // 通过校验（@Idempotent 的键含登录用户 id，互相不阻塞）。加锁把同名的创建请求串行化，
    // 由 sys_user 的唯一约束 uk_user_name 兜底。
    @DistributedLock(key = "'user:create:' + #req.userName", expireTime = 10000)
    @Idempotent(expire = 5, message = "请勿重复提交新增用户请求")
    @Operation(summary = "新增用户")
    public R insert(@RequestBody @Validated SysUserSaveRequest req) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (req.getId() != null) {
            return R.error("新增用户不允许指定用户ID");
        }
        if (StrUtil.isBlank(req.getPassword())) {
            return R.error("密码不能为空");
        }
        String authError = validateCreateAuthorization(req);
        if (authError != null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), authError);
        }
        if (isCurrentSuperAdmin()) {
            if (req.getDeptId() != null && sysDeptService.getById(req.getDeptId()) == null) {
                return R.error(FrameworkErrorCode.PARAM_ERROR.getCode(), "部门不存在");
            }
        } else {
            if (req.getDeptId() == null) {
                return R.error(AuthErrorCode.FORBIDDEN.getCode(), "请指定数据范围内的部门");
            }
            if (sysDeptScopeQuery.findVisibleDept(req.getDeptId()) == null) {
                return R.error(AuthErrorCode.FORBIDDEN.getCode(), "部门不存在或无权使用该部门");
            }
        }
        if (BaseConstant.SUPER_ADMIN.equals(req.getUserName()) && !isCurrentSuperAdmin()) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), "无权创建超级管理员账号");
        }
        SysUser user = toUser(req);
        if (service.checkUserNameAndPhone(user.getUserName(), user.getPhone(), null)) {
            return R.error("用户名或手机号码已存在");
        }
        String password = RSAUtil.decryptPwdWithTimestamp(req.getPassword());
        password = EncryptionUtil.getEncryptPassword(password);
        user.setPassword(password);
        service.saveUser(user, req.getRoleList() != null, req.getDeptList() != null);
        return R.success();
    }

    @SLog(title = "修改用户")
    @PutMapping("/update")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:update")
    @DistributedLock(key = "'user:' + #req.id", expireTime = 10000)
    @Idempotent(expire = 5)
    @Operation(summary = "修改用户")
    public R update(@RequestBody @Validated SysUserSaveRequest req) {
        if (req.getId() == null) {
            return R.error("用户ID不能为空");
        }
        SysUser target;
        try {
            target = service.getScopedUser(req.getId());
        } catch (BusinessException e) {
            return R.error(e.getCode(), e.getMessage());
        }
        String manageError = validateManageable(target);
        if (manageError != null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), manageError);
        }
        String authError = validateUpdateAuthorization(target, req);
        if (authError != null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), authError);
        }
        if (isCurrentSuperAdmin() && req.getDeptId() != null
                && sysDeptService.getById(req.getDeptId()) == null) {
            return R.error(FrameworkErrorCode.PARAM_ERROR.getCode(), "部门不存在");
        }
        if (BaseConstant.SUPER_ADMIN.equals(req.getUserName())
                && !BaseConstant.SUPER_ADMIN.equals(target.getUserName())
                && !isCurrentSuperAdmin()) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), "无权使用超级管理员用户名");
        }
        SysUser user = toUser(req);
        if (service.checkUserNameAndPhone(user.getUserName(), user.getPhone(), user.getId())) {
            return R.error("用户名或手机号码已存在");
        }
        service.saveUser(user, req.getRoleList() != null, req.getDeptList() != null);
        if (user.getId().equals(loginId())) {
            // 自我更新已禁止改角色/部门/deptId，此处只刷新非授权字段
            SysUser userById = service.getUser(user.getId(), null, null);
            service.setDataScope(userById);
            LoginUtil.updateUser(userById);
        }
        return R.success();
    }

    @SLog(title = "删除用户")
    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:delete")
    @Operation(summary = "删除用户")
    public R delete(@PathVariable("id") Long id) {
        if (id.equals(loginId())) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), "不能删除当前登录用户");
        }
        SysUser user;
        try {
            user = service.getScopedUser(id);
        } catch (BusinessException e) {
            return R.error(e.getCode(), e.getMessage());
        }
        String manageError = validateManageable(user);
        if (manageError != null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), manageError);
        }
        service.deleteUser(id);
        kickoutQuietly(id);
        return R.success();
    }

    // 登录态即可：本人改密（方法内强制 loginId + @RateLimit），普通用户必须能自助改密，故不设权限码
    @SLog(title = "修改密码")
    @PostMapping("/updatePassword")
    @RateLimit(value = "default")
    @Idempotent(expire = 5)
    @Operation(summary = "修改密码")
    public R updatePassword(@RequestBody @Validated SysPasswordRequest passwordDto) throws InvalidKeySpecException, NoSuchAlgorithmException {
        // 仅允许修改当前登录用户本人的密码（目标始终为登录态，不接受请求体指定用户）
        Long currentId = loginId();
        String oldPassword = RSAUtil.decryptPwdWithTimestamp(passwordDto.getOldPassword());
        String newPassword = RSAUtil.decryptPwdWithTimestamp(passwordDto.getNewPassword());
        if (StrUtil.isEmpty(oldPassword) || StrUtil.isEmpty(newPassword)){
            return R.error("数据解密失败");
        }
        // 初始密码未配置时（initPassword 为空）跳过该比对——不要对一个可能为 null 的值直接 equals
        String initPassword = jfFastSystemProperties.getInitPassword();
        if (StrUtil.isNotBlank(initPassword) && initPassword.equals(newPassword)){
            return R.error("新密码不可与初始密码相同");
        }
        if (!validatePasswordStrength(newPassword)) {
            return R.error("密码强度不足：需8-20位，包含大小写字母、数字和特殊字符");
        }
        // 密码策略扩展点（可插拔 Starter 提供实现后生效）：可配置复杂度、历史密码不可复用、弱口令拦截
        LoginPolicyProvider loginPolicyProvider = this.loginPolicyProvider.getIfAvailable();
        if (loginPolicyProvider != null) {
            loginPolicyProvider.validateNewPassword(currentId, newPassword);
        }
        SysUser user = service.getById(currentId);
        if (user == null) {
            return R.error("用户不存在");
        }
        boolean authenticate = EncryptionUtil.verifyPassword(oldPassword, user.getPassword());
        if (!authenticate){
            return R.error("旧密码错误");
        }
        String password = EncryptionUtil.getEncryptPassword(newPassword);
        UpdateWrapper<SysUser> updateWrapper = new UpdateWrapper<>();
        updateWrapper.set("password", password);
        updateWrapper.set("update_pwd_time", LocalDateTime.now());
        updateWrapper.eq("id",user.getId());
        service.update(updateWrapper);
        return R.success();
    }

    @SLog(title = "重置密码")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:resetPassword")
    @PostMapping("/resetPassword")
    @Idempotent(expire = 5)
    @Operation(summary = "重置密码（生成随机强口令并在响应中一次性返回）")
    public R resetPassword(@RequestBody @Validated SysResetPasswordRequest req) {
        SysUser user;
        try {
            user = service.getScopedUser(req.getUserId());
        } catch (BusinessException e) {
            return R.error(e.getCode(), e.getMessage());
        }
        String manageError = validateManageable(user);
        if (manageError != null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), manageError);
        }
        // 重置一律生成随机强口令，不再写入 init-password：
        // 否则所有被重置的账号会共用同一个（可能已随仓库公开的）口令，
        // 且重置操作者未必知道该口令已在别处暴露。明文仅在本次响应中返回一次。
        String rawPassword = PasswordGenerator.generate();
        String password = EncryptionUtil.getEncryptPassword(rawPassword);
        UpdateWrapper<SysUser> updateWrapper = new UpdateWrapper<>();
        updateWrapper.set("password", password);
        updateWrapper.set("update_pwd_time", LocalDateTime.now());
        updateWrapper.eq("id",user.getId());
        if (!service.update(updateWrapper)) {
            return R.error("密码重置失败");
        }
        kickoutQuietly(user.getId());
        // 一次性返回明文，供操作者转告用户；DB 只存加密值
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("userName", user.getUserName());
        data.put("password", rawPassword);
        return R.success(data);
    }

    @SLog(title = "解锁账号")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:unlock")
    @PostMapping("/unlock")
    @Operation(summary = "解锁账号（清除登录失败计数）")
    public R unlock(@RequestParam Long userId) {
        SysUser user;
        try {
            user = service.getScopedUser(userId);
        } catch (BusinessException e) {
            return R.error(e.getCode(), e.getMessage());
        }
        String manageError = validateManageable(user);
        if (manageError != null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), manageError);
        }
        RedisUtil.del(BaseConstant.CACHE_ERROR_COUNT + user.getUserName());
        return R.success("账号解锁成功");
    }

    /**
     * 密码强度校验：8-20位，包含大小写字母、数字和特殊字符
     */
    private boolean validatePasswordStrength(String password) {
        if (password == null || password.length() < 8 || password.length() > 20) {
            return false;
        }
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    /** 当前登录用户是否为超级管理员（角色码或内置超管角色 ID 任一命中） */
    private boolean isCurrentSuperAdmin() {
        return LoginUtil.getRoleCodeList().contains(BaseConstant.ROLE_SUPER_ADMIN)
                || LoginUtil.getRoleIdList().contains(BaseConstant.ROLE_ID_SUPER_ADMIN_LONG);
    }

    /** 目标用户是否为超级管理员：内置用户名或超管角色 */
    private boolean isSuperAdminUser(SysUser user) {
        if (BaseConstant.SUPER_ADMIN.equals(user.getUserName())) {
            return true;
        }
        return user.getRoleList() != null && user.getRoleList().stream()
                .anyMatch(role -> role != null && BaseConstant.ROLE_ID_SUPER_ADMIN_LONG.equals(role.getId()));
    }

    /**
     * 非超管不得修改/删除/重置/解锁超级管理员账号，且只能操作角色与数据范围均不高于自己的目标
     */
    private String validateManageable(SysUser target) {
        if (isSuperAdminUser(target) && !isCurrentSuperAdmin()) {
            return "无权操作超级管理员账号";
        }
        if (isCurrentSuperAdmin()) {
            return null;
        }
        SysRoleService.OperatorContext operator = sysRoleService.loadOperatorContext();
        List<SysRole> targetRoles = target.getRoleList() == null ? List.of() : target.getRoleList();
        Set<Long> targetRoleIds = targetRoles.stream()
                .filter(Objects::nonNull)
                .map(SysRole::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!new HashSet<>(operator.roleIds()).containsAll(targetRoleIds)) {
            return "无权操作该用户";
        }
        int targetRank = targetRoles.stream()
                .filter(Objects::nonNull)
                .mapToInt(role -> SysRoleService.roleRank(role.getDataScope()))
                .max().orElse(0);
        if (targetRank > operator.rank()) {
            return "无权操作该用户";
        }
        return null;
    }

    /** 创建：非超管不得分配任何角色/部门（null 与 [] 等价空） */
    private String validateCreateAuthorization(SysUserSaveRequest req) {
        if (isCurrentSuperAdmin()) {
            return null;
        }
        if (hasAnyId(req.getRoleList()) || hasAnyId(req.getDeptList())) {
            return "无权分配角色或部门";
        }
        return null;
    }

    /** 更新：自我改集合一律拒绝；非超管改他人集合一律拒绝；admin 账号的角色不可改 */
    private String validateUpdateAuthorization(SysUser target, SysUserSaveRequest req) {
        boolean self = target.getId().equals(loginId());
        boolean superAdmin = isCurrentSuperAdmin();

        if (req.getRoleList() != null && !sameRoleSet(req.getRoleList(), target.getRoleList())) {
            if (self) {
                return "不能修改自己的角色或部门";
            }
            if (!superAdmin) {
                return "无权分配角色或部门";
            }
            if (BaseConstant.SUPER_ADMIN.equals(target.getUserName())) {
                return "内置超管账号的角色不允许修改";
            }
        }
        if (req.getDeptList() != null && !sameDeptSet(req.getDeptList(), target.getDeptList())) {
            if (self) {
                return "不能修改自己的角色或部门";
            }
            if (!superAdmin) {
                return "无权分配角色或部门";
            }
        }
        if (req.getDeptId() != null && !Objects.equals(req.getDeptId(), target.getDeptId())) {
            if (self) {
                return "不能修改自己的部门";
            }
            if (!superAdmin) {
                return "无权修改该用户部门";
            }
        }
        return null;
    }

    private static boolean hasAnyId(List<SysUserSaveRequest.IdRef> list) {
        return list != null && list.stream().anyMatch(item -> item != null && item.getId() != null);
    }

    private static boolean sameRoleSet(List<SysUserSaveRequest.IdRef> requested, List<SysRole> current) {
        Set<Long> requestedIds = idRefIds(requested);
        Set<Long> currentIds = current == null ? new HashSet<>() : current.stream()
                .filter(Objects::nonNull).map(SysRole::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return requestedIds.equals(currentIds);
    }

    private static boolean sameDeptSet(List<SysUserSaveRequest.IdRef> requested, List<SysDept> current) {
        Set<Long> requestedIds = idRefIds(requested);
        Set<Long> currentIds = current == null ? new HashSet<>() : current.stream()
                .filter(Objects::nonNull).map(SysDept::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return requestedIds.equals(currentIds);
    }

    private static Set<Long> idRefIds(List<SysUserSaveRequest.IdRef> refs) {
        if (refs == null) {
            return new HashSet<>();
        }
        return refs.stream()
                .filter(Objects::nonNull)
                .map(SysUserSaveRequest.IdRef::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** 删除/重置后踢下线：失败不回滚业务，只记录告警 */
    private void kickoutQuietly(Long userId) {
        try {
            StpUtil.kickout(userId);
        } catch (Exception e) {
            log.warn("踢下线失败，userId={}", userId, e);
        }
    }

    /** DTO → 实体：只映射白名单字段，不暴露 salt/createBy/dataScope 等 */
    private SysUser toUser(SysUserSaveRequest req) {
        SysUser user = new SysUser();
        user.setId(req.getId());
        user.setUserName(req.getUserName());
        user.setRealName(req.getRealName());
        user.setNickName(req.getNickName());
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setAvatar(req.getAvatar());
        user.setState(req.getState());
        user.setDeptId(req.getDeptId());
        if (req.getRoleList() != null) {
            user.setRoleList(req.getRoleList().stream()
                    .filter(r -> r != null && r.getId() != null)
                    .map(SysUserSaveRequest.IdRef::getId)
                    .distinct()
                    .map(roleId -> {
                        SysRole role = new SysRole();
                        role.setId(roleId);
                        return role;
                    })
                    .collect(Collectors.toList()));
        }
        if (req.getDeptList() != null) {
            user.setDeptList(req.getDeptList().stream()
                    .filter(d -> d != null && d.getId() != null)
                    .map(SysUserSaveRequest.IdRef::getId)
                    .distinct()
                    .map(deptId -> {
                        SysDept dept = new SysDept();
                        dept.setId(deptId);
                        return dept;
                    })
                    .collect(Collectors.toList()));
        }
        return user;
    }

    // 登录态即可：通用附件上传（个人资料场景），登录态 + @RateLimit 兜底，故不设权限码
    @SLog(title = "上传文件")
    @PostMapping("/upload")
    @RateLimit(value = "default")
    @Operation(summary = "上传文件")
    public R upload(@RequestParam MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String imgUrl = fileStorageService.uploadFile("user-file", fileName, file);
        return R.success(imgUrl);
    }

    // 登录态即可：本人头像上传，登录态 + @RateLimit 兜底，故不设权限码
    @SLog(title = "上传头像")
    @PostMapping("/uploadHead")
    @RateLimit(value = "default")
    @Operation(summary = "上传头像")
    public R uploadHead(@RequestParam MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String imgUrl = fileStorageService.uploadImgFile("user-avatar", fileName, file);
        UpdateWrapper<SysUser> updateWrapper = new UpdateWrapper<>();
        updateWrapper.set("avatar",imgUrl);
        updateWrapper.eq("id", loginId());
        service.update(updateWrapper);
        return R.success(imgUrl);
    }

    // 登录态即可：本人资料（方法内仅取登录态用户），故不设权限码
    /**
     * 修改个人资料（仅当前登录用户本人）
     */
    @SLog(title = "修改个人资料")
    @PostMapping("/updateProfile")
    @Idempotent(expire = 5)
    @Operation(summary = "修改个人资料")
    public R<SysUserVO> updateProfile(@RequestBody @Validated SysUserProfileRequest req) {
        Long currentId = loginId();
        if (req.getId() == null || !req.getId().equals(currentId)) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), "只能修改本人资料");
        }
        if (StrUtil.isNotBlank(req.getPhone()) && service.checkUserNameAndPhone(null, req.getPhone(), currentId)) {
            return R.error(FrameworkErrorCode.PARAM_ERROR.getCode(), "手机号码已被占用");
        }
        SysUser update = new SysUser();
        update.setId(currentId);
        update.setRealName(req.getRealName());
        update.setNickName(req.getNickName());
        update.setEmail(req.getEmail());
        update.setPhone(req.getPhone());
        update.setAvatar(req.getAvatar());
        service.updateById(update);
        SysUser fresh = service.getUser(currentId, null, null);
        service.setDataScope(fresh);
        LoginUtil.updateUser(fresh);
        // 出参走 VO 白名单：会话里仍存完整实体，只有响应体被裁剪
        return R.success(SysUserVO.from(fresh));
    }

    @Operation(summary = "当前用户登录日志")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:user:view")
    @GetMapping("/loginLogs")
    public R loginLogs() {
        LambdaQueryWrapper<SysLog> query = new LambdaQueryWrapper<>();
        query.eq(SysLog::getCreateBy, loginId());
        query.eq(SysLog::getTitle, "用户登录");
        query.orderByDesc(SysLog::getCreateTime);
        query.last("limit 20");
        return R.success(sysLogService.list(query));
    }

}
