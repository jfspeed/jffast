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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 动态数据源配置（默认关闭，开启后接管 DataSource 装配）
 *
 * <pre>
 * jffast:
 *   datasource:
 *     dynamic:
 *       enabled: true          # 总开关，默认 false（关闭时行为与单数据源完全一致）
 *       primary: master        # 主数据源名（不标注 @DS 或未知名时使用）
 *       datasource:
 *         master:
 *           url: jdbc:mysql://127.0.0.1:3306/jffast
 *           driver-class-name: com.mysql.cj.jdbc.Driver
 *           username: root
 *           password: root
 *         slave1:
 *           url: jdbc:mysql://127.0.0.2:3306/jffast
 *           driver-class-name: com.mysql.cj.jdbc.Driver
 *           username: root
 *           password: root
 * </pre>
 *
 * @author jffast
 */
@Data
@ConfigurationProperties(prefix = "jffast.datasource.dynamic")
public class DynamicDataSourceProperties {

    /** 总开关，默认关闭（关闭时不注册路由数据源，原单数据源行为不变） */
    private boolean enabled = false;

    /** 主数据源名，默认 master */
    private String primary = "master";

    /** 数据源集合：key 为数据源名（@DS 引用名） */
    private Map<String, DataSource> datasource = new LinkedHashMap<>();

    /**
     * 单个数据源连接配置
     */
    @Data
    public static class DataSource {
        /** 连接 URL */
        private String url;
        /** 驱动类名 */
        private String driverClassName;
        /** 用户名 */
        private String username;
        /** 密码 */
        private String password;
    }
}
