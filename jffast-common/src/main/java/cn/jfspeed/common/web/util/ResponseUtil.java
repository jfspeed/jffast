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
package cn.jfspeed.common.web.util;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import cn.hutool.core.io.IoUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * @author jffast
 */
@Slf4j
public class ResponseUtil {

    public static void writeString(HttpServletResponse response, String content) {
        response.setContentType("text/plain;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");

        OutputStream out = null;
        try {
            out = response.getOutputStream();
            IoUtil.write(out, StandardCharsets.UTF_8, false, content);
            out.flush();
        } catch (Exception e) {
            log.error("响应字符串写入失败", e);
        } finally {
            IoUtil.close(out);
        }
    }

    public static void writeJson(HttpServletResponse response, String jsonContent) {
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");

        OutputStream out = null;
        try {
            out = response.getOutputStream();
            IoUtil.write(out, StandardCharsets.UTF_8, false, jsonContent);
            out.flush();
        } catch (Exception e) {
            log.error("JSON字符串返回失败", e);
        } finally {
            IoUtil.close(out);
        }
    }
}
