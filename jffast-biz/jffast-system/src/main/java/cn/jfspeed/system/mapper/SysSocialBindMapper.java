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

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
