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
package cn.jfspeed.common.security.domain.dto;

import java.io.Serializable;
import java.util.Map;

import lombok.Data;

/**
 * @author jffast
 */
@Data
public class LoginUser implements Serializable {

    private Long id;
    private String userName;
    private String realName;
    private Long deptId;
    private String roleId;
    private String phone;
    private String email;
    private String nickName;
    private String avatar;
    private Long tenantId;
    private Map<String, Object> dataScope;
    private String isBusinessUser;
}
