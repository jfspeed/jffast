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

import java.time.LocalDateTime;

import cn.jfspeed.common.core.annotation.SensitiveMask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author jffast
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "通知发送统计")
public class NotificationStatVO {

    @Schema(title = "记录ID")
    private Long recordId;

    @Schema(title = "用户ID")
    private Long userId;

    @Schema(title = "用户名")
    private String userName;

    @SensitiveMask(type = SensitiveMask.MaskType.MIDDLE_MASK, prefixKeep = 1, suffixKeep = 1)
    @Schema(title = "真实姓名")
    private String realName;

    @Schema(title = "是否已读(0-未读,1-已读)")
    private Integer isRead;

    @Schema(title = "阅读时间")
    private LocalDateTime readTime;
}