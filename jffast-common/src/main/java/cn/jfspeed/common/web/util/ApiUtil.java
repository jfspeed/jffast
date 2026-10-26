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

import cn.jfspeed.common.security.util.RSAUtil;
import cn.jfspeed.common.security.util.SignUtils;
import cn.hutool.json.JSONUtil;
import cn.jfspeed.common.web.domain.dto.ApiPointParam;
import cn.jfspeed.common.core.exception.BusinessException;

import java.util.HashMap;
import java.util.Map;

/**
 * @author jffast
 */
public class ApiUtil {

    public static Map<String, String> getRequestMap(ApiPointParam paramDTO, String rsaPrivateKey, String appSecret) {
        String data = paramDTO.getData();
        String dataStr;
        try {
            dataStr = RSAUtil.decryptByPrivateKey(data, rsaPrivateKey);
        } catch (Exception e) {
            throw new BusinessException(ApiErrorCode.RSA_DECRYPT_FAILED);
        }

        Map<String, String> tempMap = JSONUtil.toBean(dataStr, Map.class);
        boolean flag = SignUtils.verifySign(tempMap, appSecret);
        if (!flag) {
            throw new BusinessException(ApiErrorCode.SIGN_VERIFY_FAILED);
        }
        return tempMap;
    }

    public static Map<String, Object> getResponseMap(Map<String, String> paramMap, String appSecret, String publicKeyStr) {
        Map<String, String> signMap = SignUtils.generateSign(paramMap, appSecret);
        Map<String, Object> paramMapLast = new HashMap<>();
        String dataStr = RSAUtil.encryptByPublicKey(JSONUtil.toJsonStr(signMap), publicKeyStr);
        paramMapLast.put("data", dataStr);
        return paramMapLast;
    }

    public static Map<String, String> getDataMap(String encryptDataStr, String privateKeyStr) {
        String dataStr = RSAUtil.decryptByPrivateKey(encryptDataStr, privateKeyStr);
        return JSONUtil.toBean(dataStr, Map.class);
    }
}
