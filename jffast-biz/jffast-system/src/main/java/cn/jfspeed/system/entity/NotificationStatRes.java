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

import java.util.List;

import cn.jfspeed.common.core.util.page.PageRes;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * @author jffast
 */
@Getter
@Setter
@Schema(title = "通知发送统计响应")
public class NotificationStatRes extends PageRes<NotificationStatVO> {

    @Schema(title = "已读数")
    private Long readCount;

    @Schema(title = "未读数")
    private Long unreadCount;

    public NotificationStatRes(Long total, Long pages, List<NotificationStatVO> list, Long readCount, Long unreadCount) {
        super(total, pages, list);
        this.readCount = readCount;
        this.unreadCount = unreadCount;
    }
}