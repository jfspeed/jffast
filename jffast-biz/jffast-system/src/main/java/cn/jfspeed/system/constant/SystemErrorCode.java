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
package cn.jfspeed.system.constant;

import cn.jfspeed.common.core.enums.IErrorCode;

/**
 * 系统模块业务错误码（段位 1000–1099，与 docs/开发规范.md §11 一致）。
 * 新增码请在此登记，禁止在业务代码中再写魔法数字。
 *
 * @author jffast
 */
public enum SystemErrorCode implements IErrorCode {

    PASSWORD_ERROR(1000, "密码错误"),
    USER_NOT_FOUND(1001, "用户不存在"),
    ACCOUNT_DISABLED(1002, "账号已停用"),
    /** 1003 登录失效见 common 的 {@code AuthErrorCode.LOGIN_EXPIRED}（LoginUtil 所在模块） */
    ACCOUNT_LOCKED(1004, "账号锁定中"),
    /** RSA 公钥未配置：前端无法加密密码，登录页应阻止提交而非降级明文 */
    RSA_PUBLIC_KEY_MISSING(1005, "RSA公钥未配置"),
    /** 初始密码未配置：拒绝「导入用户」等需要落初始口令的操作，避免落到公开常量上 */
    INIT_PASSWORD_NOT_CONFIGURED(1006, "初始密码未配置");

    private final int code;
    private final String msg;

    SystemErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }
}
