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
package cn.jfspeed.system.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;

/**
 * 契约：探表 SQL 必须显式声明不被租户拦截器改写。
 *
 * <p>{@code SysSocialBindMapper#countSocialBindTable()} 查的是 {@code information_schema.tables}，
 * 而租户拦截器按**末段表名**判断是否豁免——它收到的是 {@code tables}，不是
 * {@code information_schema.tables}，于是不豁免，会给这条 SQL 追加 {@code AND tenant_id = 1}。
 * {@code information_schema.tables} 没有 {@code tenant_id} 列，数据库报
 * {@code Unknown column 'tenant_id'}，异常在 {@code deleteUser} 的事务里未被吞 ⇒ 删号整体 500。
 *
 * <p>这条断言在**没有** {@code @InterceptorIgnore} 的旧实现上必失败（旧实现只有一句裸
 * {@code @Select}），因此它就是本缺陷的回归护栏。
 *
 * @author jffast
 */
class SysSocialBindMapperTenantIgnoreContractTest {

    private static final String MS_ID =
            SysSocialBindMapper.class.getName() + ".countSocialBindTable";

    @BeforeAll
    static void registerMapper() {
        // 让 MyBatis-Plus 走一遍真实的 Mapper 解析：注解就是在这一步被登记进拦截器策略表的。
        new MybatisConfiguration().addMapper(SysSocialBindMapper.class);
    }

    @Test
    @DisplayName("探表方法必须带 @InterceptorIgnore 且租户拦截器对它放行")
    void countSocialBindTableIsImmuneToTenantInterceptor() throws Exception {
        Method method = SysSocialBindMapper.class.getMethod("countSocialBindTable");

        InterceptorIgnore ignore = method.getAnnotation(InterceptorIgnore.class);
        assertNotNull(ignore,
                "countSocialBindTable() 必须带 @InterceptorIgnore：租户拦截器按末段表名判断豁免，"
                        + "information_schema.tables 会被追加 tenant_id 条件并报 Unknown column");
        assertTrue(ignores(ignore.tenantLine()),
                "@InterceptorIgnore#tenantLine 必须表达「忽略」，实际值：" + ignore.tenantLine());
        assertTrue(ignores(ignore.dataPermission()),
                "@InterceptorIgnore#dataPermission 必须表达「忽略」，实际值：" + ignore.dataPermission());

        // 走 MyBatis-Plus 自己的判定，而不是只比对注解字面值：拦截器运行时读的就是这张策略表。
        assertTrue(InterceptorIgnoreHelper.willIgnoreTenantLine(MS_ID),
                "MyBatis-Plus 必须把该语句登记为「忽略租户改写」，否则拦截器照样会追加 tenant_id 条件");
        assertTrue(InterceptorIgnoreHelper.willIgnoreDataPermission(MS_ID),
                "MyBatis-Plus 必须把该语句登记为「忽略数据范围改写」");
    }

    /**
     * 与 MyBatis-Plus {@code InterceptorIgnoreHelper#getBoolean} 同款判定：
     * {@code 1 / true / on} = 忽略，{@code 0 / false / off} = 不忽略，其余取值 MP 会直接抛异常。
     */
    private static boolean ignores(String value) {
        return "1".equals(value) || "true".equals(value) || "on".equals(value);
    }
}
