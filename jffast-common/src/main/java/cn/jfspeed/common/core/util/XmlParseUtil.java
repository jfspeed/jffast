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
package cn.jfspeed.common.core.util;

import java.io.StringReader;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * @author jffast
 */
@Slf4j
public class XmlParseUtil {

    public static String getParamFromXml(String xmlStr, String paramName) {
        if (!StringUtils.hasText(xmlStr)) {
            log.warn("XML字符串为空，无法解析");
            return null;
        }

        SAXReader saxReader = new SAXReader();
        try {
            saxReader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            saxReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            saxReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = saxReader.read(new StringReader(xmlStr));

            Element rootElement = document.getRootElement();
            if (rootElement == null || !"msg".equals(rootElement.getName())) {
                log.warn("XML根节点不是msg，解析失败");
                return null;
            }

            String fromUsername = rootElement.attributeValue(paramName);
            if (!StringUtils.hasText(fromUsername)) {
                log.warn("msg节点未找到{}属性", paramName);
                return null;
            }

            return fromUsername;

        } catch (Exception e) {
            log.error("XML解析失败（格式错误或编码问题）：{}", e.getMessage(), e);
        }

        return null;
    }
}
