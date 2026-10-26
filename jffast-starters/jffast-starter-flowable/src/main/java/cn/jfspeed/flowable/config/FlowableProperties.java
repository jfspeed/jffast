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
package cn.jfspeed.flowable.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Flowable 工作流引擎配置属性。
 * <p>
 * 配置前缀：{@code jffast.flow}
 *
 * @author jffast
 */
@ConfigurationProperties(prefix = "jffast.flow")
public class FlowableProperties {

    /**
     * 是否启用工作流引擎（关闭后整个 Starter 不注册）
     */
    private boolean enabled = true;

    /**
     * 流程引擎在 jffast 中的应用名（用于 Flowable 内部标识）
     */
    private String applicationName = "jffast-flow";

    /**
     * 数据库 schema 更新策略（true=启动时自动建/更新 ACT_* 表）
     */
    private boolean databaseSchemaUpdate = true;

    /**
     * 历史数据级别：none/activity/audit/full
     */
    private String historyLevel = "audit";

    /**
     * 是否启用异步执行器（流程中的异步任务、定时事件需要）
     */
    private boolean asyncExecutorActivate = true;

    /**
     * 是否在启动时部署 classpath:processes/*.bpmn20.xml（关闭则只能手动部署）
     */
    private boolean autoDeploy = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public boolean isDatabaseSchemaUpdate() {
        return databaseSchemaUpdate;
    }

    public void setDatabaseSchemaUpdate(boolean databaseSchemaUpdate) {
        this.databaseSchemaUpdate = databaseSchemaUpdate;
    }

    public String getHistoryLevel() {
        return historyLevel;
    }

    public void setHistoryLevel(String historyLevel) {
        this.historyLevel = historyLevel;
    }

    public boolean isAsyncExecutorActivate() {
        return asyncExecutorActivate;
    }

    public void setAsyncExecutorActivate(boolean asyncExecutorActivate) {
        this.asyncExecutorActivate = asyncExecutorActivate;
    }

    public boolean isAutoDeploy() {
        return autoDeploy;
    }

    public void setAutoDeploy(boolean autoDeploy) {
        this.autoDeploy = autoDeploy;
    }
}
