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

import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j

/**
 * @author jffast
 */
public class TreeUtil {
    private TreeUtil() {
    }

    private static <T> Field getField(T t, String fieldName) {
        Class<?> clazz = t.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new BusinessException(FrameworkErrorCode.SYSTEM_ERROR.getCode(), "反射异常，NoSuchFieldException");
    }

    private static <T> Object getFieldValue(T t, String fieldName) {
        try {
            Field field = getField(t, fieldName);
            return field.get(t);
        } catch (IllegalAccessException var3) {
            throw new BusinessException(FrameworkErrorCode.SYSTEM_ERROR.getCode(), "反射异常，IllegalAccessException");
        }
    }

    public static <T> List<T> buildTreeIsRoot(List<T> list, Long parentId) {
        // 复制列表避免修改原始数据
        List<T> copyList = new ArrayList<>(list);
        List<T> root = buildTree(copyList, parentId);
        getChild(root, copyList);
        return root;
    }

    public static <T> List<T> buildTreeNotRoot(List<T> list) {
        // 复制列表避免修改原始数据
        List<T> sources = new ArrayList<>(list);
        List<T> result = new ArrayList();
        Iterator<T> var3 = sources.iterator();

        while(var3.hasNext()) {
            T t =  var3.next();
            Long parentId = (Long)getFieldValue(t, "parentId");
            List<T> copyList = new ArrayList<>(list);
            List<T> root = buildTree(copyList, parentId);
            getChild(root, copyList);
            result.addAll(root);
        }

        return result;
    }

    public static <T> List<T> buildTree(List<T> list, Object pId) {
        List<T> root = new ArrayList();
        Iterator<T> it = list.iterator();

        while(it.hasNext()) {
            T t = it.next();
            Object parentIdObj = getFieldValue(t, "parentId");
            if (parentIdObj != null && String.valueOf(parentIdObj).equals(String.valueOf(pId))) {
                root.add(t);
                it.remove();
            }
        }

        return root;
    }

    public static <T> void getChild(List<T> root, List<T> list) {
        try {
            Iterator<T> var2 = root.iterator();

            while(var2.hasNext()) {
                T nav = var2.next();
                Object idObj = getFieldValue(nav, "id");
                // 复制列表避免递归时修改原始list影响其他节点
                List<T> copyList = new ArrayList<>(list);
                List<T> childList = buildTree(copyList, idObj);
                getChild(childList, copyList);
                Field child = getField(nav, "children");
                child.set(nav, childList);
            }
        } catch (Exception var7) {
            log.error("Operation failed", var7);
        }

    }
}
