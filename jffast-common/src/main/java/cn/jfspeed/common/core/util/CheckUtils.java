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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author jffast
 */
public class CheckUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$");

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^1[3-9]\\d{9}$");

    public static final Pattern NUMBER_PATTERN = Pattern.compile(
            "^[-+]?\\d+(\\.\\d+)?$"
    );

    private static String getMessage(String[] messages, String defaultValue) {
        return (messages != null && messages.length > 0 && messages[0] != null)
                ? messages[0]
                : defaultValue;
    }

    public static void checkNull(Object obj, String... message) {
        if (obj == null) {
            String msg = getMessage(message, "信息不能为空");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkNotNull(Object obj, String... message) {
        if (obj != null) {
            String msg = getMessage(message, "信息不能为空");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkEmpty(String str, String... message) {
        if (str == null || str.trim().isEmpty()) {
            String msg = getMessage(message, "内容不能为空或空串");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkEmpty(Collection<?> collection, String... message) {
        if (collection == null || collection.isEmpty()) {
            String msg = getMessage(message, "列表不能为空");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkEmpty(Map<?, ?> map, String... message) {
        if (map == null || map.isEmpty()) {
            String msg = getMessage(message, "数据集合不能为空");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkEmpty(Object[] array, String... message) {
        if (array == null || array.length == 0) {
            String msg = getMessage(message, "数组不能为空");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkEmpty(Object object, String... message) {
        if (object == null) {
            String msg = getMessage(message, "对象不能为空");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkNotEqual(Object obj1, Object obj2, String... message) {
        if (obj1 == null && obj2 == null) {
        } else if (obj1 == null || obj2 == null) {
            String msg = getMessage(message, "数据不一致");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        } else if (obj1 instanceof Number && obj2 instanceof Number && new BigDecimal(obj1.toString()).compareTo(new BigDecimal(obj2.toString())) != 0) {
            String msg = getMessage(message, "数据不一致");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        } else if (obj1 instanceof String && obj2 instanceof String && !obj1.equals(obj2)) {
            String msg = getMessage(message, "数据不一致");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        } else if (!obj1.getClass().isInstance(obj2) && !obj1.toString().equals(obj2.toString())) {
            String msg = getMessage(message, "数据不一致");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkEqual(Object obj1, Object obj2, String... message) {
        if (obj1 == null && obj2 == null) {
            String msg = getMessage(message, "数据一致");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        } else if (obj1 == null || obj2 == null) {
        } else if (obj1 instanceof Number && obj2 instanceof Number && new BigDecimal(obj1.toString()).compareTo(new BigDecimal(obj2.toString())) == 0) {
            String msg = getMessage(message, "数据一致");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        } else if (obj1 instanceof String && obj2 instanceof String && obj1.equals(obj2)) {
            String msg = getMessage(message, "数据一致");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        } else if (obj1.getClass().isInstance(obj2) && obj1.toString().equals(obj2.toString())) {
            String msg = getMessage(message, "数据一致");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkIn(Number obj1, List<Number> list, String... message) {
        list = list == null ? new ArrayList<>() : list;
        if (list.contains(obj1)) {
            String msg = getMessage(message, "数据在范围内");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkIn(String obj1, List<String> list, String... message) {
        list = list == null ? new ArrayList<>() : list;
        if (list.contains(obj1)) {
            String msg = getMessage(message, "数据在范围内");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkNotIn(Number obj1, List<Number> list, String... message) {
        list = list == null ? new ArrayList<>() : list;
        if (!list.contains(obj1)) {
            String msg = getMessage(message, "数据不在范围内");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkNotIn(String obj1, List<String> list, String... message) {
        list = list == null ? new ArrayList<>() : list;
        if (!list.contains(obj1)) {
            String msg = getMessage(message, "数据不在范围内");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkMin(Number number, Number min, String... message) {
        checkNull(number, "数值信息不能为空");
        checkNull(min, "最小值信息不能为空");
        if (number.doubleValue() < min.doubleValue()) {
            String msg = getMessage(message, String.format("数值不能小于%s", min));
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkMax(Number number, Number max, String... message) {
        checkNull(number, "数值信息不能为空");
        checkNull(max, "最大值信息不能为空");
        if (number.doubleValue() > max.doubleValue()) {
            String msg = getMessage(message, String.format("数值不能大于%s", max));
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkRange(Number number, Number min, Number max, String... message) {
        checkNull(number, "数值信息不能为空");
        checkNull(min, "最小值信息不能为空");
        checkNull(max, "最大值信息不能为空");
        if (number.doubleValue() < min.doubleValue() || number.doubleValue() > max.doubleValue()) {
            String msg = getMessage(message, String.format("数值必须在%s到%s之间", min, max));
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkLength(String str, int minLen, int maxLen, String... message) {
        checkNull(str, "字符串信息不能为空");
        if (str.length() < minLen || str.length() > maxLen) {
            String msg = getMessage(message, String.format("长度必须在%s到%s个字符之间", minLen, maxLen));
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkEmail(String email, String... message) {
        checkEmpty(email, "邮箱地址不能为空或空串");
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            String msg = getMessage(message, "请输入有效的邮箱地址");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkPhone(String phone, String... message) {
        checkEmpty(phone, "手机号码不能为空或空串");
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            String msg = getMessage(message, "请输入有效的手机号码");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkIsTrue(boolean expression, String... message) {
        if (!expression) {
            String msg = getMessage(message, "信息校验失败，请检查输入");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkIsFalse(boolean expression, String... message) {
        if (expression) {
            String msg = getMessage(message, "信息校验失败，请检查输入");
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    public static void checkIsNumber(String expression, String... message) {
        String msg = getMessage(message, "请输入有效的数字");
        checkEmpty(expression, msg);
        if (!NUMBER_PATTERN.matcher(expression).matches()) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), msg);
        }
    }

    /**
     * 抛出异常
     * @param code
     * @param msg
     */
    private static void throwException(int code , String msg) {
        throw new BusinessException(code, msg);
    }

}
