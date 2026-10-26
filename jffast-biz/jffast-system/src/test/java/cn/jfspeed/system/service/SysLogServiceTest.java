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

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import cn.jfspeed.system.entity.SysLog;
import cn.jfspeed.system.mapper.SysLogMapper;
import cn.jfspeed.system.mapper.SysUserMapper;

/**
 * SysLogService 单元测试
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysLogServiceTest {

    @Mock
    private SysLogMapper baseMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private SysLogService service;

    @BeforeEach
    void injectBaseMapper() {
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new MybatisConfiguration(), ""), SysLog.class);
    }

    @Nested
    @DisplayName("cleanLogs：清理旧日志")
    class CleanLogs {

        @Test
        @DisplayName("应删除指定天数前的日志")
        void shouldDeleteOldLogs() {
            when(baseMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(5);

            service.cleanLogs(30);

            verify(baseMapper).delete(any(LambdaQueryWrapper.class));
        }
    }
}