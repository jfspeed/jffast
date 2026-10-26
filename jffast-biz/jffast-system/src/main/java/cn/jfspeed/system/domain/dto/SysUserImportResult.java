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
package cn.jfspeed.system.domain.dto;

import java.util.List;

import lombok.Data;

/**
 * 用户导入结果：逐行校验，坏行跳过不阻断整体导入
 *
 * @author jffast
 */
@Data
public class SysUserImportResult {

    private int total;

    private int successCount;

    private int failureCount;

    /**
     * 失败明细，格式「第N行：原因」（N 含表头行）
     */
    private List<String> failures;
}
