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
import cn.jfspeed.system.entity.SysRoleDept;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 角色部门关联 Mapper 接口（废弃）
 *
 * @author jffast
 */
@Mapper
public interface SysRoleDeptMapper extends BaseMapper<SysRoleDept> {
    int insertBatch(List<SysRoleDept> list);
}
