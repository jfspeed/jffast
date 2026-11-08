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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RSA fail-closed 判定口径：只有显式 dev / test 才算非生产，其余（含未指定）一律按生产严格。
 */
class RsaKeyInitializerProductionTest {

    @Test
    @DisplayName("无 profile：按生产严格")
    void noProfileIsProduction() {
        assertTrue(RsaKeyInitializer.isProductionProfile(new String[0]));
    }

    @Test
    @DisplayName("dev：非生产（宽松）")
    void devIsNotProduction() {
        assertFalse(RsaKeyInitializer.isProductionProfile(new String[] {"dev"}));
    }

    @Test
    @DisplayName("test：非生产（宽松）")
    void testIsNotProduction() {
        assertFalse(RsaKeyInitializer.isProductionProfile(new String[] {"test"}));
    }

    @Test
    @DisplayName("prod：生产（严格）")
    void prodIsProduction() {
        assertTrue(RsaKeyInitializer.isProductionProfile(new String[] {"prod"}));
    }

    @Test
    @DisplayName("staging：未识别 profile 也按生产严格")
    void stagingIsProduction() {
        assertTrue(RsaKeyInitializer.isProductionProfile(new String[] {"staging"}));
    }
}
