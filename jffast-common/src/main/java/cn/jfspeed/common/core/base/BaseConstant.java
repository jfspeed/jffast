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
package cn.jfspeed.common.core.base;


/**
 * @author jffast
 */
public interface BaseConstant {
    String ROLE_SUPER_ADMIN = "super_admin";
    String ROLE_ID_SUPER_ADMIN = "1";
    Long ROLE_ID_SUPER_ADMIN_LONG = 1L;
    String SUPER_ADMIN = "admin";
    String ROLE_ADMIN = "admin";
    String ROLE_COMMON = "common";
    String UNKNOWN_EXCEPTION = "系统异常，请联系管理员";
    String PARENT_ID_STR = "0";
    Long PARENT_ID = 0L;
    String PARENT_VIEW = "ParentView";

    /**
     * 初始密码兜底值：<b>空串</b>，语义即「未配置」。
     * <p>
     * 这里刻意不再提供任何真实口令。仓库里出现过的示例口令（README、SQL seed、
     * 配置默认值）一旦随仓库公开，就是全网已知凭据——任何按默认配置部署的实例
     * 都能被直接登录。未显式配置时，由各调用方用 {@code PasswordGenerator}
     * 随机生成强口令并回传，保证每个实例的口令互不相同且不可预测。
     *
     * @deprecated 仅为兼容旧引用保留。判断「是否已配置初始密码」请用
     *             {@code StrUtil.isBlank(...)}，切勿把本常量当作口令使用。
     */
    @Deprecated
    String INIT_PASSWORD = "";

    Integer DELETED_NO = 0;
    Integer DELETED_YES = 1;
    String TENANT_DEFAULT = "default";

    String CACHE_PREFIX = "jffast:";
    String CACHE_ROLE_PERM_LIST = CACHE_PREFIX + "roleBtnPermList:";
    String CACHE_ALL_MENU_ID_LIST = CACHE_PREFIX + "getAllMenuIdList:";
    String CACHE_VER_CODE = CACHE_PREFIX + "login:verCode:";
    String CACHE_ERROR_COUNT = CACHE_PREFIX + "login:errorCount:";
    String CACHE_CAPTCHA_CHARS = CACHE_PREFIX + "captcha:chars";
    String TOKEN_HEADER = "token";

    String DATA_SCOPE_DEPT_ID = "bmid";
    String DATA_SCOPE_DEPT_NAME = "bmmc";
    String DATA_SCOPE_DEPT_PARENT_ID = "bmjzbmid";
    String DATA_SCOPE_DEPT_PARENT_NAME = "bmJzbmmc";
    String DATA_SCOPE_SELF = "self";
    String DATA_SCOPE_CUSTOM_ID = "zdyid";
    String DATA_SCOPE_CUSTOM_NAME = "zdymc";
    /**
     * 受限用户无任何可用数据范围：SQL 片段固定为 {@code 1 = 0}（fail-closed）。
     */
    String DATA_SCOPE_NONE = "none";
}
