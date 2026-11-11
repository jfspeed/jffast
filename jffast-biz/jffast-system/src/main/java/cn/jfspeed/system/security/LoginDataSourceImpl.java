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
package cn.jfspeed.system.security;

import cn.jfspeed.common.security.spi.LoginDataSource;
import cn.jfspeed.system.service.SysMenuService;
import cn.jfspeed.system.service.SysRoleMenuService;
import cn.jfspeed.system.service.SysUserService;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

/**
 * {@link LoginDataSource} SPI 的 system 模块实现。
 *
 * <p>取代 LoginUtil 历史上对 system 三服务的跨层反射调用：编译期直调，
 * 签名漂移直接编译红。装了 system 模块的应用（boot / pro / cloud-system）
 * 经组件扫描自动注册本实现，无需配置；纯 common 栈未装 system 时本类不在类路径，
 * LoginUtil 按「warn + 返回空」降级。
 *
 * @author jffast
 */
@Component
public class LoginDataSourceImpl implements LoginDataSource {

    private final SysRoleMenuService sysRoleMenuService;
    private final SysMenuService sysMenuService;
    private final SysUserService sysUserService;

    public LoginDataSourceImpl(SysRoleMenuService sysRoleMenuService,
                               SysMenuService sysMenuService,
                               SysUserService sysUserService) {
        this.sysRoleMenuService = sysRoleMenuService;
        this.sysMenuService = sysMenuService;
        this.sysUserService = sysUserService;
    }

    @Override
    public List<Long> listAllMenuIdList(Long roleId) {
        return sysRoleMenuService.listAllMenuIdList(roleId);
    }

    @Override
    public List<String> listAllBtnPerm() {
        // 超管权限全集：SysMenuService 内部带 TTL 缓存，菜单变更时失效
        return sysMenuService.listAllBtnPerm();
    }

    @Override
    public List<String> listAllBtnPerm(Long roleId) {
        // 按角色按钮权限：SysRoleMenuService 内部带 TTL 缓存
        return sysRoleMenuService.listAllBtnPerm(roleId);
    }

    @Override
    public Object getFreshUser(Serializable id) {
        return sysUserService.getById(id);
    }
}
