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
package cn.jfspeed.system.enums;

import cn.jfspeed.common.core.domain.dto.KvResponse;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jffast
 * @date 2024/1/23 16:51
 * @desc  菜单类型枚举
 */
@Getter
public enum MenuTypeEnum {

    CERTIFICATE("M", "目录"),
    RELATIONSHIP("C", "菜单"),
    RELATIONTOIN("F", "按钮");
    private final String code;
    private final String desc;
    MenuTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取枚举列表
     * @return
     */
    public static List<KvResponse<String,String>> getList(){
        List<KvResponse<String,String>> list = new ArrayList<>();
        for (MenuTypeEnum value : MenuTypeEnum.values()) {
             list.add(
                    new KvResponse<>(value.getCode(),value.getDesc())
            );
        }
        return list;
    }

}
