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

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 社交账号绑定表（{@code sys_social_bind}）的删除操作。
 *
 * <p>本 Mapper 只承载「删号时清理绑定」这一条 SQL：绑定表的完整读写属于可选 Starter
 * （{@code jffast-starter-social}），而 {@code jffast-system} 不能依赖它——否则未引入该
 * Starter 的部署（如仅含 system 的服务）会在启动时因缺少 Bean 直接失败。
 *
 * @author jffast
 */
@Mapper
public interface SysSocialBindMapper {

    /**
     * 删除指定用户的全部社交账号绑定；表未导入时由调用方按开关跳过，不在此兜底
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * {@code sys_social_bind} 是否已导入（该表由**可选**脚本 {@code sql/social_bind.sql} 创建，
     * 不在 {@code sql/system_sql.sql} 里）。返回 0 = 表不存在。
     *
     * <p>为什么需要：{@code jffast.social.enabled=true} 只说明部署方想用社交功能，
     * 并不能证明他导了那张可选脚本。表不存在时无条件 DELETE 会让删号直接 500。
     *
     * <p>为什么必须显式 {@code @InterceptorIgnore(tenantLine = "true")}：本查询打在
     * {@code information_schema.tables} 上，而租户拦截器按**末段表名**判断是否豁免
     * （它收到的是 {@code tables}，不是 {@code information_schema.tables}），因而不会豁免，
     * 会给它追加 {@code AND tenant_id = 1} —— {@code information_schema.tables} 没有该列，
     * 数据库直接报 {@code Unknown column 'tenant_id'}，删号接口整体 500。
     * 探表语句本身与租户无关（问的是「这张表在不在」），必须声明不被改写。
     *
     * <p>{@code dataPermission = "true"} 同理：数据范围只针对业务数据行，元数据查询不受它约束。
     */
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Select("SELECT COUNT(1) FROM information_schema.tables "
            + "WHERE table_schema = DATABASE() AND table_name = 'sys_social_bind'")
    int countSocialBindTable();
}
