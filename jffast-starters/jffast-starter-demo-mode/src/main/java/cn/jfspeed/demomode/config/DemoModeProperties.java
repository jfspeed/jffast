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
package cn.jfspeed.demomode.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 演示模式配置（上线演示站专用）。
 *
 * <pre>
 * jffast:
 *   demo-mode:
 *     enabled: true   # 默认 false；仅演示 profile 打开
 *     exempt-statement-prefixes:
 *       - SysLogMapper      # 操作日志照写（否则连“谁看过演示”都无记录）
 *       - SysJobLogMapper   # 任务日志照写
 * </pre>
 *
 * @author jffast
 */
@ConfigurationProperties(prefix = "jffast.demo-mode")
public class DemoModeProperties {

    /** 是否启用演示模式，默认 false */
    private boolean enabled = false;

    /**
     * 豁免的 Mapper 语句前缀（按 Mapper 接口简单类名匹配）。
     * 豁免的写操作正常入库，其余 DML 一律短路返回 0。
     */
    private List<String> exemptStatementPrefixes = new ArrayList<>(List.of(
            "SysLogMapper",
            "SysJobLogMapper"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getExemptStatementPrefixes() {
        return exemptStatementPrefixes;
    }

    public void setExemptStatementPrefixes(List<String> exemptStatementPrefixes) {
        this.exemptStatementPrefixes = exemptStatementPrefixes;
    }
}
