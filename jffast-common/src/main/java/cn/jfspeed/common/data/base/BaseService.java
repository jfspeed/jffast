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
package cn.jfspeed.common.data.base;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.jfspeed.common.core.base.BaseEmptyEntity;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.data.util.BaseUtil;
import cn.jfspeed.common.security.util.LoginUtil;

/**
 * @author jffast
 */
public abstract class BaseService<M extends BaseMapper<E>, E extends BaseEmptyEntity> extends ServiceImpl<M, E> {

    public LoginUser getLoginUser() {
        return LoginUtil.getLoginUser();
    }

    public Long loginId() {
        LoginUser user = getLoginUser();
        return user != null ? user.getId() : null;
    }

    public String queryApplyScope(String fieldName, String self, boolean isId) {
        LoginUser user = getLoginUser();
        if (user != null && user.getDataScope() != null) {
            return BaseUtil.setQueryApplyScope(user.getDataScope(), fieldName, self, isId);
        }
        return null;
    }
}
