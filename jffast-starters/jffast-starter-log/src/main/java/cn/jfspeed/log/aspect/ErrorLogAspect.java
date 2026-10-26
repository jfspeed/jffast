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
package cn.jfspeed.log.aspect;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.jfspeed.common.core.util.IdUtils;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.common.web.util.ClientIpResolver;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.util.LoginUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jffast
 */
@Slf4j
@Aspect
@Order(1)
public class ErrorLogAspect {

    private static final int MAX_LENGTH = 500;

    /**
     * 真实客户端 IP 的统一解析入口，理由同 {@link LogAspect} 的同名字段：
     * {@code X-Forwarded-For} 客户端可伪造，不能直接作为错误日志的来源 IP。
     * <p>
     * 允许传 null（直接 new 的用法），此时退化为「只认 TCP 对端地址」。
     */
    private final ClientIpResolver clientIpResolver;

    public ErrorLogAspect() {
        this(null);
    }

    @Autowired
    public ErrorLogAspect(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver == null ? new ClientIpResolver(List.of()) : clientIpResolver;
    }

    @Around("execution(* cn.jfspeed..controller.*.*(..))")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        Object res = null;
        Throwable error = null;
        try {
            res = joinPoint.proceed();
            return res;
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            if (error != null) {
                try {
                    saveErrorLog(joinPoint, res, error, System.currentTimeMillis() - startTime);
                } catch (Exception e) {
                    log.error("记录错误日志失败", e);
                }
            }
        }
    }

    private void saveErrorLog(ProceedingJoinPoint joinPoint, Object res, Throwable error, long duration) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }

        HttpServletRequest request = attributes.getRequest();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();
        String title = className + "." + methodName;

        LoginUser user = null;
        try {
            user = LoginUtil.getLoginUser();
        } catch (Exception e) {
            log.error("获取登录用户信息失败", e.getMessage());
        }
        String userName = user != null ? user.getUserName() : "unknown";

        String inputParam = buildInputParam(request, joinPoint);
        String outputParam = buildOutputParam(res, error);
        String errorMsg = error.getMessage();

        StringBuilder info = new StringBuilder();
        info.append("用户").append(userName).append("调用").append(className).append(".").append(methodName).append("失败");
        if (StrUtil.isNotBlank(errorMsg)) {
            info.append("：").append(truncate(errorMsg, MAX_LENGTH));
        }
        if (StrUtil.isNotBlank(inputParam)) {
            info.append(" | 入参：").append(inputParam);
        }
        if (StrUtil.isNotBlank(outputParam)) {
            info.append(" | 出参：").append(outputParam);
        }

        log.error("调用接口异常：ip={}, title={}, method={}, user={}, state=系统异常, info={}",
                clientIpResolver.resolve(request), title, methodName, userName, LogAspect.maskSensitiveInfo(info.toString()));
    }

    private String buildInputParam(HttpServletRequest request, ProceedingJoinPoint joinPoint) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method)) {
            return buildGetInputParam(request);
        }
        return buildPostInputParam(joinPoint);
    }

    private String buildGetInputParam(HttpServletRequest request) {
        Map<String, Object> paramMap = new LinkedHashMap<>();
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            String[] values = request.getParameterValues(name);
            if (values != null && values.length == 1) {
                paramMap.put(name, values[0]);
            } else if (values != null) {
                paramMap.put(name, values);
            }
        }
        if (paramMap.isEmpty()) {
            return "";
        }
        return truncate(JSONUtil.toJsonStr(paramMap), MAX_LENGTH);
    }

    private String buildPostInputParam(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "";
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Map<String, Object> paramMap = new LinkedHashMap<>();

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            String name = (paramNames != null && i < paramNames.length) ? paramNames[i] : "arg" + i;

            if (arg instanceof HttpServletRequest) {
                continue;
            } else if (arg instanceof MultipartFile) {
                paramMap.put(name, "[file:" + ((MultipartFile) arg).getOriginalFilename() + "]");
            } else if (arg instanceof MultipartFile[]) {
                paramMap.put(name, "[files:" + ((MultipartFile[]) arg).length + "个文件]");
            } else {
                paramMap.put(name, arg);
            }
        }

        if (paramMap.isEmpty()) {
            return "";
        }

        try {
            return truncate(JSONUtil.toJsonStr(paramMap), MAX_LENGTH);
        } catch (Exception e) {
            return truncate(paramMap.toString(), MAX_LENGTH);
        }
    }

    private String buildOutputParam(Object res, Throwable error) {
        if (res != null) {
            try {
                return truncate(JSONUtil.toJsonStr(res), MAX_LENGTH);
            } catch (Exception e) {
                return truncate(res.getClass().getSimpleName(), MAX_LENGTH);
            }
        }
        if (error == null) {
            return "";
        }
        R<Object> errorRes = buildErrorResponse(error);
        try {
            return truncate(JSONUtil.toJsonStr(errorRes), MAX_LENGTH);
        } catch (Exception e) {
            return truncate("{\"code\":500,\"message\":\"" + error.getClass().getSimpleName() + "\"}", MAX_LENGTH);
        }
    }

    private R<Object> buildErrorResponse(Throwable error) {
        if (error instanceof BusinessException) {
            BusinessException be = (BusinessException) error;
            return R.error(be.getCode(), be.getMessage());
        }
        if (error instanceof NullPointerException) {
            return R.error("空指针异常");
        }
        if (error instanceof SQLException) {
            return R.error("SQL异常");
        }
        return R.error(error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
    }

    private String truncate(String str, int maxLen) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLen) {
            return str;
        }
        return str.substring(0, maxLen) + "...";
    }
}
