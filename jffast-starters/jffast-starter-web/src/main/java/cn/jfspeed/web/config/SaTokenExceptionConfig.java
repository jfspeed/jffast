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

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.jfspeed.common.core.util.IdUtils;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.web.domain.dto.R;
import lombok.extern.slf4j.Slf4j;

/**
 * Sa-Token 异常的全局处理（登录失效 / 无权限）。
 *
 * <p><b>为什么单独一个类：</b>本模块的 pom 里 {@code sa-token-core} 是 {@code provided}
 * （即「用得到就自己引」），但 {@link GlobalExceptionConfig} 是组件扫描进来的 Bean，
 * 它的 {@code @ExceptionHandler} 方法签名引用了 {@link NotLoginException}。
 * 当某个应用引入了本 starter 却**没有**引入 Sa-Token 时（例如鉴权在上游完成、
 * 下游只从 Header 取用户上下文的服务），Spring 在创建该 Bean 时要做类自省，
 * 立刻抛 {@code NoClassDefFoundError: cn/dev33/satoken/exception/NotLoginException}，
 * **整个应用起不来**（编译期毫无征兆）。
 *
 * <p>这里用 {@link ConditionalOnClass} 把 Sa-Token 相关的处理器隔离出来：
 * 有 Sa-Token 就注册，没有就整体跳过——而 {@link GlobalExceptionConfig} 里
 * 与 Sa-Token 无关的通用异常处理（业务异常、参数校验、兜底 Exception）
 * 在任何情况下都照常生效。**不要**图省事给 {@code GlobalExceptionConfig} 整类加条件注解，
 * 那会让无 Sa-Token 的应用连基础异常处理一起丢掉。
 *
 * <p><b>为什么要 @Order(HIGHEST_PRECEDENCE)：</b>{@code ExceptionHandlerExceptionResolver}
 * 是按 advice 顺序逐个询问的，**第一个**「有能匹配该异常的方法」的 advice 就胜出，
 * 它不会跨 advice 比较匹配精确度。{@link GlobalExceptionConfig} 里有
 * {@code @ExceptionHandler(Exception.class)}，能匹配一切异常；两个 advice 都没有 @Order 时
 * 顺序不确定，一旦通用 advice 排在前面，NotLoginException 就会被它兜成
 * HTTP 200 + code 500，本类的 401 处理器永远轮不到。
 * 因此这里显式声明最高优先级，保证仅在两个类之间改变顺序，不影响其它 advice。
 *
 * @author jffast
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass({NotLoginException.class, NotPermissionException.class})
public class SaTokenExceptionConfig {

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(NotLoginException.class)
    public R notLoginException(NotLoginException e) {
        log.error("登录失效,uri:{}==>{}", LoginUtil.getRequest().getRequestURI(), e.getLocalizedMessage());
        return R.error(401, "登录失效，请重新登录", IdUtils.snowflakeShort());
    }

    @ExceptionHandler(NotPermissionException.class)
    public R notPermissionException(NotPermissionException e) {
        log.error("没有权限,uri:{}==>{}", LoginUtil.getRequest().getRequestURI(), e.getLocalizedMessage());
        return R.error(403, "没有该项操作的权限", IdUtils.snowflakeShort());
    }
}
