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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import cn.jfspeed.system.entity.SysConfig;
import cn.jfspeed.system.mapper.SysConfigMapper;

/**
 * SysConfigService 单元测试
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysConfigServiceTest {

    @Mock
    private SysConfigMapper baseMapper;

    @InjectMocks
    private SysConfigService service;

    @BeforeEach
    void injectBaseMapper() {
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new MybatisConfiguration(), ""), SysConfig.class);
    }

    private SysConfig buildConfig(Long id, String key, String value) {
        SysConfig config = new SysConfig();
        config.setId(id);
        config.setConfigKey(key);
        config.setConfigValue(value);
        return config;
    }

    @Nested
    @DisplayName("getConfigValue：获取配置值")
    class GetConfigValue {

        @Test
        @DisplayName("空键名应返回 null")
        void shouldReturnNullForBlankKey() {
            assertNull(service.getConfigValue(""));
            assertNull(service.getConfigValue(null));
        }
    }

    @Nested
    @DisplayName("hasConfigKey：检查键名是否存在")
    class HasConfigKey {

        @Test
        @DisplayName("键名不存在应返回 false")
        void shouldReturnFalseWhenKeyNotExists() {
            when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            assertFalse(service.hasConfigKey("non-existent", null));
        }

        @Test
        @DisplayName("键名存在应返回 true")
        void shouldReturnTrueWhenKeyExists() {
            when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
            assertTrue(service.hasConfigKey("sys.name", null));
        }
    }

    @Nested
    @DisplayName("save：新增配置后刷新缓存")
    class Save {

        @Test
        @DisplayName("新增配置应调用 baseMapper.insert 并更新缓存")
        void shouldSaveAndRefreshCache() {
            SysConfig config = buildConfig(1L, "new.key", "new.value");
            when(baseMapper.insert(config)).thenReturn(1);

            boolean result = service.save(config);

            assertTrue(result);
            verify(baseMapper).insert(config);
        }
    }

    @Nested
    @DisplayName("removeById：删除配置后刷新缓存")
    class RemoveById {

        @Test
        @DisplayName("删除配置应调用 baseMapper.deleteById 并移除缓存")
        void shouldRemoveAndRefreshCache() {
            SysConfig config = buildConfig(1L, "delete.key", "value");
            when(baseMapper.selectById(1L)).thenReturn(config);
            when(baseMapper.deleteById(1L)).thenReturn(1);

            boolean result = service.removeById(1L);

            assertTrue(result);
            verify(baseMapper).deleteById(1L);
        }
    }
}