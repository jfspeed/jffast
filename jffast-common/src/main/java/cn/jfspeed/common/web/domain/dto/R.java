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
package cn.jfspeed.common.web.domain.dto;

import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @author jffast
 */
@Schema(title = "通用响应结果")
public class R<T> {
    @Schema(title = "响应码", description = "200表示成功，非200表示失败", example = "200")
    private Integer code;
    @Schema(title = "响应消息", description = "成功或失败的描述信息", example = "操作成功")
    private String message;

    @Schema(title = "响应数据", description = "业务数据对象")
    private T data;

    private R() {
    }

    public Integer getCode() {
        return this.code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return this.data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public boolean isSuccess() {
        return this.code != null && this.code.equals(FrameworkErrorCode.SUCCESS.getCode());
    }

    public static <T> R<T> successMsg(String msg) {
        R<T> result = new R<>();
        result.setCode(FrameworkErrorCode.SUCCESS.getCode());
        result.setMessage(msg);
        return result;
    }

    public static <T> R<T> success(T object) {
        R<T> result = new R<>();
        result.setCode(FrameworkErrorCode.SUCCESS.getCode());
        result.setMessage("ok");
        result.setData(object);
        return result;
    }

    public static <T> R<T> success(Integer code, String msg) {
        R<T> result = new R<>();
        result.setCode(code);
        result.setMessage(msg);
        return result;
    }

    public static <T> R<T> success() {
        R<T> result = new R<>();
        result.setCode(FrameworkErrorCode.SUCCESS.getCode());
        result.setMessage("ok");
        return result;
    }

    public static <T> R<T> error(Integer code, String msg) {
        R<T> result = new R<>();
        result.setCode(code);
        result.setMessage(msg);
        return result;
    }
    /**
     * 业务失败默认 400（与 {@code BusinessException(msg)} 一致）；500 只表示真正的服务端故障。
     * 需要 500 时显式传 {@code FrameworkErrorCode.SYSTEM_ERROR.getCode()}。
     */
    public static <T> R<T> error(String msg, T data) {
        return error(FrameworkErrorCode.PARAM_ERROR.getCode(), msg, data);
    }

    public static <T> R<T> error(Integer code, String msg, T data) {
        R<T> result = new R<>();
        result.setCode(code);
        result.setMessage(msg);
        result.setData(data);
        return result;
    }

    /** 业务失败默认 400；语义与 {@code BusinessException(msg)} 一致，500 仅用于服务端故障。 */
    public static <T> R<T> error(String msg) {
        return error(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
    }

    public static <T> R<T> status(boolean flag) {
        if (flag) {
            return R.success();
        } else {
            R<T> result = new R<>();
            result.setCode(FrameworkErrorCode.SYSTEM_ERROR.getCode());
            result.setMessage("执行失败");
            return result;
        }
    }
}
