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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.util.LoginUtil;
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

    /**
     * 回归：配置缓存键曾只有 configKey，A 读到 B 的配置（实测 B-TENANT-SECRET）。
     * 修复后键为 {@code <tenantScope>:<configKey>}，且写后只失效本租户桶。
     */
    @Nested
    @DisplayName("租户缓存隔离")
    class TenantScopedCache {

        private LoginUser loginUserOf(Long tenantId) {
            LoginUser user = new LoginUser();
            user.setTenantId(tenantId);
            return user;
        }

        @Test
        @DisplayName("A 写入后 B 读到自己的值，而不是 A 的缓存")
        void shouldNotLeakAcrossTenants() {
            String key = "unit.tenant.key." + System.nanoTime();
            when(baseMapper.insert(any(SysConfig.class))).thenReturn(1);
            SysConfig bRow = buildConfig(2L, key, "B-SECRET");
            when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bRow);

            try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(100L));
                assertTrue(service.save(buildConfig(1L, key, "A-SECRET")));

                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(101L));
                assertEquals("B-SECRET", service.getConfigValue(key));
            }
        }

        @Test
        @DisplayName("写操作只失效本租户缓存，其他租户的缓存保持可用")
        void shouldEvictOnlyOwnTenant() {
            String key = "unit.tenant.evict." + System.nanoTime();
            when(baseMapper.insert(any(SysConfig.class))).thenReturn(1);
            when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildConfig(2L, key, "B-1"));

            try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(101L));
                assertEquals("B-1", service.getConfigValue(key));

                // 之后数据库里的值变了；若 A 的写操作错误地失效了 B 的缓存，B 会读到 B-2
                when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildConfig(2L, key, "B-2"));
                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(100L));
                assertTrue(service.save(buildConfig(1L, key, "A-1")));

                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(101L));
                assertEquals("B-1", service.getConfigValue(key));
            }
        }

        @Test
        @DisplayName("无租户会话的写入不落任何租户桶")
        void shouldNotCacheWithoutTenantScope() {
            String key = "unit.tenant.none." + System.nanoTime();
            when(baseMapper.insert(any(SysConfig.class))).thenReturn(1);
            when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(buildConfig(2L, key, "A-DB"));

            try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
                login.when(LoginUtil::getLoginUser).thenReturn(null);
                assertTrue(service.save(buildConfig(1L, key, "PLATFORM-VALUE")));

                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(100L));
                assertEquals("A-DB", service.getConfigValue(key));
            }
        }
    }

    /**
     * 回归（2026-11-09）：上一版只把 tenantId 拼进缓存键，SQL 仍无租户条件，
     * 云版探针第 23 例「管理员跨租户读配置」依旧 {@code leak=True}。
     * Base 没有 {@code TenantLineInnerInterceptor}，所以每条查询都必须<b>自己</b>带条件。
     */
    @Nested
    @DisplayName("SQL 租户过滤（基座无拦截器，必须显式带条件）")
    class SqlTenantScope {

        private LoginUser loginUserOf(Long tenantId) {
            LoginUser user = new LoginUser();
            user.setTenantId(tenantId);
            return user;
        }

        @SuppressWarnings("unchecked")
        private LambdaQueryWrapper<SysConfig> captureQuery() {
            ArgumentCaptor<LambdaQueryWrapper<SysConfig>> captor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(baseMapper).selectOne(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("租户身份回源：条件为 tenant_id = 本租户")
        void shouldFilterByOwnTenantOnMiss() {
            when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            String key = "unit.sql.tenant." + System.nanoTime();

            try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(200L));
                assertNull(service.getConfigValue(key));
            }

            LambdaQueryWrapper<SysConfig> query = captureQuery();
            assertTrue(query.getSqlSegment().contains("tenant_id ="), query.getSqlSegment());
            assertTrue(query.getParamNameValuePairs().containsValue(200L));
            // 不能出现「平台行兜底」：那等于把 NULL 行集合开放给所有租户
            assertFalse(query.getSqlSegment().contains("IS NULL"), query.getSqlSegment());
        }

        @Test
        @DisplayName("平台身份回源：条件为 tenant_id IS NULL OR tenant_id = 0")
        void shouldFilterPlatformRowsForPlatformIdentity() {
            when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            String key = "unit.sql.platform." + System.nanoTime();

            try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(null));
                assertNull(service.getConfigValue(key));
            }

            String segment = captureQuery().getSqlSegment();
            assertTrue(segment.contains("tenant_id IS NULL"), segment);
            assertTrue(segment.contains("tenant_id ="), segment);
        }

        @Test
        @DisplayName("新增：租户身份落 session tenantId，不落 NULL")
        void shouldFillTenantOnCreate() {
            when(baseMapper.insert(any(SysConfig.class))).thenReturn(1);
            SysConfig config = buildConfig(1L, "unit.create." + System.nanoTime(), "v");

            try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(200L));
                assertTrue(service.save(config));
            }

            assertEquals(Long.valueOf(200L), config.getTenantId());
        }

        @Test
        @DisplayName("新增：平台身份落 NULL，不会被挂到某个租户")
        void shouldLeavePlatformRowWithoutTenant() {
            when(baseMapper.insert(any(SysConfig.class))).thenReturn(1);
            SysConfig config = buildConfig(1L, "unit.create.platform." + System.nanoTime(), "v");

            try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(null));
                assertTrue(service.save(config));
            }

            assertNull(config.getTenantId());
        }

        @Test
        @DisplayName("修改：跨租户按 id 直接命中数据库行时拒绝")
        void shouldRejectCrossTenantUpdate() {
            SysConfig other = buildConfig(9L, "unit.update." + System.nanoTime(), "v");
            other.setTenantId(999L);
            when(baseMapper.selectById(9L)).thenReturn(other);

            try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(200L));
                BusinessException ex = assertThrows(BusinessException.class,
                        () -> service.updateById(buildConfig(9L, "x", "y")));
                assertEquals(Integer.valueOf(403), ex.getCode());
            }
        }

        @Test
        @DisplayName("删除：跨租户按 id 直接命中数据库行时拒绝")
        void shouldRejectCrossTenantRemove() {
            SysConfig other = buildConfig(9L, "unit.remove." + System.nanoTime(), "v");
            other.setTenantId(999L);
            when(baseMapper.selectById(9L)).thenReturn(other);

            try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(200L));
                BusinessException ex = assertThrows(BusinessException.class, () -> service.removeById(9L));
                assertEquals(Integer.valueOf(403), ex.getCode());
            }
        }

        @Test
        @DisplayName("详情：按 id 读取带租户条件（跨租户按不存在处理）")
        void shouldScopeGetById() {
            when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(200L));
                assertNull(service.getById(9L));
            }

            assertTrue(captureQuery().getSqlSegment().contains("tenant_id ="));
        }

        @Test
        @DisplayName("键名唯一性只在同租户内判断")
        void shouldScopeConfigKeyExistence() {
            when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOf(200L));
                assertFalse(service.hasConfigKey("unit.key", null));
            }

            @SuppressWarnings("unchecked")
            ArgumentCaptor<LambdaQueryWrapper<SysConfig>> captor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(baseMapper).selectCount(captor.capture());
            assertTrue(captor.getValue().getSqlSegment().contains("tenant_id ="));
        }
    }
}