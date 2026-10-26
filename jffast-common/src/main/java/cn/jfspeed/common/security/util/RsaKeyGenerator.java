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
package cn.jfspeed.common.security.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.asymmetric.AsymmetricAlgorithm;
import cn.hutool.crypto.SecureUtil;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;

/**
 * RSA 密钥对生成工具
 * <p>
 * 运行方式：
 * <pre>
 * mvn compile -pl jffast-common -q
 * java -cp "jffast-common/target/classes;$(mvn -pl jffast-common dependency:build-classpath -q -Dmdep.outputFile=-)" cn.jfspeed.common.security.util.RsaKeyGenerator
 * </pre>
 * 或在 IDE 中直接运行此类。
 *
 * @author jffast
 */
public class RsaKeyGenerator {

    public static void main(String[] args) {
        List<String> keys = RSAUtil.generateKey();
        String publicKey = keys.get(0);
        String privateKey = keys.get(1);

        System.out.println("========== RSA 密钥对生成完成 ==========");
        System.out.println();
        System.out.println("【后端配置】将以下配置添加到环境变量或 application-dev.yml：");
        System.out.println();
        System.out.println("环境变量方式（推荐，写入启动脚本）：");
        System.out.println("set \"RSA_PRIVATE_KEY=" + privateKey + "\"");
        System.out.println("set \"RSA_PUBLIC_KEY=" + publicKey + "\"");
        System.out.println();
        System.out.println("或写入 application-dev.yml：");
        System.out.println("jffast:");
        System.out.println("  rsa:");
        System.out.println("    private-key: " + privateKey);
        System.out.println("    public-key: " + publicKey);
        System.out.println();
        System.out.println("【前端配置】将公钥写入 jffast-ui/.env.development：");
        System.out.println("VITE_RSA_PUBLIC_KEY = " + publicKey);
        System.out.println();
        System.out.println("=========================================");
    }
}
