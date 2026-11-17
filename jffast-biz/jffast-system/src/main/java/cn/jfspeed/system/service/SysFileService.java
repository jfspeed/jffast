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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.jfspeed.common.core.context.TenantIds;
import cn.jfspeed.common.core.context.TenantScopeBridge;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.system.domain.dto.SysFileQueryRequest;
import cn.jfspeed.system.entity.SysFile;
import cn.jfspeed.system.mapper.SysFileMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件记录 服务实现
 *
 * @author jffast
 */
@Service
public class SysFileService extends BaseService<SysFileMapper, SysFile> {

    /** 租户上下文桥；Base 单租户无实现，Pro 的租户 Starter 提供 */
    private final ObjectProvider<TenantScopeBridge> tenantScopeBridge;

    public SysFileService(ObjectProvider<TenantScopeBridge> tenantScopeBridge) {
        this.tenantScopeBridge = tenantScopeBridge;
    }

    /**
     * 文件分页列表
     */
    public PageRes<SysFile> page(SysFileQueryRequest query) {
        return page(query, null);
    }

    /**
     * 文件分页列表（ownerId 非空时仅返回该用户登记的记录）
     *
     * <p>没有这一层过滤时，任何拿到 {@code system:file:view} 的人都能在一页里看到别人上传文件的
     * id 与 url，列表即泄露他人文件的存在与元数据（受控下载 / 签名直显虽仍有归属校验，但清单
     * 本身不该跨人可见）。归属口径与 {@link SysTaskService#page} 一致，按 create_by 隔离。
     *
     * <p><b>租户维度显式过滤</b>（AGENTS §2.1：基座不做租户兜底，必须自己过滤）：
     * 当前租户为 0（平台/单租户）时只看 {@code tenant_id = 0}；
     * 当前租户为 t 时只看 {@code tenant_id = t}。超管不豁免租户维度，
     * 否则跨租户隔离就是靠「超管手滑」维持的。
     */
    public PageRes<SysFile> page(SysFileQueryRequest query, Long ownerId) {
        LambdaQueryWrapper<SysFile> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(query.getFileName()), SysFile::getFileName, query.getFileName());
        queryWrapper.eq(StrUtil.isNotBlank(query.getBizType()), SysFile::getBizType, query.getBizType());
        queryWrapper.eq(StrUtil.isNotBlank(query.getStorageType()), SysFile::getStorageType, query.getStorageType());
        queryWrapper.eq(ownerId != null, SysFile::getCreateBy, ownerId);
        queryWrapper.eq(SysFile::getTenantId, currentTenantId());
        queryWrapper.orderByDesc(SysFile::getCreateTime);
        Page<SysFile> page = this.page(query.getPage(), queryWrapper);
        return PageUtil.resultPageMp(page);
    }

    /**
     * 按存储定位键反查记录（迁移脚本与兼容逻辑用；{@code idx_url} 支撑）。
     */
    public SysFile findByUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        LambdaQueryWrapper<SysFile> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysFile::getUrl, url);
        queryWrapper.last("limit 1");
        return this.getOne(queryWrapper, false);
    }

    /**
     * 按 ID 取记录并校验「归属 + 租户」。
     *
     * <p>越权与「记录不存在」必须是同一句提示、同一个分支：否则可以用返回值差异枚举出
     * 哪些 id 属于别人。超管只看得到本租户的文件（跨租户需切租户上下文）。
     *
     * @param id      文件 ID
     * @param ownerId 归属用户 ID；{@code null} = 不限归属（超管）
     * @return 命中的记录；不通过时返回 {@code null}
     */
    public SysFile getOwnedFile(Long id, Long ownerId) {
        if (id == null) {
            return null;
        }
        SysFile file = this.getById(id);
        if (file == null) {
            return null;
        }
        if (ownerId != null && !ownerId.equals(file.getCreateBy())) {
            return null;
        }
        Long tenantId = currentTenantId();
        Long fileTenant = TenantIds.normalize(file.getTenantId());
        if (!tenantId.equals(fileTenant)) {
            return null;
        }
        return file;
    }

    /**
     * 当前请求可见的归属：超管返回 null（全量），普通用户返回自己的用户 ID。
     *
     * <p>⚠️ 取不到登录用户时必须拒绝，不能退化成 null（null = 不限归属）。
     */
    public Long currentOwnerId() {
        if (LoginUtil.isSuperAdmin()) {
            return null;
        }
        Long userId = LoginUtil.getLoginUserId();
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.NOT_LOGIN.getCode(), "登录状态已失效，请重新登录");
        }
        return userId;
    }

    /** 当前租户（统一口径见 {@code TenantIds.resolve}） */
    public Long currentTenantId() {
        TenantScopeBridge bridge = null;
        try {
            if (tenantScopeBridge != null) {
                bridge = tenantScopeBridge.getIfAvailable();
            }
        } catch (Exception ignored) {
            // 取不到桥：退回登录用户租户
        }
        return TenantIds.resolve(bridge);
    }

    /**
     * 批量按 ID 取记录（换发签名 URL 时避免 N+1 查询）。
     *
     * @return id → 记录
     */
    public Map<Long, SysFile> mapByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return this.listByIds(ids).stream()
                .filter(f -> f.getId() != null)
                .collect(Collectors.toMap(SysFile::getId, f -> f, (a, b) -> a));
    }
}
