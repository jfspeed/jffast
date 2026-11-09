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
import cn.jfspeed.common.core.context.TenantIds;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.file.FileRefs;
import cn.jfspeed.common.security.util.FileUrlSignUtil;
import cn.jfspeed.file.service.FileUrlSigner;
import cn.jfspeed.system.entity.SysFile;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件签名 URL 的**唯一签发入口**。
 *
 * <p>⚠️ 铁律：任何路径都**不允许**「见到 {@code file:{id}} 就签」。
 * 签发前必须先确认「当前调用方有权看这个文件」，否则等于把「知道 ID」变成「能读文件」：
 * 攻击者只要枚举 ID 就能拿到全站文件的直链。
 *
 * <p>两条签发口径：
 * <ul>
 *   <li>{@link #signOwned(Long)}：严格归属（文件属于当前登录用户；超管不限归属），
 *       供 {@code GET /sys/file/signed-url/{id}} 与管理页预览使用；</li>
 *   <li>{@link #signAttachment(Long)}：只校验租户可见，不校验上传者，
 *       供「随业务记录一起展示」的附件使用（头像、示例图片）——
 *       调用方已确认有权查看承载该附件的记录，附件本身允许被记录的所有看客读到。</li>
 * </ul>
 *
 * <p>签名 URL 是 **API 根相对路径**（{@code /sys/file/view/{id}?ticket=...}）：
 * 部署形态不同（单体 context-path / 网关 /api 前缀）时外部前缀不同，
 * 由前端按与 axios 相同的 baseURL 拼接，避免后端猜错前缀导致图片全裂。
 *
 * @author jffast
 */
@Service
public class FileUrlSignService {

    /** 匿名直显端点（相对 API 根） */
    public static final String VIEW_PATH_PREFIX = FileUrlSigner.VIEW_PATH_PREFIX;

    private final SysFileService sysFileService;
    private final FileUrlSigner signer;

    public FileUrlSignService(SysFileService sysFileService, FileUrlSigner signer) {
        this.sysFileService = sysFileService;
        this.signer = signer;
    }

    /**
     * 严格归属签发：文件必须属于当前登录用户（超管不限归属）。
     *
     * @throws BusinessException 记录不存在 / 不属于当前用户 / 跨租户
     */
    public String signOwned(Long fileId) {
        SysFile file = sysFileService.getOwnedFile(fileId, sysFileService.currentOwnerId());
        if (file == null) {
            throw new BusinessException(FileUrlSignUtil.INVALID_MESSAGE);
        }
        return sign(file);
    }

    /**
     * 记录附件签发：只校验租户可见，不校验上传者。
     *
     * <p>调用方必须已经确认「当前用户有权查看承载该附件的业务记录」。
     *
     * @return 签名 URL；记录不存在或跨租户时返回 {@code null}（不抛异常）
     */
    public String signAttachment(Long fileId) {
        if (fileId == null) {
            return null;
        }
        SysFile file = sysFileService.getById(fileId);
        if (file == null) {
            return null;
        }
        Long tenantId = sysFileService.currentTenantId();
        if (!tenantId.equals(TenantIds.normalize(file.getTenantId()))) {
            return null;
        }
        return sign(file);
    }

    /**
     * 批量换发（避免列表页 N+1 查询）。
     *
     * @return id → 签名 URL（只含签发成功的）
     */
    public Map<Long, String> signAttachments(Collection<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = fileIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Long tenantId = sysFileService.currentTenantId();
        Map<Long, SysFile> files = sysFileService.mapByIds(ids);
        Map<Long, String> result = new LinkedHashMap<>();
        for (Long id : ids) {
            SysFile file = files.get(id);
            if (file == null) {
                continue;
            }
            if (!tenantId.equals(TenantIds.normalize(file.getTenantId()))) {
                continue;
            }
            result.put(id, sign(file));
        }
        return result;
    }

    /**
     * 把库里存的引用（{@code file:{id}}）换发成签名 URL。
     *
     * <p>非本形态（历史裸 URL / 空值）或换发失败时返回 {@code null}，
     * 由调用方决定是留空还是落占位图——**不抛异常**，避免历史数据把整个列表接口打挂。
     */
    public String resolveStoredRef(String storedValue) {
        Long fileId = FileRefs.parseId(storedValue);
        if (fileId == null) {
            return null;
        }
        return signAttachment(fileId);
    }

    /**
     * 用户头像出参换发：{@code file:{id}} → 签名 URL。
     *
     * <p>非本形态（历史裸路径 / 空）保持原值；换发失败也保持原值——
     * 头像挂了不该让整个接口 500。
     */
    public void fillAvatarUrl(cn.jfspeed.system.domain.vo.SysUserVO vo) {
        if (vo == null) {
            return;
        }
        Long fileId = FileRefs.parseId(vo.getAvatar());
        if (fileId == null) {
            return;
        }
        String url = signAttachment(fileId);
        if (url != null) {
            vo.setAvatar(url);
        }
    }

    /**
     * 批量头像换发（避免列表页 N+1 查询）。
     */
    public void fillAvatarUrls(List<cn.jfspeed.system.domain.vo.SysUserVO> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        List<Long> fileIds = voList.stream()
                .map(vo -> FileRefs.parseId(vo.getAvatar()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (fileIds.isEmpty()) {
            return;
        }
        Map<Long, String> signed = signAttachments(fileIds);
        if (signed.isEmpty()) {
            return;
        }
        for (cn.jfspeed.system.domain.vo.SysUserVO vo : voList) {
            Long fileId = FileRefs.parseId(vo.getAvatar());
            if (fileId == null) {
                continue;
            }
            String url = signed.get(fileId);
            if (url != null) {
                vo.setAvatar(url);
            }
        }
    }

    /** 签名有效期（秒） */
    public long ttlSeconds() {
        return signer.ttlSeconds();
    }

    private String sign(SysFile file) {
        long tenantId = TenantIds.normalize(file.getTenantId());
        String ticket = signer.sign(file.getId(), tenantId);
        return VIEW_PATH_PREFIX + file.getId() + "?ticket=" + ticket;
    }

    /** 从签名 URL 反解文件 ID（测试与运维排查用） */
    public static Long parseIdFromViewUrl(String url) {
        if (StrUtil.isBlank(url) || !url.startsWith(VIEW_PATH_PREFIX)) {
            return null;
        }
        String rest = url.substring(VIEW_PATH_PREFIX.length());
        int idx = rest.indexOf('?');
        String idPart = idx >= 0 ? rest.substring(0, idx) : rest;
        try {
            return Long.valueOf(idPart);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
