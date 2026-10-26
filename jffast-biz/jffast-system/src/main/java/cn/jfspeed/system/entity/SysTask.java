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
 * 异步任务（导入导出）
 *
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_task")
public class SysTask extends BaseEntity {

    /** 状态：待处理 */
    public static final int STATUS_PENDING = 0;
    /** 状态：进行中 */
    public static final int STATUS_RUNNING = 1;
    /** 状态：已完成 */
    public static final int STATUS_SUCCESS = 2;
    /** 状态：失败 */
    public static final int STATUS_FAIL = 3;

    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 任务类型（export导出 / import导入）
     */
    @TableField("task_type")
    private String taskType;

    /**
     * 业务类型（user用户）
     */
    @TableField("biz_type")
    private String bizType;

    /**
     * 任务标题
     */
    @TableField("title")
    private String title;

    /**
     * 状态（0待处理 1进行中 2已完成 3失败）
     */
    @TableField("status")
    private Integer status;

    /**
     * 进度（0-100）
     */
    @TableField("progress")
    private Integer progress;

    /**
     * 总行数
     */
    @TableField("total_rows")
    private Integer totalRows;

    /**
     * 成功行数
     */
    @TableField("success_rows")
    private Integer successRows;

    /**
     * 失败行数
     */
    @TableField("fail_rows")
    private Integer failRows;

    /**
     * 失败明细（多行文本）
     */
    @TableField("fail_detail")
    private String failDetail;

    /**
     * 结果文件本地路径
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 下载文件名
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 错误信息
     */
    @TableField("error_msg")
    private String errorMsg;
}
