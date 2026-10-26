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
package cn.jfspeed.web.config;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import cn.hutool.json.JSONUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.core.util.IdUtils;
import cn.jfspeed.common.core.util.SensitiveMaskUtil;
import cn.jfspeed.common.security.util.LoginUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;


/**
 * @author jffast
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionConfig {

    /**
     * 取当前请求 URI，无请求上下文时返回占位符。
     *
     * <p>{@link LoginUtil#getRequest()} 在无请求上下文（异步线程、非 servlet 线程、
     * 容器级错误分发）时返回 null，此前各 handler 直接 {@code .getRequestURI()}，
     * 异常处理器自己会抛 NPE，把原始异常吞掉、响应退化成 Tomcat 默认错误页。
     */
    private static String safeUri() {
        HttpServletRequest request = LoginUtil.getRequest();
        return request == null ? "-" : request.getRequestURI();
    }

    @ExceptionHandler(value = BusinessException.class)
    public R processException(BusinessException e) {
        log.error("自定义异常，uri:{}==>{}", safeUri(),e.getLocalizedMessage());
        return R.error(e.getCode(), e.getMessage(), IdUtils.snowflakeShort());
    }

    @ResponseBody
    @ExceptionHandler(BindException.class)
    public R bindExceptionHandler(BindException ex) {
        StringBuilder sb = new StringBuilder();
        BindingResult bindingResult = ex.getBindingResult();
        if (bindingResult.hasErrors()) {
            for (int i = 0; i < bindingResult.getAllErrors().size(); i++) {
                ObjectError error = bindingResult.getAllErrors().get(i);
                sb.append((i == 0 ? "" : "\n") + error.getDefaultMessage());
            }
        }
        return R.error(400, sb.toString(), IdUtils.snowflakeShort());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseBody
    public R handler(ConstraintViolationException ex) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (ConstraintViolation violation : ex.getConstraintViolations()) {
            sb.append((++i == 1 ? "" : "\n") + violation.getMessage());
        }
        return R.error(400, sb.toString(), IdUtils.snowflakeShort());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public R methodArgumentNotValidHandler(MethodArgumentNotValidException ex) {
        StringBuilder sb = new StringBuilder();
        BindingResult bindingResult = ex.getBindingResult();
        if (bindingResult.hasErrors()) {
            for (int i = 0; i < bindingResult.getAllErrors().size(); i++) {
                ObjectError error = bindingResult.getAllErrors().get(i);
                sb.append((i == 0 ? "" : "\n") + error.getDefaultMessage());
            }
        }
        return R.error(400, sb.toString(), IdUtils.snowflakeShort());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R httpReqMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.error("请求方式不支持，uri:{}==>{}", safeUri(),e.getLocalizedMessage());
        return R.error(400, "请求方式不支持", IdUtils.snowflakeShort());
    }

    // 说明：Sa-Token 的两个处理器（NotLoginException / NotPermissionException）已移到
    // SaTokenExceptionConfig，并用 @ConditionalOnClass 守卫。原因见那个类的 javadoc：
    // 本 starter 的 sa-token-core 是 provided，若应用没引 Sa-Token，本类因方法签名引用
    // Sa-Token 类型而在类自省时抛 NoClassDefFoundError，会导致整个应用起不来。

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R missingParameterException(MissingServletRequestParameterException e) {
        log.error("缺少请求参数, uri:{} ==> 参数：{} 类型:{}",
                safeUri(),
                e.getParameterName(),
                e.getParameterType());
        String message = String.format("请求缺少必要的参数 [%s]，请检查请求参数是否完整", e.getParameterName());
        return R.error(400, message, IdUtils.snowflakeShort());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException.class)
    public R noResourceFoundException(NoResourceFoundException e) {
        log.warn("请求资源不存在, uri:{}==>{}", safeUri(), e.getLocalizedMessage());
        return R.error(404, "请求资源不存在", IdUtils.snowflakeShort());
    }

    /**
     * 数据完整性违例（唯一约束冲突 / NOT NULL 违例 / 长度超限等）。
     *
     * <p>此前没有专门处理，会落到兜底 handler 并统一报成「系统繁忙」——
     * 用户看到的报错与真实原因完全无关（典型：用户名重复、姓名为空）。
     * 这里给出可操作的提示，真实原因只落服务端日志，不回显给客户端。
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public R dataIntegrityViolation(DataIntegrityViolationException e) {
        log.error("数据完整性约束冲突, uri:{} ==> {}", safeUri(), e.getLocalizedMessage(), e);
        return R.error(400, "数据已存在或不符合约束（可能是用户名/手机号重复），请检查后重试",
                IdUtils.snowflakeShort());
    }

    /**
     * 兜底异常：返回 HTTP 500 + 业务 code=500。
     * 此前 @ResponseStatus(OK) 导致网关/监控按 HTTP 2xx 计数，5xx 告警恒为 0。
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public R exception(Exception e) {
        String msg = BaseConstant.UNKNOWN_EXCEPTION;
        if (e instanceof NullPointerException){
            msg = "空指针异常";
        }else if (e instanceof SQLException){
            msg = "SQL异常";
        }else if (e instanceof DataAccessException){
            // Spring 的 DataAccessException 体系（DataIntegrityViolationException 等）
            // **不继承** java.sql.SQLException，原先的 instanceof SQLException 判断对它恒不成立，
            // 于是所有约束违例都被误报成「系统繁忙」。
            msg = "数据访问异常";
        }
        R<Object> error = R.error(500, msg, IdUtils.snowflakeShort());
        String safeMessage = SensitiveMaskUtil.maskText(e.getMessage());
        String safeErrorJson = SensitiveMaskUtil.maskText(JSONUtil.toJsonStr(error));
        log.error("GlobalException: {} [uri: {}] [return：{}]", safeMessage, safeUri(),
                safeErrorJson, e);

        return error;
    }

    private String getRequestBody(HttpServletRequest request) {
        ContentCachingRequestWrapper requestWrapper =
                WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (requestWrapper == null) {
            return "";
        }
        return new String(requestWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

}
