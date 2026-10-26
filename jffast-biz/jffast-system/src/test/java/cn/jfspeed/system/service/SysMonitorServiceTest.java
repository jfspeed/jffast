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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cn.jfspeed.system.domain.vo.DiskInfo;
import cn.jfspeed.system.domain.vo.JvmInfo;
import cn.jfspeed.system.domain.vo.MonitorInfo;
import cn.jfspeed.system.domain.vo.ServerInfo;

/**
 * SysMonitorService 单元测试
 *
 * @author jffast
 */
class SysMonitorServiceTest {

    private final SysMonitorService service = new SysMonitorService();

    @Test
    @DisplayName("getMonitorInfo 应返回完整的监控信息")
    void shouldReturnCompleteMonitorInfo() {
        MonitorInfo info = service.getMonitorInfo();

        assertNotNull(info);
        assertNotNull(info.getServer());
        assertNotNull(info.getJvm());
        assertNotNull(info.getDisks());
    }

    @Test
    @DisplayName("getServerInfo 应返回服务器基本信息")
    void shouldReturnServerInfo() {
        MonitorInfo info = service.getMonitorInfo();
        ServerInfo server = info.getServer();

        assertNotNull(server.getOsName());
        assertNotNull(server.getOsArch());
        assertTrue(server.getCpuCores() > 0);
        assertTrue(server.getTotalMemory() > 0);
        assertNotNull(server.getServerIp());
    }

    @Test
    @DisplayName("getJvmInfo 应返回 JVM 基本信息")
    void shouldReturnJvmInfo() {
        MonitorInfo info = service.getMonitorInfo();
        JvmInfo jvm = info.getJvm();

        assertNotNull(jvm.getJvmName());
        assertNotNull(jvm.getJvmVersion());
        assertNotNull(jvm.getJvmVendor());
        assertTrue(jvm.getStartTime() > 0);
        assertTrue(jvm.getUpTime() > 0);
        assertNotNull(jvm.getUpTimeStr());
        assertTrue(jvm.getHeapMax() > 0);
        assertTrue(jvm.getThreadCount() > 0);
    }

    @Test
    @DisplayName("getDiskInfo 应返回磁盘列表")
    void shouldReturnDiskInfo() {
        MonitorInfo info = service.getMonitorInfo();
        java.util.List<DiskInfo> disks = info.getDisks();

        assertNotNull(disks);
        assertTrue(disks.size() > 0);
        for (DiskInfo disk : disks) {
            assertNotNull(disk.getDirName());
            assertTrue(disk.getTotal() >= 0);
            assertTrue(disk.getFree() >= 0);
        }
    }
}