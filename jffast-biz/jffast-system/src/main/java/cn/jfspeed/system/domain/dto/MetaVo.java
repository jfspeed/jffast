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
package cn.jfspeed.system.domain.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * @author jffast
 */
public class MetaVo {
    private Object title;
    private String icon;
    private String activeMenu;
    private Boolean redDot;
    private Integer orderNo;
    private Boolean hidden;

    public Object getTitle() {
        return this.title;
    }

    public void setTitle(Object title) {
        this.title = title;
    }

    public String getIcon() {
        return this.icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getActiveMenu() {
        return this.activeMenu;
    }

    public void setActiveMenu(String activeMenu) {
        this.activeMenu = activeMenu;
    }

    public Boolean getRedDot() {
        return this.redDot;
    }

    public void setRedDot(Boolean redDot) {
        this.redDot = redDot;
    }

    public Integer getOrderNo() {
        return this.orderNo;
    }

    public void setOrderNo(Integer orderNo) {
        this.orderNo = orderNo;
    }

    public Boolean getHidden() {
        return this.hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public MetaVo() {
    }

    public MetaVo(String title, String icon, String activeMenu) {
        Map<String, String> titleMap = new HashMap<>();
        titleMap.put("zh_CN", title);
        titleMap.put("en_US", title);
        this.title = titleMap;
        this.icon = icon;
        this.activeMenu = activeMenu;
    }
}
