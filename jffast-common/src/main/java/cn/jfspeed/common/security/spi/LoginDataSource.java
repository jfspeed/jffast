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
package cn.jfspeed.common.security.spi;

import java.io.Serializable;
import java.util.List;

/**
 * 登录鉴权数据源扩展点：common 层鉴权逻辑所需的菜单/权限/用户数据由 biz 层提供。
 * <p>
 * jffast-common 不能编译期依赖 system 模块，历史上 {@code LoginUtil} 用
 * {@code Class.forName + invoke} 反射跨层调 system 服务，方法签名漂移只能运行期暴露。
 * 引入本 SPI 后：system 模块提供实现（编译期直调三个服务），common 只见接口，
 * 签名漂移直接编译红。
 * <p>
 * <b>调用约定</b>：与 {@link LoginPolicyProvider} 同口径——{@code ObjectProvider#getIfAvailable()}
 * 可选获取实现，未提供实现（纯 common 栈未装 system 模块）时调用方按
 * 「warn + 返回空」降级，行为与历史上的 {@code ClassNotFoundException} 分支等价。
 * <p>
 * 装了 system 模块的应用（boot / pro / cloud-system）经组件扫描自动获得实现，无需配置。
 *
 * @author jffast
 */
public interface LoginDataSource {

    /**
     * 角色绑定的全部菜单 ID（含按钮）。
     * <p>对应 SysRoleMenuService.listAllMenuIdList(Long)，内部带 TTL 缓存。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 列表（可能为空，不为 null）
     */
    List<Long> listAllMenuIdList(Long roleId);

    /**
     * 超管权限全集。
     * <p>对应 SysMenuService.listAllBtnPerm()，内部带 TTL 缓存，菜单变更时失效。
     *
     * @return 权限标识列表（可能为空，不为 null）
     */
    List<String> listAllBtnPerm();

    /**
     * 按角色查按钮权限集。
     * <p>对应 SysRoleMenuService.listAllBtnPerm(Long)，内部带 TTL 缓存。
     *
     * @param roleId 角色 ID
     * @return 权限标识列表（可能为空，不为 null）
     */
    List<String> listAllBtnPerm(Long roleId);

    /**
     * 按 ID 取最新的用户实体（MyBatis-Plus getById 语义）。
     * <p>返回 {@code Object} 而非具体类型：common 不反向依赖 system 实体，
     * 调用方对返回对象按会话内既有惯例（反射访问器 + 清空凭据）处理。
     *
     * @param id 用户 ID
     * @return 用户实体，查无此人返回 null
     */
    Object getFreshUser(Serializable id);
}
