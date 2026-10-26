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
package cn.jfspeed.system.service;

import java.util.Map;
import java.util.Set;

import cn.hutool.core.util.StrUtil;

/**
 * 用户导入的部门解析上下文：在请求线程一次性固化导入者的可见部门，
 * 异步 worker 只读本对象，不得再查询登录态或数据权限（worker 无登录态，
 * 重新查询会退化为全量部门，等于越权按名称解析任意部门）。
 *
 * @author jffast
 */
public record SysUserImportContext(boolean superAdmin,
                                   Map<Long, String> visibleDeptNames,
                                   Set<String> ambiguousDeptNames,
                                   Map<String, Long> uniqueIdByName) {

    public SysUserImportContext {
        visibleDeptNames = visibleDeptNames == null ? Map.of() : Map.copyOf(visibleDeptNames);
        ambiguousDeptNames = ambiguousDeptNames == null ? Set.of() : Set.copyOf(ambiguousDeptNames);
        uniqueIdByName = uniqueIdByName == null ? Map.of() : Map.copyOf(uniqueIdByName);
    }

    /** 归属部门解析结果状态：失败原因与 §4.3 文案一一对应 */
    public enum DeptResolutionStatus {
        OK, REQUIRED, INVALID_ID, NOT_FOUND, AMBIGUOUS
    }

    public record DeptResolution(DeptResolutionStatus status, Long deptId, String message) {

        public boolean ok() {
            return status == DeptResolutionStatus.OK;
        }

        static DeptResolution success(Long deptId) {
            return new DeptResolution(DeptResolutionStatus.OK, deptId, null);
        }

        static DeptResolution failure(DeptResolutionStatus status, String message) {
            return new DeptResolution(status, null, message);
        }
    }

    /**
     * 解析导入行的「归属部门」文本：纯数字按部门 ID，否则按部门名称精确匹配（trim 后区分大小写）。
     * 超管留空合法（不设归属、不写绑定）；非超管留空即失败。
     */
    public DeptResolution resolveDeptId(String rawText) {
        String text = StrUtil.trimToNull(rawText);
        if (text == null) {
            return superAdmin
                    ? DeptResolution.success(null)
                    : DeptResolution.failure(DeptResolutionStatus.REQUIRED, "归属部门不能为空");
        }
        if (isAsciiDigits(text)) {
            long deptId;
            try {
                deptId = Long.parseLong(text);
            } catch (NumberFormatException e) {
                return DeptResolution.failure(DeptResolutionStatus.INVALID_ID, "归属部门ID格式不正确");
            }
            if (!visibleDeptNames.containsKey(deptId)) {
                return DeptResolution.failure(DeptResolutionStatus.NOT_FOUND, "部门不存在或无权使用该部门");
            }
            return DeptResolution.success(deptId);
        }
        if (ambiguousDeptNames.contains(text)) {
            return DeptResolution.failure(DeptResolutionStatus.AMBIGUOUS, "部门名称重复，请填写部门ID");
        }
        Long deptId = uniqueIdByName.get(text);
        if (deptId == null) {
            return DeptResolution.failure(DeptResolutionStatus.NOT_FOUND, "部门不存在或无权使用该部门");
        }
        return DeptResolution.success(deptId);
    }

    private static boolean isAsciiDigits(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }
}
