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

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.jfspeed.system.domain.dto.SysRoleMenuResponse;
import cn.jfspeed.system.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色菜单关联 Mapper 接口
 *
 * @author jffast
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {
    int insertBatch(List<SysRoleMenu> list);

    List<SysRoleMenu> listAll();

    /**
     * 如果是报表的组件那么perms保存的就是组件的id
     * 如果是菜单表的按钮权限就是菜单表的perms
     * @param roleId
     * @return
     */
    List<SysRoleMenuResponse> listAllBtnPerm(@Param("roleId") Long roleId);
}
