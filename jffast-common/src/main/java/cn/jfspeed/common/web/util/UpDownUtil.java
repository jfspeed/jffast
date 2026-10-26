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

/**
 * @author jffast
 * @date 2024/1/15 10:19
 * @desc
 */

import cn.hutool.core.codec.Base64;
import jakarta.servlet.http.HttpServletResponse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j

public class UpDownUtil {
    private UpDownUtil() {
    }

    public static String saveFile(InputStream inputStream, String path, String fileName) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path + File.separator + fileName))) {
            byte[] bs = new byte[1024];

            int len;
            while ((len = inputStream.read(bs)) != -1) {
                bos.write(bs, 0, len);
            }

            bos.flush();
        }
        return fileName;
    }

    public static String download(File file, HttpServletResponse response) throws IOException {
        if (!file.exists()) {
            return "下载失败，附件不存在";
        } else {
            String fileName = file.getName();
            log.debug("download path = {}", file.getAbsolutePath());
            InputStream inStream = new FileInputStream(file);
            return download(inStream, fileName, response);
        }
    }

    public static String download(InputStream inStream, String fileName, HttpServletResponse response) throws IOException {
        String msg = "";
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Length", String.valueOf(inStream.available()));
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        try (InputStream in = inStream; OutputStream os = response.getOutputStream()) {
            byte[] buff = new byte[1024];

            int len;
            while ((len = in.read(buff)) != -1) {
                os.write(buff, 0, len);
            }

            msg = "下载成功";
            os.flush();
        } catch (IOException var7) {
            msg = "下载失败，系统找不到指定路径";
        }

        return msg;
    }

    public static Map<String, String> getImageBase64String(String path) {
        Map<String, String> map = new HashMap();
        if (path == null || path.isEmpty()) {
            return null;
        } else {
            File file = new File(path);
            if (!file.exists()) {
                return null;
            } else {
                byte[] data = null;

                try (InputStream is = new FileInputStream(file);
                     InputStream is1 = new FileInputStream(file)) {
                    data = new byte[is.available()];
                    is.read(data);
                    BufferedImage image = ImageIO.read(is1);
                    if (image != null) {
                        map.put("height", Integer.toString(image.getHeight()));
                        map.put("width", Integer.toString(image.getWidth()));
                    }
                } catch (IOException var7) {
                    log.error("Operation failed", var7);
                }

                if (data != null) {
                    map.put("encode", Base64.encode(data));
                }
                return map;
            }
        }
    }

    public static Map<String, String> getImageBase64String(String path, boolean flag) {
        Map<String, String> map = new HashMap();
        if (path == null || path.isEmpty()) {
            return null;
        } else {
            File file = new File(path);
            if (!file.exists()) {
                return null;
            } else {
                byte[] data = null;

                try (InputStream is = new FileInputStream(file);
                     InputStream is1 = new FileInputStream(file)) {
                    data = new byte[is.available()];
                    is.read(data);
                    BufferedImage image = ImageIO.read(is1);
                    int height = image == null ? 0 : image.getHeight();
                    int width = image == null ? 0 : image.getWidth();
                    int showWidth;
                    int showHeight;
                    if (flag) {
                        if (width <= 50 && height <= 50) {
                            map.put("height", Integer.toString(height));
                            map.put("width", Integer.toString(width));
                        } else {
                            int cWidth = 50;
                            int cHeight = 50;
                            showWidth = cWidth;
                            showHeight = cHeight;
                            if (1.0 * (double)width / (double)height >= 1.0 * (double)cWidth / (double)cHeight) {
                                showHeight = cWidth * height / width;
                            } else {
                                showWidth = cHeight * width / height;
                            }

                            map.put("height", Integer.toString(showHeight));
                            map.put("width", Integer.toString(showWidth));
                        }
                    } else if (width <= 500 && height <= 500) {
                        map.put("height", Integer.toString(height));
                        map.put("width", Integer.toString(width));
                    } else {
                        int cWidth = 500;
                        int cHeight = 500;
                        showWidth = cWidth;
                        showHeight = cHeight;
                        if (1.0 * (double)width / (double)height >= 1.0 * (double)cWidth / (double)cHeight) {
                            showHeight = cWidth * height / width;
                        } else {
                            showWidth = cHeight * width / height;
                        }

                        map.put("height", Integer.toString(showHeight));
                        map.put("width", Integer.toString(showWidth));
                    }

                } catch (IOException var14) {
                    log.error("Operation failed", var14);
                }

                if (data != null) {
                    map.put("encode", Base64.encode(data));
                }
                return map;
            }
        }
    }
}
