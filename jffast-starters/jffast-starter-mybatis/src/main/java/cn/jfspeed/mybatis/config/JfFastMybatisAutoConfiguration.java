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
package cn.jfspeed.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

import cn.jfspeed.mybatis.handler.AutoFillHandler;
import cn.jfspeed.mybatis.interceptor.BaseFieldInterceptor;
import cn.jfspeed.mybatis.interceptor.DataScopeInnerInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * @author jffast
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class JfFastMybatisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            BaseFieldInterceptor baseFieldInterceptor,
            ObjectProvider<DataScopeInnerInterceptor> dataScopeInnerInterceptorProvider,
            @Value("${jffast.mybatis.page-max-limit:500}") long pageMaxLimit) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(baseFieldInterceptor);
        dataScopeInnerInterceptorProvider.ifAvailable(interceptor::addInnerInterceptor);
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // MyBatis-Plus 默认不限单页条数：任意列表接口传 pageSize=1000000 就能一次把全表读进内存。
        // 前端最大档位是 100，默认 500 给批量场景留余量；更大需求由部署方显式调高。
        paginationInnerInterceptor.setMaxLimit(pageMaxLimit);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public BaseFieldInterceptor baseFieldInterceptor() {
        return new BaseFieldInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    public AutoFillHandler autoFillHandler() {
        return new AutoFillHandler();
    }

    /**
     * 数据权限拦截器 —— **默认装配**，只有显式 {@code jffast.mybatis.data-scope.enabled=false} 才关掉。
     *
     * <p>{@code matchIfMissing} 原本取 {@code false}：漏配这一行不报错、不告警，表现是
     * {@code @DataPermission} 照常把 SQL 片段写进 {@link DataScopeContext}，却没有任何拦截器去消费它，
     * 于是"配了数据权限的用户仍能看到全表"。Base 只在单体 {@code application.yml} 里写了这行，
     * cloud 的三个服务都没写 —— 同一套代码切到 cloud 形态，数据权限就整体静默失效。
     * 安全开关的缺省值必须是"开着"，漏配最多是部署方显式关掉。
     */
    @Bean
    @ConditionalOnProperty(prefix = "jffast.mybatis.data-scope", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataScopeInnerInterceptor dataScopeInnerInterceptor() {
        return new DataScopeInnerInterceptor();
    }

}
