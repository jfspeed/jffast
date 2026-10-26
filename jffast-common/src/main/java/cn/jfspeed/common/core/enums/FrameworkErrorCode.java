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
 * @author jffast
 */
public enum FrameworkErrorCode implements IErrorCode {

    SUCCESS(200, "请求成功"),

    PARAM_ERROR(400, "参数错误（缺少、格式异常）"),
    /** 409 Conflict：请求与当前资源状态冲突（幂等拦截、唯一约束冲突等） */
    CONFLICT(409, "请求冲突"),
    SYSTEM_ERROR(500, "系统错误"),

    HTTP_REQUEST_FAILED(900000, "HTTP请求失败"),
    HTTP_400_BAD_REQUEST(900400, "Bad Request - 请求参数错误"),
    HTTP_401_UNAUTHORIZED(900401, "Unauthorized - 未授权"),
    HTTP_403_FORBIDDEN(900403, "Forbidden - 禁止访问"),
    HTTP_404_NOT_FOUND(900404, "Not Found - 资源不存在"),
    HTTP_405_METHOD_NOT_ALLOWED(900405, "Method Not Allowed - 方法不允许"),
    HTTP_408_REQUEST_TIMEOUT(900408, "Request Timeout - 请求超时"),
    HTTP_429_TOO_MANY_REQUESTS(900429, "Too Many Requests - 请求频率超限"),
    HTTP_500_INTERNAL_ERROR(900500, "Internal Server Error - 服务器内部错误"),
    HTTP_502_BAD_GATEWAY(900502, "Bad Gateway - 网关错误"),
    HTTP_503_SERVICE_UNAVAILABLE(900503, "Service Unavailable - 服务不可用"),
    HTTP_504_GATEWAY_TIMEOUT(900504, "Gateway Timeout - 网关超时"),
    HTTP_RESPONSE_NOT_JSON(900900, "响应不是JSON格式");

    private final int code;
    private final String msg;

    FrameworkErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public static FrameworkErrorCode getByHttpCode(int httpCode) {
        switch (httpCode) {
            case 400: return HTTP_400_BAD_REQUEST;
            case 401: return HTTP_401_UNAUTHORIZED;
            case 403: return HTTP_403_FORBIDDEN;
            case 404: return HTTP_404_NOT_FOUND;
            case 405: return HTTP_405_METHOD_NOT_ALLOWED;
            case 408: return HTTP_408_REQUEST_TIMEOUT;
            case 429: return HTTP_429_TOO_MANY_REQUESTS;
            case 500: return HTTP_500_INTERNAL_ERROR;
            case 502: return HTTP_502_BAD_GATEWAY;
            case 503: return HTTP_503_SERVICE_UNAVAILABLE;
            case 504: return HTTP_504_GATEWAY_TIMEOUT;
            default: return SYSTEM_ERROR;
        }
    }
}
