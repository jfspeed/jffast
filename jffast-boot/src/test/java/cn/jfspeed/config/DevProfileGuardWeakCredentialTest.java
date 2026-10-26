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
package cn.jfspeed.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DevProfileGuard} 弱口令 fail-fast 判定逻辑的回归测试。
 *
 * <p>守护的是一条真实事故链：随仓库公开的默认口令 {@code test1234} 被搬到**远程**数据库 /
 * Redis 上 —— 本机开发时它只是"方便"，一旦指向公网服务，它就是"人人可用的后门"。
 * 因此判据必须是「弱口令 **且** 远程端点」两个条件同时成立，单条任一都不该拦。
 */
class DevProfileGuardWeakCredentialTest {

    private static final String LOCAL_URL = "jdbc:mysql://127.0.0.1:3306/jffast?useSSL=false";
    private static final String REMOTE_URL = "jdbc:mysql://mysql.example.com:3307/jffast?useSSL=false";

    @Test
    @DisplayName("本机 + 默认口令：零配置本机开发不受影响，不拦")
    void localEndpointWithPublicPasswordIsAllowed() {
        List<String> findings = DevProfileGuard.collectWeakCredentialFindings(
                DevProfileGuard.PUBLIC_TEST_PASSWORD, DevProfileGuard.PUBLIC_TEST_PASSWORD,
                DevProfileGuard.PUBLIC_TEST_PASSWORD, LOCAL_URL, "127.0.0.1");
        assertTrue(findings.isEmpty(), "本机开发不得被拦截，实际命中：" + findings);
    }

    @Test
    @DisplayName("远程数据库 + 默认初始口令：命中管理员初始口令暴露")
    void remoteDbWithPublicInitPasswordIsRejected() {
        List<String> findings = DevProfileGuard.collectWeakCredentialFindings(
                DevProfileGuard.PUBLIC_TEST_PASSWORD, "Str0ngDbPass", "Str0ngRedisPass", REMOTE_URL, "127.0.0.1");
        assertEquals(1, findings.size(), "实际命中：" + findings);
        assertTrue(findings.get(0).contains("管理员初始口令"), findings.get(0));
    }

    @Test
    @DisplayName("远程数据库 + 默认数据库口令：命中数据库口令暴露")
    void remoteDbWithPublicDbPasswordIsRejected() {
        List<String> findings = DevProfileGuard.collectWeakCredentialFindings(
                "Str0ngInit", DevProfileGuard.PUBLIC_TEST_PASSWORD, "Str0ngRedisPass", REMOTE_URL, "127.0.0.1");
        assertEquals(1, findings.size(), "实际命中：" + findings);
        assertTrue(findings.get(0).contains("数据库口令"), findings.get(0));
    }

    @Test
    @DisplayName("远程 Redis + 默认 Redis 口令：命中 Redis 口令暴露")
    void remoteRedisWithPublicRedisPasswordIsRejected() {
        List<String> findings = DevProfileGuard.collectWeakCredentialFindings(
                "Str0ngInit", "Str0ngDbPass", DevProfileGuard.PUBLIC_TEST_PASSWORD, LOCAL_URL, "redis.example.com");
        assertEquals(1, findings.size(), "实际命中：" + findings);
        assertTrue(findings.get(0).contains("Redis 口令"), findings.get(0));
    }

    @Test
    @DisplayName("远程端点但口令全部为强口令：不命中")
    void remoteEndpointWithStrongPasswordsIsAllowed() {
        List<String> findings = DevProfileGuard.collectWeakCredentialFindings(
                "Str0ngInit", "Str0ngDbPass", "Str0ngRedisPass", REMOTE_URL, "redis.example.com");
        assertTrue(findings.isEmpty(), "强口令不得被误拦，实际命中：" + findings);
    }

    @Test
    @DisplayName("初始口令置空（随机生成强口令）：不命中")
    void blankInitPasswordIsAllowed() {
        List<String> findings = DevProfileGuard.collectWeakCredentialFindings(
                "", "Str0ngDbPass", "Str0ngRedisPass", REMOTE_URL, "redis.example.com");
        assertTrue(findings.isEmpty(), "空初始口令=随机强口令，不得被拦，实际命中：" + findings);
    }

    @Test
    @DisplayName("localhost / 127.x / ::1 都算本机")
    void localHostVariantsAreRecognized() {
        assertTrue(DevProfileGuard.isLocalHost("localhost"));
        assertTrue(DevProfileGuard.isLocalHost("127.0.0.1"));
        assertTrue(DevProfileGuard.isLocalHost("127.0.0.53"));
        assertTrue(DevProfileGuard.isLocalHost("::1"));
        assertTrue(DevProfileGuard.isLocalHost("[::1]"));
        assertTrue(DevProfileGuard.isLocalHost(null));
        assertFalse(DevProfileGuard.isLocalHost("mysql.example.com"));
        assertFalse(DevProfileGuard.isLocalHost("10.0.0.8"));
    }

    @Test
    @DisplayName("JDBC URL 主机名提取")
    void extractJdbcHostHandlesCommonShapes() {
        assertEquals("127.0.0.1", DevProfileGuard.extractJdbcHost(LOCAL_URL));
        assertEquals("mysql.example.com", DevProfileGuard.extractJdbcHost(REMOTE_URL));
        assertEquals("::1", DevProfileGuard.extractJdbcHost("jdbc:mysql://[::1]:3306/db"));
        assertNull(DevProfileGuard.extractJdbcHost("jdbc:mysql://"));
        assertNull(DevProfileGuard.extractJdbcHost("not-a-url"));
        assertNull(DevProfileGuard.extractJdbcHost(null));
        assertNull(DevProfileGuard.extractJdbcHost("  "));
    }

    @Test
    @DisplayName("未配置数据源时不得凭空判定为远程")
    void missingUrlDoesNotImplyRemote() {
        List<String> findings = DevProfileGuard.collectWeakCredentialFindings(
                DevProfileGuard.PUBLIC_TEST_PASSWORD, DevProfileGuard.PUBLIC_TEST_PASSWORD,
                DevProfileGuard.PUBLIC_TEST_PASSWORD, "", "");
        assertTrue(findings.isEmpty(), "端点未配置时不应判远程，实际命中：" + findings);
    }

    @Test
    @DisplayName("命中项必须给出可操作的处理指引")
    void findingsMentionActionableEndpoints() {
        List<String> findings = DevProfileGuard.collectWeakCredentialFindings(
                DevProfileGuard.PUBLIC_TEST_PASSWORD, DevProfileGuard.PUBLIC_TEST_PASSWORD,
                DevProfileGuard.PUBLIC_TEST_PASSWORD, REMOTE_URL, "redis.example.com");
        assertFalse(findings.isEmpty());
        findings.forEach(f -> assertNotNull(f));
        assertTrue(findings.stream().anyMatch(f -> f.contains("mysql.example.com")
                        || f.contains("redis.example.com")),
                "命中项应指明具体端点：" + findings);
    }
}
