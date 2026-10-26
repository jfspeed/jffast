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

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import jakarta.validation.Valid;
import cn.jfspeed.system.domain.dto.SysNotificationRequest;
import cn.jfspeed.security.annotation.DistributedLock;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.system.entity.NotificationStatRes;
import cn.jfspeed.system.entity.SysNotification;
import cn.jfspeed.system.entity.UserNotificationVO;
import cn.jfspeed.system.service.SysNotificationRecordService;
import cn.jfspeed.system.service.SysNotificationService;
import cn.jfspeed.security.annotation.Idempotent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * @author jffast
 */
@Tag(name = "系统通知")
@RestController
@RequestMapping("/sys/notification")
@ConditionalOnProperty(name = "jffast.system.notification.enabled", havingValue = "true")
public class SysNotificationController extends BaseController<SysNotificationService, SysNotification> {

    private final SysNotificationRecordService sysNotificationRecordService;

    public SysNotificationController(SysNotificationRecordService sysNotificationRecordService) {
        this.sysNotificationRecordService = sysNotificationRecordService;
    }

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:view")
    @Operation(summary = "分页查询")
    public R<PageRes<SysNotification>> page(@ModelAttribute SysNotification param, @ParameterObject PageReq pageReq) {
        return R.success(service.page(param, pageReq));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:view")
    @Operation(summary = "根据ID获取详情")
    public R<SysNotification> detail(@PathVariable("id") Long id) {
        return R.success(service.getById(id));
    }

    @PostMapping("/create")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:add")
    @Idempotent(expire = 5)
    @Operation(summary = "新增")
    @SLog(title = "新增系统通知")
    @RateLimit(value = "default")
    public R<String> create(@RequestBody @Valid SysNotificationRequest req) {
        SysNotification entity = BeanUtil.copyProperties(req, SysNotification.class);
        service.save(entity);
        return R.successMsg("新增成功");
    }

    @PutMapping("/update")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:update")
    @Idempotent(expire = 5)
    @Operation(summary = "修改")
    @SLog(title = "修改系统通知")
    @DistributedLock(key = "'notification:' + #req.id", expireTime = 10000)
    public R<String> update(@RequestBody @Valid SysNotificationRequest req) {
        SysNotification entity = BeanUtil.copyProperties(req, SysNotification.class);
        service.updateById(entity);
        return R.successMsg("修改成功");
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:delete")
    @Operation(summary = "删除")
    @SLog(title = "删除系统通知")
    public R<String> delete(@PathVariable("id") Long id) {
        sysNotificationRecordService.deleteByNotificationId(id);
        service.removeById(id);
        return R.successMsg("删除成功");
    }

    @GetMapping("/all")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:view")
    @Operation(summary = "获取全部")
    public R<List<SysNotification>> all() {
        return R.success(service.list());
    }

    @PostMapping("/send")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:send")
    @Idempotent(expire = 5)
    @Operation(summary = "发送通知")
    @SLog(title = "发送系统通知")
    @RateLimit(value = "default")
    public R<String> send(@RequestBody @Valid SysNotificationRequest req) {
        SysNotification entity = BeanUtil.copyProperties(req, SysNotification.class);
        service.sendNotification(entity);
        return R.successMsg("发送成功");
    }

    @PostMapping("/copy/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:add")
    @Idempotent(expire = 5)
    @Operation(summary = "复制通知")
    @SLog(title = "复制系统通知")
    public R<SysNotification> copy(@PathVariable("id") Long id) {
        return R.success(service.copyNotification(id));
    }

    @GetMapping("/stats/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:view")
    @Operation(summary = "通知发送统计")
    public R<NotificationStatRes> stats(@PathVariable("id") Long id,
                                         @RequestParam(required = false) String userName,
                                         @RequestParam(required = false) Integer isRead,
                                         @ParameterObject PageReq pageReq) {
        return R.success(sysNotificationRecordService.getNotificationStats(id, userName, isRead, pageReq));
    }

    // 登录态即可：本人通知列表（userId 取自登录态），故不设权限码
    @GetMapping("/userNotifications")
    @Operation(summary = "获取当前用户的通知列表")
    public R<List<UserNotificationVO>> userNotifications() {
        Long userId = LoginUtil.getLoginUser().getId();
        return R.success(sysNotificationRecordService.getUserNotifications(userId));
    }

    // 登录态即可：本人通知分页（userId 取自登录态），故不设权限码
    @GetMapping("/userNotificationPage")
    @Operation(summary = "分页查询当前用户的通知列表")
    public R<PageRes<UserNotificationVO>> userNotificationPage(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(required = false) String type,
            @ParameterObject PageReq pageReq) {
        Long userId = LoginUtil.getLoginUser().getId();
        return R.success(sysNotificationRecordService.getUserNotificationPage(userId, title, isRead, type, pageReq));
    }

    // 登录态即可：本人未读角标（userId 取自登录态），故不设权限码
    @GetMapping("/unreadCount")
    @Operation(summary = "获取当前用户未读通知数量")
    public R<Long> unreadCount() {
        Long userId = LoginUtil.getLoginUser().getId();
        return R.success(sysNotificationRecordService.getUnreadCount(userId));
    }

    // 登录态即可：本人通知标已读（service 内校验归属），故不设权限码
    @PutMapping("/markAsRead/{notificationId}")
    @Operation(summary = "标记通知为已读")
    public R<String> markAsRead(@PathVariable("notificationId") Long notificationId) {
        Long userId = LoginUtil.getLoginUser().getId();
        sysNotificationRecordService.markAsRead(notificationId, userId);
        return R.successMsg("操作成功");
    }

    // 登录态即可：本人通知全部标已读（userId 取自登录态），故不设权限码
    @PutMapping("/markAllAsRead")
    @Operation(summary = "标记全部通知为已读")
    public R<String> markAllAsRead() {
        Long userId = LoginUtil.getLoginUser().getId();
        sysNotificationRecordService.markAllAsRead(userId);
        return R.successMsg("操作成功");
    }
}
