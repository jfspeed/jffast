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
package cn.jfspeed.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.jfspeed.common.data.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job")
public class SysJob extends BaseEntity {

    @TableField("job_name")
    private String jobName;

    @TableField("job_group")
    private String jobGroup;

    @TableField("invoke_target")
    private String invokeTarget;

    @TableField("cron_expression")
    private String cronExpression;

    @TableField("misfire_policy")
    private String misfirePolicy;

    @TableField("concurrent")
    private Integer concurrent;

    @TableField("status")
    private Integer status;

    /**
     * **执行该任务的服务名**（`spring.application.name`）。
     * <p>注解（`@JfFastJob`）登记的任务由声明它的服务自动填本服务名；
     * 为空表示"无归属"—— 由 `jffast.job.scheduler.default-scope=true` 的默认调度者执行
     * （单体即唯一进程；多服务部署时通常是系统服务）。
     */
    @TableField("service_name")
    private String serviceName;

    @TableField("remark")
    private String remark;
}
