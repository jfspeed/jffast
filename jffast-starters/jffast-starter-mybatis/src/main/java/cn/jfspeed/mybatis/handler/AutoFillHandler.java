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
package cn.jfspeed.mybatis.handler;

import cn.jfspeed.common.core.annotation.IgnoreBaseField;
import cn.jfspeed.common.core.context.AuditContext;
import cn.jfspeed.common.security.util.LoginUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * @author jffast
 */
@Slf4j
public class AutoFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        IgnoreBaseField ignore = getIgnoreAnnotation(metaObject);
        Long userId = getUserId();

        if (!isIgnored(ignore, "createBy") && metaObject.hasSetter("createBy")) {
            Object val = metaObject.getValue("createBy");
            if (val == null) {
                this.setFieldValByName("createBy", userId, metaObject);
            }
        }
        if (!isIgnored(ignore, "createTime") && metaObject.hasSetter("createTime")) {
            Object val = metaObject.getValue("createTime");
            if (val == null) {
                this.setFieldValByName("createTime", LocalDateTime.now(), metaObject);
            }
        }
        if (!isIgnored(ignore, "updateBy") && metaObject.hasSetter("updateBy")) {
            Object val = metaObject.getValue("updateBy");
            if (val == null) {
                this.setFieldValByName("updateBy", userId, metaObject);
            }
        }
        if (!isIgnored(ignore, "updateTime") && metaObject.hasSetter("updateTime")) {
            Object val = metaObject.getValue("updateTime");
            if (val == null) {
                this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        IgnoreBaseField ignore = getIgnoreAnnotation(metaObject);
        Long userId = getUserId();

        if (!isIgnored(ignore, "updateBy") && metaObject.hasSetter("updateBy")) {
            this.setFieldValByName("updateBy", userId, metaObject);
        }
        if (!isIgnored(ignore, "updateTime") && metaObject.hasSetter("updateTime")) {
            this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        }
    }

    private IgnoreBaseField getIgnoreAnnotation(MetaObject metaObject) {
        try {
            Object original = metaObject.getOriginalObject();
            if (original != null) {
                return original.getClass().getAnnotation(IgnoreBaseField.class);
            }
        } catch (Exception e) {
            log.debug("Failed to get @IgnoreBaseField annotation: {}", e.getMessage());
        }
        return null;
    }

    private boolean isIgnored(IgnoreBaseField ignore, String fieldName) {
        if (ignore == null) return false;
        return switch (fieldName) {
            case "createBy" -> ignore.createBy();
            case "createTime" -> ignore.createTime();
            case "updateBy" -> ignore.updateBy();
            case "updateTime" -> ignore.updateTime();
            default -> false;
        };
    }

    private Long getUserId() {
        // 异步任务线程没有登录态，任务包装器把操作者放进 AuditContext，优先级高于登录态
        Long auditUserId = AuditContext.getUserId();
        if (auditUserId != null) {
            return auditUserId;
        }
        try {
            Long id = LoginUtil.getLoginUserId();
            return id != null ? id : 0L;
        } catch (Throwable e) {
            // ⚠️ 必须捕获 Throwable：**没有 Sa-Token 的服务**里 LoginUtil.getLoginUserId() 抛的是
            //    NoClassDefFoundError（Error，不是 Exception），只 catch(Exception) 会漏过去，
            //    导致 insert/update 时自动填充用户信息把整个请求打挂（2026-09-24 评审发现）。
            return 0L;
        }
    }
}
