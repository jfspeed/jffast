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
package cn.jfspeed.mybatis.datasource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 动态数据源配置属性默认值单元测试（默认关闭，行为与单数据源一致）。
 *
 * @author jffast
 */
class DynamicDataSourcePropertiesTest {

    @Test
    @DisplayName("默认关闭、primary=master、数据源集合为空")
    void defaults() {
        DynamicDataSourceProperties props = new DynamicDataSourceProperties();

        assertFalse(props.isEnabled());
        assertEquals("master", props.getPrimary());
        assertTrue(props.getDatasource().isEmpty());
    }

    @Test
    @DisplayName("数据源条目可配置")
    void configuresEntries() {
        DynamicDataSourceProperties props = new DynamicDataSourceProperties();
        DynamicDataSourceProperties.DataSource ds = new DynamicDataSourceProperties.DataSource();
        ds.setUrl("jdbc:mysql://127.0.0.2:3306/jffast");
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUsername("root");
        ds.setPassword("root");
        props.getDatasource().put("slave1", ds);

        assertFalse(props.isEnabled());
        assertEquals(1, props.getDatasource().size());
        assertEquals("jdbc:mysql://127.0.0.2:3306/jffast", props.getDatasource().get("slave1").getUrl());
        assertEquals("root", props.getDatasource().get("slave1").getUsername());
    }
}
