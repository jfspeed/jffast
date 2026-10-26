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

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author jffast
 */
@Data
public class SaveLocalRequest {
    @NotBlank(message = "表名不能为空")
    private String tableName;
    private String packageName;
    private String author;
    private Boolean overwrite;
    private Boolean includeDto;
    private List<String> types;
    private String genType;
    private String parentField;
    private String nameField;
    private String childTableName;
    private String childForeignKey;
    private String childPackageName;
    private String moduleName;
}
