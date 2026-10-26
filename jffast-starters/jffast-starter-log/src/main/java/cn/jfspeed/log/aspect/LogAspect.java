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
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import cn.jfspeed.common.core.util.IdUtils;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.common.web.util.ClientIpResolver;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.util.LoginUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;

/**
 * @author jffast
 */
@Slf4j
@Aspect
public class LogAspect {

    /**
     * 真实客户端 IP 的统一解析入口。
     * <p>
     * 这里刻意不用 {@code cn.jfspeed.common.web.util.IPUtil#getIpAddress}：那个方法取
     * {@code X-Forwarded-For} 首跳，而该头客户端可任意伪造 —— 写进操作日志的来源 IP
     * 既能被攻击者用来隐身，也能被用来把锅甩给别人，审计定责就失效了。
     * <p>
     * 由 {@code JfFastLogAutoConfiguration} 注入；为兼容直接 new 的用法，允许传 null，
     * 此时退化为「只认 TCP 对端地址」（不信任任何转发头），同样不可伪造。
     */
    private final ClientIpResolver clientIpResolver;

    public LogAspect() {
        this(null);
    }

    @Autowired
    public LogAspect(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver == null ? new ClientIpResolver(List.of()) : clientIpResolver;
    }

    /**
     * 启动期反射自检缓存的句柄。若 SysLog / SysLogService 在类路径上但构造器或方法签名不匹配，
     * 应用启动时即失败（fail-fast），而非运行到打日志时才抛出 NoSuchMethodException 被吞掉。
     */
    private volatile Class<?> sysLogClass;
    private volatile Constructor<?> sysLogConstructor;
    private volatile Method setIdMethod;
    private volatile Method setCreateByMethod;
    private volatile Method setStateMethod;
    private volatile Method setInfoMethod;
    private volatile Class<?> sysLogServiceClass;
    private volatile Method saveMethod;
    private volatile boolean available;

    @PostConstruct
    public void init() {
        try {
            Class<?> logClass = Class.forName("cn.jfspeed.system.entity.SysLog");
            this.sysLogClass = logClass;
            this.sysLogConstructor = logClass.getConstructor(String.class, String.class, String.class);
            this.setIdMethod = logClass.getMethod("setId", Long.class);
            this.setCreateByMethod = logClass.getMethod("setCreateBy", Long.class);
            this.setStateMethod = logClass.getMethod("setState", String.class);
            this.setInfoMethod = logClass.getMethod("setInfo", String.class);

            Class<?> logServiceClass = Class.forName("cn.jfspeed.system.service.SysLogService");
            this.sysLogServiceClass = logServiceClass;
            this.saveMethod = logServiceClass.getMethod("save", Object.class);
            this.available = true;
            log.info("LogAspect 反射自检通过：SysLog 构造器/setter 与 SysLogService.save(Object) 签名匹配");
        } catch (ClassNotFoundException e) {
            log.debug("SysLog/SysLogService 不在类路径，操作日志保存将跳过");
            this.available = false;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "操作日志切面反射自检失败：SysLog 或 SysLogService 方法签名不匹配，请检查"
                            + " cn.jfspeed.system.entity.SysLog 与 cn.jfspeed.system.service.SysLogService。", e);
        }
    }

    @Pointcut("@annotation(cn.jfspeed.log.annotation.SLog)")
    public void log() {
    }

    @Around("log()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        SLog annotation = signature.getMethod().getAnnotation(SLog.class);

        Object res = null;
        Throwable error = null;
        try {
            res = joinPoint.proceed();
            return res;
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            try {
                saveLog(joinPoint, annotation, res, error, System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                log.error("记录操作日志失败", e);
            }
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, SLog annotation, Object res, Throwable error, long duration) {
        if (annotation == null) {
            return;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();

        LoginUser user = LoginUtil.getLoginUser();
        String userName = user != null ? user.getUserName() : "unknown";
        String state;
        String info;

        if (error != null) {
            state = "失败";
            R<Object> errorRes = buildErrorResponse(error);
            String errorOutput = "";
            try {
                errorOutput = errorRes != null ? JSONUtil.toJsonStr(errorRes) : "";
            } catch (Exception ex) {
                errorOutput = "{\"code\":500,\"message\":\"" + error.getClass().getSimpleName() + "\"}";
            }
            if (errorOutput.length() > 500) {
                errorOutput = errorOutput.substring(0, 500) + "...";
            }
            info = userName + annotation.title() + "失败：" + error.getMessage()
                    + (StrUtil.isNotBlank(errorOutput) ? " | 错误返回：" + errorOutput : "");
        } else {
            if (response != null && response.getStatus() != 200) {
                state = "失败";
                info = userName + annotation.title() + "失败";
            } else if (res == null) {
                state = "失败";
                info = userName + annotation.title() + "失败：null";
            } else if (res instanceof R) {
                R r = (R) res;
                if (r.getCode() != null && !r.getCode().equals(200)) {
                    state = "失败";
                    info = userName + annotation.title() + "失败：" + r.getMessage();
                } else {
                    state = "成功";
                    info = userName + annotation.title() + "成功";
                }
            } else {
                state = "成功";
                info = userName + annotation.title() + "成功";
            }
        }

        saveSysLogViaReflection(clientIpResolver.resolve(request), annotation.title(),
                ((MethodSignature) joinPoint.getSignature()).getMethod().getName(),
                userName, state, info);
    }

    private void saveSysLogViaReflection(String ip, String title, String methodName, String userName, String state, String info) {
        if (!available) {
            return;
        }
        try {
            Object sysLog = sysLogConstructor.newInstance(ip, title, methodName);
            setIdMethod.invoke(sysLog, IdUtils.snowflakeShort());
            Long userId = LoginUtil.getLoginUserId();
            setCreateByMethod.invoke(sysLog, userId);
            setStateMethod.invoke(sysLog, state);
            // 对日志信息进行脱敏处理，防止密码等敏感信息泄露
            String maskedInfo = maskSensitiveInfo(info);
            setInfoMethod.invoke(sysLog, maskedInfo);

            Object sysLogService = SpringUtil.getBean(sysLogServiceClass);
            saveMethod.invoke(sysLogService, sysLog);
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
        }
    }

    /**
     * 敏感信息脱敏
     * 对 password、pwd、phone、idCard 等字段值进行掩码处理。
     * 包内静态方法，供 {@link ErrorLogAspect} 复用，保证操作日志与错误日志脱敏口径一致。
     * <p>
     * 密钥/令牌类字段（clientSecret、secretKey、accessKey、access_token 等）同样必须掩码：
     * 管理端「新增/修改客户端」这类接口带 {@code @SLog}，请求体会整份写进操作日志表，
     * 不掩码等于把凭据明文落库并可被日志页读到。
     */
    static String maskSensitiveInfo(String info) {
        if (StrUtil.isBlank(info)) {
            return info;
        }
        // 脱敏 password=xxx、pwd=xxx、"password":"xxx" 等模式（长键名放前面，避免被短备选截胡）
        String masked = info
                .replaceAll("(?i)(client_secret|clientSecret|secret_key|secretKey|access_key|accessKey"
                        + "|access_token|refresh_token|token|password|pwd|passwd)"
                        + "[\"']?\\s*[:=]\\s*[\"']?[^\"'&,\\s}]+", "$1=******")
                // 脱敏手机号（11位数字，保留前3后4）
                .replaceAll("(?<=\\D)(1[3-9]\\d)\\d{4}(\\d{4})(?=\\D|$)", "$1****$2");
        return masked;
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
}
