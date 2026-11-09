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
package cn.jfspeed.file.record;

/**
 * {@code sys_file} 记录的只读视图（JDBC 直读，不依赖 jffast-system 的实体与 Mapper）。
 *
 * <p>用途：让**没有 jffast-system 依赖**的服务（例如 demo 微服务）也能按 id 读取文件归属，
 * 从而给「随业务记录展示的附件」换发签名 URL。
 *
 * @param id        文件 ID
 * @param tenantId  归属租户（0 = 平台/单租户）
 * @param createBy  上传人
 * @param fileName  原始文件名
 * @param url       存储定位键（内部用，禁止对外直出）
 * @param size      文件大小（字节）
 * @param mime      MIME 类型
 */
public record SysFileRecord(Long id,
                            Long tenantId,
                            Long createBy,
                            String fileName,
                            String url,
                            Long size,
                            String mime) {
}
