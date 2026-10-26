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
package cn.jfspeed.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.demo.domain.dto.LeaveApplicationQueryRequest;
import cn.jfspeed.demo.domain.dto.LeaveApplicationRequest;
import cn.jfspeed.demo.entity.LeaveApplication;
import cn.jfspeed.demo.mapper.LeaveApplicationMapper;
import org.springframework.stereotype.Service;

/**
 * 请假申请服务
 *
 * <p><b>归属隔离</b>：请假申请是「个人提交、按人可见」的记录。写侧在新增时写入
 * {@code userId}，但读侧与改删侧此前**没有任何归属校验** ⇒ 任何拿到
 * {@code demo:leave:*} 权限的角色（种子菜单 205 默认授给超管，实际部署里通常会授给
 * 普通员工角色）都能读到全表请假事由，并按任意 id 改写、删除他人的申请。
 * 这里把归属校验下沉到服务层：控制器之外的任何调用方都绕不过去。
 *
 * <p>超管放行（与全项目「超管不受数据范围限制」的口径一致）。
 *
 * @author jffast
 */
@Service
public class LeaveApplicationService extends BaseService<LeaveApplicationMapper, LeaveApplication> {

    /**
     * 分页查询请假申请
     *
     * <p>非超管只返回**本人**提交的记录；超管返回全部（HR 视角）。
     */
    public PageRes<LeaveApplication> page(LeaveApplicationQueryRequest query) {
        LambdaQueryWrapper<LeaveApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getUserName()), LeaveApplication::getUserName, query.getUserName());
        wrapper.eq(StrUtil.isNotBlank(query.getLeaveType()), LeaveApplication::getLeaveType, query.getLeaveType());
        wrapper.eq(query.getStatus() != null, LeaveApplication::getStatus, query.getStatus());
        if (!LoginUtil.isSuperAdmin()) {
            wrapper.eq(LeaveApplication::getUserId, LoginUtil.getLoginUserId());
        }
        wrapper.orderByDesc(LeaveApplication::getCreateTime);
        return PageUtil.resultPageMp(baseMapper.selectPage(query.getPage(), wrapper));
    }

    /**
     * 读取单条请假申请，并校验归属（非超管只能读自己的）。
     */
    public LeaveApplication getOwnedById(Long id) {
        if (id == null) {
            throw new BusinessException(400, "请假申请 ID 不能为空");
        }
        LeaveApplication entity = getById(id);
        if (entity == null) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "请假申请不存在或无权访问");
        }
        assertOwnership(entity);
        return entity;
    }

    /**
     * 修改请假申请，并校验归属（非超管只能改自己的）。
     *
     * <p>{@code userId} / {@code userName} / {@code status} 不接受客户端提交：
     * 归属与状态由服务端维护，避免「改别人的申请」顺带把归属改走。
     */
    public void updateOwned(LeaveApplicationRequest req) {
        LeaveApplication existing = getOwnedById(req.getId());
        LeaveApplication entity = BeanUtil.copyProperties(req, LeaveApplication.class);
        entity.setId(existing.getId());
        entity.setUserId(existing.getUserId());
        entity.setUserName(existing.getUserName());
        entity.setStatus(existing.getStatus());
        updateById(entity);
    }

    /**
     * 删除请假申请，并校验归属（非超管只能删自己的）。
     */
    public void deleteOwned(Long id) {
        getOwnedById(id);
        removeById(id);
    }

    /** 归属校验：非超管只能操作 userId 为本人的记录 */
    private void assertOwnership(LeaveApplication entity) {
        if (LoginUtil.isSuperAdmin()) {
            return;
        }
        Long currentUserId = LoginUtil.getLoginUserId();
        if (currentUserId == null || !currentUserId.equals(entity.getUserId())) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权访问他人的请假申请");
        }
    }
}
