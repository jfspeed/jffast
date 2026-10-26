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
package cn.jfspeed.common.core.enums;

/**
 * 认证/会话相关错误码（框架级，段位 1000–1099 中与登录态相关的子集）。
 * <p>
 * 放在 common 而非 system：{@code LoginUtil} 位于 common，不能反向依赖 system 模块。
 * system 模块可继续使用 {@code SystemErrorCode}（密码/用户/锁定），码值不得与本枚举冲突。
 *
 * @author jffast
 */
public enum AuthErrorCode implements IErrorCode {

    LOGIN_EXPIRED(1003, "登录失效，请重新登录"),
    NOT_LOGIN(401, "请先登录"),
    /** 403 Forbidden：授权拒绝（无权操作该资源 / 目标超出数据范围）。与 401 同属鉴权语义 */
    FORBIDDEN(403, "无权访问该资源");

    private final int code;
    private final String msg;

    AuthErrorCode(int code, String msg) {
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
