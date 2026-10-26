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
import cn.jfspeed.system.entity.SysDept;
import io.lettuce.core.dynamic.annotation.Param;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 部门 Mapper 接口
 *
 * @author jffast
 */
@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    List<SysDept> list(Map<String,Object> map);

    List<SysDept> childList(@Param("parentId") Long parentId);

    /**
     * 部门/用户树节点：只取 id / parent_id / dept_name，避免拉取部门全字段。
     */
    List<SysDept> selectTreeNodes();
}
