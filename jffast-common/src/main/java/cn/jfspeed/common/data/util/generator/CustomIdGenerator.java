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
package cn.jfspeed.common.data.util.generator;

import cn.jfspeed.common.core.util.IdUtils;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author jffast
 * @date 2025/10/4 10:20
 * @desc 自定义ID生成*
 */
@Slf4j
@Component
public class CustomIdGenerator  implements IdentifierGenerator {

    @Override
    public synchronized Long nextId(Object entity) {
        return IdUtils.snowflakeShort();
    }

}
