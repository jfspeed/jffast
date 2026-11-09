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

import cn.jfspeed.common.core.context.TenantIds;
import cn.jfspeed.common.core.context.TenantScopeBridge;
import cn.jfspeed.common.file.FileRefs;
import cn.jfspeed.file.record.SysFileReader;
import cn.jfspeed.file.record.SysFileRecord;
import cn.jfspeed.file.service.FileUrlSigner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 示例图片的签名 URL 换发。
 *
 * <p>为什么 demo 模块自己换发，而不是复用 jffast-system 的 {@code FileUrlSignService}：
 * demo 模块**刻意不依赖 jffast-system**（Cloud 的 demo 微服务 classpath 上没有它），
 * 所以这里用 starter-file 里的 {@link SysFileReader}（JDBC 直读）+ {@link FileUrlSigner}
 * 做同样的事——读归属、校验租户、签发票据。
 *
 * <p>签名密钥必须与提供 {@code /sys/file/view/{id}} 的服务一致（同一把
 * {@code jffast.file.sign.secret}），否则验签必失败。
 *
 * @author jffast
 */
@Service
public class DemoImageSignService {

    private final SysFileReader sysFileReader;
    private final FileUrlSigner fileUrlSigner;
    private final ObjectProvider<TenantScopeBridge> tenantScopeBridge;

    public DemoImageSignService(SysFileReader sysFileReader,
                                FileUrlSigner fileUrlSigner,
                                ObjectProvider<TenantScopeBridge> tenantScopeBridge) {
        this.sysFileReader = sysFileReader;
        this.fileUrlSigner = fileUrlSigner;
        this.tenantScopeBridge = tenantScopeBridge;
    }

    /**
     * 把库里存的 {@code file:{id}} 换发成签名 URL。
     *
     * @return 签名 URL；非本形态 / 记录不存在 / 跨租户时返回 {@code null}
     */
    public String sign(String storedRef) {
        Long fileId = FileRefs.parseId(storedRef);
        if (fileId == null) {
            return null;
        }
        SysFileRecord record = sysFileReader.findById(fileId);
        if (record == null) {
            return null;
        }
        Long tenantId = currentTenantId();
        if (!tenantId.equals(TenantIds.normalize(record.tenantId()))) {
            return null;
        }
        return FileUrlSigner.VIEW_PATH_PREFIX + fileId + "?ticket=" + fileUrlSigner.sign(fileId, tenantId);
    }

    private Long currentTenantId() {
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
}
