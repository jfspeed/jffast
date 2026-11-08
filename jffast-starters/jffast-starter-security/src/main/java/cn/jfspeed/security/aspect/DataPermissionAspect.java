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
package cn.jfspeed.security.aspect;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.mybatis.config.DataScopeContext;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.security.annotation.DataPermission;
import cn.jfspeed.security.util.DataPermissionSqlBuilder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 数据权限切面：把注解元数据对应的 SQL 片段放进 {@link DataScopeContext}。
 * <p>异步任务会预先在工作线程恢复提交时快照的片段；本切面在无登录态时必须保留该片段
 * （而不是无条件清空），退出时再恢复进入前的值，避免嵌套调用与 CallerRunsPolicy 互相污染。
 * <p>无登录态且没有「已解析」标记时一律拒绝（{@code 1 = 0}）：判断不出查看者身份，
 * 就不能让查询退化成全量。
 *
 * @author jffast
 */
@Slf4j
@Aspect
public class DataPermissionAspect {

    @Around("@annotation(dataPermission)")
    public Object around(ProceedingJoinPoint point, DataPermission dataPermission) throws Throwable {
        String previous = DataScopeContext.get();
        boolean previousResolved = DataScopeContext.isResolved();
        try {
            String sqlFragment = null;
            if (LoginUtil.getLoginUser() != null || !previousResolved) {
                sqlFragment = DataPermissionSqlBuilder.build(LoginUtil.getLoginUser(), dataPermission);
            }
            if (StrUtil.isNotBlank(sqlFragment)) {
                DataScopeContext.set(sqlFragment);
                publishRequestAttribute(sqlFragment);
            }
            return point.proceed();
        } finally {
            if (previous != null) {
                DataScopeContext.set(previous);
            } else {
                DataScopeContext.clear();
            }
            if (previousResolved) {
                DataScopeContext.markResolved();
            }
        }
    }

    static void publishRequestAttribute(String sqlFragment) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            servletRequestAttributes.getRequest().setAttribute(DataScopeContext.REQUEST_ATTRIBUTE, sqlFragment);
        }
    }
}
