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
package cn.jfspeed.common.core.exception;

import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import cn.jfspeed.common.core.enums.IErrorCode;
import cn.jfspeed.common.core.util.MessageUtil;

/**
 * @author jffast
 */
public class BusinessException extends RuntimeException {
    private Integer code;
    private String message;
    private String method;
    private String messageCode;
    private Object[] messageArgs;

    public Integer getCode() {
        return this.code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        if (this.messageCode != null) {
            return MessageUtil.getMessage(this.messageCode, this.messageArgs, this.message);
        }
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMethod() {
        return this.method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getMessageCode() {
        return this.messageCode;
    }

    public Object[] getMessageArgs() {
        return this.messageArgs;
    }

    private BusinessException() {
    }

    public BusinessException(String message) {
        this.code = 400;
        this.message = message;
    }

    public BusinessException(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public BusinessException(Integer code, String message, String method) {
        this.code = code;
        this.message = message;
        this.method = method;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    public BusinessException(IErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMsg();
    }

    /**
     * 使用国际化消息代码构造异常
     *
     * @param code HTTP 状态码
     * @param messageCode 消息代码
     * @param args 消息参数
     */
    public BusinessException(Integer code, String messageCode, Object... args) {
        this.code = code;
        this.messageCode = messageCode;
        this.messageArgs = args;
        this.message = messageCode;
    }

    /**
     * 使用国际化消息代码构造异常（默认 400 状态码）
     *
     * @param messageCode 消息代码
     * @param args 消息参数
     */
    public static BusinessException of(String messageCode, Object... args) {
        return new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), messageCode, args);
    }

    /**
     * 使用国际化消息代码构造异常
     *
     * @param code HTTP 状态码
     * @param messageCode 消息代码
     * @param args 消息参数
     */
    public static BusinessException of(Integer code, String messageCode, Object... args) {
        return new BusinessException(code, messageCode, args);
    }
}
