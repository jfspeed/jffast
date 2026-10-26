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

import cn.jfspeed.common.web.util.IPUtil;
import cn.jfspeed.system.domain.vo.DiskInfo;
import cn.jfspeed.system.domain.vo.JvmInfo;
import cn.jfspeed.system.domain.vo.MonitorInfo;
import cn.jfspeed.system.domain.vo.ServerInfo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统监控服务
 *
 * @author jffast
 */
@Slf4j
@Service
public class SysMonitorService {

    /**
     * 获取系统监控信息
     */
    public MonitorInfo getMonitorInfo() {
        MonitorInfo monitor = new MonitorInfo();
        monitor.setServer(getServerInfo());
        monitor.setJvm(getJvmInfo());
        monitor.setDisks(getDiskInfo());
        return monitor;
    }

    /**
     * 获取服务器信息
     */
    private ServerInfo getServerInfo() {
        ServerInfo server = new ServerInfo();

        // 操作系统信息
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        server.setOsName(osBean.getName());
        server.setOsArch(osBean.getArch());
        server.setCpuCores(osBean.getAvailableProcessors());

        // CPU 使用率
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            server.setCpuUsage(Math.round(sunOsBean.getSystemCpuLoad() * 100.0) / 100.0);
        }

        // 内存信息
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        server.setTotalMemory(maxMemory);
        server.setUsedMemory(usedMemory);
        server.setFreeMemory(freeMemory);
        server.setMemoryUsage(Math.round((double) usedMemory / maxMemory * 100.0) / 100.0);

        // 服务器 IP
        try {
            server.setServerIp(InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) {
            server.setServerIp("127.0.0.1");
        }

        return server;
    }

    /**
     * 获取 JVM 信息
     */
    private JvmInfo getJvmInfo() {
        JvmInfo jvm = new JvmInfo();

        // JVM 基本信息
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        jvm.setJvmName(runtimeBean.getVmName());
        jvm.setJvmVersion(runtimeBean.getVmVersion());
        jvm.setJvmVendor(runtimeBean.getVmVendor());
        jvm.setStartTime(runtimeBean.getStartTime());
        jvm.setUpTime(runtimeBean.getUptime());
        jvm.setUpTimeStr(formatUptime(runtimeBean.getUptime()));

        // 堆内存信息
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        jvm.setHeapMax(heapUsage.getMax() / (1024 * 1024));
        jvm.setHeapUsed(heapUsage.getUsed() / (1024 * 1024));
        jvm.setHeapFree((heapUsage.getMax() - heapUsage.getUsed()) / (1024 * 1024));
        jvm.setHeapUsage(Math.round((double) heapUsage.getUsed() / heapUsage.getMax() * 100.0) / 100.0);

        // 非堆内存信息
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        jvm.setNonHeapUsed(nonHeapUsage.getUsed() / (1024 * 1024));

        // 线程信息
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        jvm.setThreadCount(threadBean.getThreadCount());
        jvm.setDaemonThreadCount(threadBean.getDaemonThreadCount());

        // GC 信息
        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += gcBean.getCollectionCount();
            gcTime += gcBean.getCollectionTime();
        }
        jvm.setGcCount(gcCount);
        jvm.setGcTime(gcTime);

        return jvm;
    }

    /**
     * 获取磁盘信息
     */
    private List<DiskInfo> getDiskInfo() {
        List<DiskInfo> disks = new ArrayList<>();
        java.io.File[] roots = java.io.File.listRoots();
        for (java.io.File root : roots) {
            DiskInfo disk = new DiskInfo();
            disk.setDirName(root.getAbsolutePath());
            disk.setTotal(Math.round(root.getTotalSpace() / (1024.0 * 1024.0 * 1024.0) * 100.0) / 100.0);
            disk.setFree(Math.round(root.getFreeSpace() / (1024.0 * 1024.0 * 1024.0) * 100.0) / 100.0);
            disk.setUsed(Math.round((root.getTotalSpace() - root.getFreeSpace()) / (1024.0 * 1024.0 * 1024.0) * 100.0) / 100.0);
            disk.setUsage(Math.round((double) (root.getTotalSpace() - root.getFreeSpace()) / root.getTotalSpace() * 100.0) / 100.0);
            disks.add(disk);
        }
        return disks;
    }

    /**
     * 格式化运行时间
     */
    private String formatUptime(long uptime) {
        long days = uptime / (1000 * 60 * 60 * 24);
        long hours = (uptime % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
        long minutes = (uptime % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (uptime % (1000 * 60)) / 1000;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("天 ");
        }
        if (hours > 0) {
            sb.append(hours).append("小时 ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("分钟 ");
        }
        sb.append(seconds).append("秒");
        return sb.toString();
    }
}
