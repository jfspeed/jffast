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
import cn.jfspeed.system.entity.SysUser;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 用户信息 Mapper 接口
 *
 * @author jffast
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    SysUser getById(Map<String,Object> params);

    IPage<SysUser> page(Page page, @Param("params") Map<String, Object> params);

    /**
     * 不分页用户列表（供 /all 下拉与导出复用），与 {@code page} 相同的联表结构，
     * 以便 {@code @DataPermission(deptField="d.id", userField="u.id")} 生效；
     * {@code params.maxRows} 非空时限制行数。
     */
    List<SysUser> list(@Param("params") Map<String, Object> params);
}
