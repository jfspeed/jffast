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
package cn.jfspeed.system.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.pinyin.PinyinUtil;
import com.rnkrsoft.bopomofo4j.Bopomofo4j;

/**
 * 名称 → 代号（首字母）：角色 / 字典共用。
 *
 * <p>拼音引擎的初始化必须早于 {@link PinyinUtil} 首次创建引擎单例：Bopomofo4j
 * 默认沙盒模式会在运行期连 Maven 中心下载 jar，不符合离线/内网边界。
 * 放在本类的静态块里，任何调用方（角色、字典）都先经过它，不再依赖控制器加载顺序。
 *
 * <p>拼音引擎对部分输入返回 null（历史缺陷：/sys/role/createDictCode 实测 500），
 * 这里降级为「保留 ASCII 字母数字」的确定性兜底，绝不抛空指针。
 *
 * @author jffast
 */
public final class PinyinCodeUtil {

    static {
        Bopomofo4j.local();
    }

    private PinyinCodeUtil() {
    }

    public static String firstLetterCode(String name) {
        String first = PinyinUtil.getFirstLetter(name, "");
        if (first != null && !first.isBlank()) {
            return first.toUpperCase();
        }
        StringBuilder sb = new StringBuilder();
        for (char c : StrUtil.nullToEmpty(name).toCharArray()) {
            if (c < 128 && Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString().toUpperCase();
    }
}
