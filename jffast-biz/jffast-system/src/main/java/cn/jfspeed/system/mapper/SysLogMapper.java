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
import cn.jfspeed.system.entity.SysLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author jffast
 */
@Mapper
public interface SysLogMapper extends BaseMapper<SysLog> {

    @Select("SELECT DISTINCT title FROM sys_log WHERE title IS NOT NULL AND title != '' ORDER BY title")
    List<String> selectTitleList();

    /**
     * 按天统计操作日志（总数 / 成功数），一次查询覆盖整个时间范围。
     */
    @Select("""
            SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d,
                   COUNT(*) AS total,
                   SUM(CASE WHEN state = '成功' THEN 1 ELSE 0 END) AS succ
            FROM sys_log
            WHERE create_time >= #{start}
            GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
            ORDER BY d
            """)
    List<Map<String, Object>> selectDailyTrend(@Param("start") LocalDateTime start);
}
