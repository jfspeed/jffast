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
package cn.jfspeed.common.core.context;

import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.util.LoginUtil;

/**
 * 租户 ID 解析口径（与 {@code SysConfigService.PLATFORM_TENANT_ID} 同口径）。
 *
 * <p><b>0 = 平台 / 单租户</b>：Base 单租户部署下所有租户维度都落 0；
 * Pro 多租户下 0 表示平台级视角（可跨租户），非 0 表示某个具体租户。
 *
 * <p>解析顺序：
 * <ol>
 *   <li>有 {@link TenantScopeBridge}（Pro 的请求级租户上下文）⇒ 用它；它返回 {@code null}
 *       即「平台级视角」，落 0，**不再**回退登录用户（避免把平台视角误判成某个租户）；</li>
 *   <li>无租户桥（Base 单租户）⇒ 退回登录用户的 {@code tenantId}；</li>
 *   <li>皆空 ⇒ 0（平台/单租户）。</li>
 * </ol>
 *
 * @author jffast
 */
public final class TenantIds {

    /** 平台 / 单租户口径 */
    public static final long PLATFORM_TENANT_ID = 0L;

    private TenantIds() {
    }

    /** 是否平台级视角（null 或 0） */
    public static boolean isPlatform(Long tenantId) {
        return tenantId == null || tenantId == PLATFORM_TENANT_ID;
    }

    /** 归一：null ⇒ 0 */
    public static Long normalize(Long tenantId) {
        return tenantId == null ? PLATFORM_TENANT_ID : tenantId;
    }

    /**
     * 解析当前租户。
     *
     * @param bridge 租户上下文桥，可为 null（Base 单租户无实现）
     */
    public static Long resolve(TenantScopeBridge bridge) {
        if (bridge != null) {
            Long tenantId = bridge.currentTenantId();
            return normalize(tenantId);
        }
        try {
            LoginUser loginUser = LoginUtil.getLoginUser();
            if (loginUser != null && loginUser.getTenantId() != null) {
                return loginUser.getTenantId();
            }
        } catch (Exception e) {
            // 非登录上下文（脚本/异步任务）：落平台
        }
        return PLATFORM_TENANT_ID;
    }
}
