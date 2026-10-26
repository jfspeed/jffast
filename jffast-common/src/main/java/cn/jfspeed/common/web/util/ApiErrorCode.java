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
package cn.jfspeed.common.web.util;

import cn.jfspeed.common.core.enums.IErrorCode;

/**
 * 开放 API 加解密/签名错误码（1404/1405，段位见 docs/开发规范.md §11）。
 *
 * @author jffast
 */
public enum ApiErrorCode implements IErrorCode {
    RSA_DECRYPT_FAILED(1404, "data进行rsa解密失败"),
    SIGN_VERIFY_FAILED(1405, "参数签名校验失败");

    private final int code;
    private final String msg;

    ApiErrorCode(int code, String msg) {
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
