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

/**
 * 异步任务上下文：提交线程在入队前捕获的「操作者 + 数据权限 SQL 快照」。
 * <p>
 * 工作线程没有登录态，{@code @DataPermission} 切面拿到的是空用户；若不在提交时快照，
 * 导出会退化为无数据权限的全量查询。操作者用于审计字段（create_by/update_by）回填。
 *
 * @param operatorId   提交任务的登录用户 ID，可为 null（无登录态时）
 * @param dataScopeSql 提交时按方法注解元数据算出的数据权限 SQL 片段，null 表示该任务不带数据权限
 * @author jffast
 */
public record SysTaskContext(Long operatorId, String dataScopeSql) {
}
