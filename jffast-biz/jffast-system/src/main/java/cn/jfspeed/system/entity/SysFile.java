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
 * 文件记录（上传由 jffast-starter-file 自动登记）
 *
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
public class SysFile extends BaseEntity {

    /**
     * 归属租户（0 = 平台/单租户，与 {@code sys_role} 同口径）。
     *
     * <p>显式映射：{@code BaseEntity.tenantId} 是 {@code @TableField(exist = false)}，
     * 基座默认不映射该列——文件记录要按租户显式过滤，必须在本实体上重新声明。
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 原始文件名
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 存储定位键（内部用，**禁止对外直出**）
     */
    @TableField("url")
    private String url;

    /**
     * 文件大小（字节）
     */
    @TableField("size")
    private Long size;

    /**
     * MIME 类型
     */
    @TableField("mime")
    private String mime;

    /**
     * 业务类型（上传前缀）
     */
    @TableField("biz_type")
    private String bizType;

    /**
     * 存储类型（local/minio/aliyun-oss/tencent-cos）
     */
    @TableField("storage_type")
    private String storageType;
}
