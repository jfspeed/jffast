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

    @Bean
    @ConditionalOnProperty(prefix = "jffast.mybatis.data-scope", name = "enabled", havingValue = "true", matchIfMissing = false)
    public DataScopeInnerInterceptor dataScopeInnerInterceptor() {
        return new DataScopeInnerInterceptor();
    }

}
