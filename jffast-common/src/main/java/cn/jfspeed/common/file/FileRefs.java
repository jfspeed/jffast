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
package cn.jfspeed.common.file;

import cn.hutool.core.util.StrUtil;

/**
 * 库里存的文件引用格式：{@code file:{id}}。
 *
 * <p><b>为什么库里不存 URL</b>：签名 URL 有 TTL，落库即过期；存储定位键（{@code /upload/...}）
 * 会随 {@code access-path} 变化而失效，且裸路径一旦外泄就等于永久可读。
 * {@code file:{id}} 是**稳定引用**：换存储、换域名、换签名策略都不影响它，
 * 展示时再按当前策略换发。
 *
 * <p>ID 一律是字符串形态（雪花 ID 19 位，超出 JS 安全整数范围，数字型会在前端丢精度）。
 *
 * @author jffast
 */
public final class FileRefs {

    /** 引用前缀 */
    public static final String PREFIX = "file:";

    private FileRefs() {
    }

    /** 构造引用 */
    public static String of(Long fileId) {
        return fileId == null ? null : PREFIX + fileId;
    }

    /**
     * 解析引用里的文件 ID；非本形态（历史裸 URL、空值）返回 {@code null}。
     */
    public static Long parseId(String ref) {
        if (StrUtil.isBlank(ref) || !ref.startsWith(PREFIX)) {
            return null;
        }
        String raw = ref.substring(PREFIX.length()).trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 是否是本形态的引用 */
    public static boolean isRef(String ref) {
        return parseId(ref) != null;
    }

    /**
     * 把「可能是签名 URL 的值」归一成稳定引用。
     *
     * <p>前端可能把带签名的直显地址回传（出参里 {@code image}/{@code avatar} 就是它），
     * 直接落库会存下一条会过期的签名 URL；这里统一还原成 {@code file:{id}}。
     *
     * <p>归一不了的值（历史裸路径 {@code /upload/...} 等）原样返回——由迁移脚本统一处理，
     * 这里不做「猜测式」改写，避免把无关字符串改坏。
     */
    public static String toStableRef(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith(PREFIX)) {
            return trimmed;
        }
        Long id = parseViewUrlId(trimmed);
        return id == null ? value : PREFIX + id;
    }

    /**
     * 从签名直显地址里取文件 ID：{@code /sys/file/view/{id}?ticket=...} → id。
     *
     * <p>只取路径里的 id，不校验签名——归一化的目的是「还原出稳定引用」，
     * 真正的归属校验由调用方（{@code validateFileRef}）负责。
     *
     * @return 文件 ID；不是本形态时返回 {@code null}
     */
    public static Long parseViewUrlId(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        String path = url.trim();
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        int fragmentIndex = path.indexOf('#');
        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }
        int idx = path.lastIndexOf(VIEW_PATH_PREFIX);
        if (idx < 0) {
            return null;
        }
        String idPart = path.substring(idx + VIEW_PATH_PREFIX.length()).replaceAll("/+$", "");
        if (idPart.isEmpty() || idPart.contains("/")) {
            return null;
        }
        try {
            return Long.valueOf(idPart);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 匿名直显端点（API 根相对），与 {@code FileUrlSigner.VIEW_PATH_PREFIX} 同口径 */
    public static final String VIEW_PATH_PREFIX = "/sys/file/view/";
}
