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
package cn.jfspeed.security.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.jfspeed.common.security.util.LoginUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jffast
 */
public class SaTokenStpInterfaceImpl implements StpInterface {

    public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> permList = LoginUtil.getPermList();
        return permList != null ? permList : new ArrayList<>();
    }

    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> roleCodeList = LoginUtil.getRoleCodeList();
        return roleCodeList != null ? roleCodeList : new ArrayList<>();
    }
}
