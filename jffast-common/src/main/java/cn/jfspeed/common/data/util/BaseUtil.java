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
package cn.jfspeed.common.data.util;

/**
 * @author jffast
 * @date 2024/1/15 10:14
 * @desc
 */

import org.springframework.beans.BeanUtils;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BaseUtil {
    private BaseUtil() {
    }

    public static <K, V> void removeNullValue(Map<K, V> map) {
        if (null != map && !map.isEmpty()) {
            Iterator iter = map.entrySet().iterator();

            while(true) {
                Map.Entry entry;
                do {
                    if (!iter.hasNext()) {
                        return;
                    }

                    entry = (Map.Entry)iter.next();
                } while(null != entry.getValue() && !"".equals(entry.getValue()));

                iter.remove();
            }
        }
    }

    public static <T1, T2> List<T2> listCopy(List<T1> sourceList, Class<T2> clazz) {
        return null != sourceList && !sourceList.isEmpty() ? (List)sourceList.stream().map((source) -> {
            Object target;
            try {
                target = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception var4) {
                throw new RuntimeException();
            }

            BeanUtils.copyProperties(source, target);
            return target;
        }).collect(Collectors.toList()) : null;
    }

    public static String setQueryApplyScope(Map<String, Object> scope, String fieldName, String self, boolean isId) {
        String safeFieldName = sanitizeFieldName(fieldName);
        String safeSelf = self != null ? sanitizeFieldName(self) : null;

        StringBuilder sqlString = new StringBuilder();
        if (isId) {
            if (null != scope.get("zdyid")) {
                sqlString.append(String.format("(FIND_IN_SET(%s,'%s')", safeFieldName, sanitizeIdValue(scope.get("zdyid"))));
                if (null != scope.get("bmjzbmid")) {
                    sqlString.append(String.format(" or FIND_IN_SET(%s,'%s')", safeFieldName, sanitizeIdValue(scope.get("bmjzbmid"))));
                } else if (null != scope.get("bmid")) {
                    sqlString.append(String.format(" or %s = '%s'", safeFieldName, sanitizeIdValue(scope.get("bmid"))));
                }

                sqlString.append(")");
            } else if (null != scope.get("bmjzbmid")) {
                sqlString.append(String.format("FIND_IN_SET(%s,'%s')", safeFieldName, sanitizeIdValue(scope.get("bmjzbmid"))));
            } else if (null != scope.get("bmid")) {
                sqlString.append(String.format("%s = '%s'", safeFieldName, sanitizeIdValue(scope.get("bmid"))));
            }
        } else if (null != scope.get("zdymc")) {
            sqlString.append(String.format("(FIND_IN_SET(%s,'%s')", safeFieldName, sanitizeNameValue(scope.get("zdymc"))));
            if (null != scope.get("bmJzbmmc")) {
                sqlString.append(String.format(" or FIND_IN_SET(%s,'%s')", safeFieldName, sanitizeNameValue(scope.get("bmJzbmmc"))));
            } else if (null != scope.get("bmmc")) {
                sqlString.append(String.format(" or %s = '%s'", safeFieldName, sanitizeNameValue(scope.get("bmmc"))));
            }

            sqlString.append(")");
        } else if (null != scope.get("bmJzbmmc")) {
            sqlString.append(String.format("FIND_IN_SET(%s,'%s')", safeFieldName, sanitizeNameValue(scope.get("bmJzbmmc"))));
        } else if (null != scope.get("bmmc")) {
            sqlString.append(String.format("%s = '%s'", safeFieldName, sanitizeNameValue(scope.get("bmmc"))));
        }

        if (safeSelf != null && null != scope.get("self")) {
            if (sqlString.length() > 0) {
                sqlString.append(" and ");
            }

            sqlString.append(String.format("%s = '%s'", safeSelf, sanitizeIdValue(scope.get("self"))));
        }

        return sqlString.toString();
    }

    private static String sanitizeFieldName(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        return fieldName.replaceAll("[^a-zA-Z0-9_.]", "");
    }

    private static String sanitizeIdValue(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        return str.replaceAll("[^0-9,]", "");
    }

    private static String sanitizeNameValue(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        return str.replace("'", "''");
    }

    public static boolean hasElement(List<String> list1, List<String> list2, boolean isAnd) {
        boolean flag = true;
        if (list1 != null && list1.size() != 0 && list2 != null && list2.size() != 0) {
            if (list1.contains(list2)) {
                return flag;
            } else {
                Iterator var4 = list2.iterator();

                while(var4.hasNext()) {
                    String element = (String)var4.next();
                    boolean b = hasElement(list1, element);
                    if (isAnd) {
                        if (!b) {
                            flag = false;
                            break;
                        }
                    } else {
                        if (b) {
                            flag = true;
                            break;
                        }

                        flag = false;
                    }
                }

                return flag;
            }
        } else {
            return false;
        }
    }

    public static boolean hasElement(List<String> list, String element) {
        if (list != null && list.size() != 0) {
            if (list.contains(element)) {
                return true;
            } else {
                Iterator it = list.iterator();

                while(it.hasNext()) {
                    String patt = (String)it.next();
                    if (hasElement(patt, element)) {
                        return true;
                    }
                }

                return false;
            }
        } else {
            return false;
        }
    }

    private static boolean hasElement(String value1, String value2) {
        if (value1 == null && value2 == null) {
            return true;
        } else if (value1 != null && value2 != null) {
            return value1.indexOf("*") == -1 ? value1.equals(value2) : Pattern.matches(value1.replaceAll("\\*", ".*"), value2);
        } else {
            return false;
        }
    }

    public static int continuousDay(List<LocalDate> dateList) {
        if (dateList != null && dateList.size() != 0) {
            dateList = (List)dateList.stream().sorted(LocalDate::compareTo).collect(Collectors.toList());
            int maxContinuousDay = 1;
            int continuousDay = 1;

            for(int i = 0; i < dateList.size() && i != dateList.size() - 1; ++i) {
                LocalDate date = (LocalDate)dateList.get(i);
                LocalDate secondDate = (LocalDate)dateList.get(i + 1);
                if (date.plusDays(1L).equals(secondDate)) {
                    ++continuousDay;
                } else {
                    if (continuousDay > maxContinuousDay) {
                        maxContinuousDay = continuousDay;
                    }

                    continuousDay = 1;
                }
            }

            return maxContinuousDay;
        } else {
            return 0;
        }
    }

    @Nullable
    public static String cleanIdentifier(@Nullable String param) {
        if (param == null) {
            return null;
        } else {
            StringBuilder paramBuilder = new StringBuilder();

            for(int i = 0; i < param.length(); ++i) {
                char c = param.charAt(i);
                if (Character.isJavaIdentifierPart(c)) {
                    paramBuilder.append(c);
                }
            }

            return paramBuilder.toString();
        }
    }

    /**
     * 占比-val/totalVal*100
     * @param val
     * @param totalVal
     * @return
     */
    public static BigDecimal getPercent(BigDecimal val, BigDecimal totalVal) {
        if (val == null || val.doubleValue()==0) {
            return BigDecimal.ZERO;
        }
        if (totalVal == null) {
            totalVal = new BigDecimal("0");
        }
        return val.divide(totalVal,4,RoundingMode.HALF_UP).divide(new BigDecimal("0.01"),2, RoundingMode.HALF_UP);
    }

    /**
     * 增长*(new-old)/old*100
     * @param oldValue
     * @param newValue
     * @return
     */
    public static BigDecimal getRate(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue == null || oldValue.doubleValue()==0) {
            return BigDecimal.ZERO;
        }
        if (newValue == null) {
            newValue = new BigDecimal("0");
        }
        return newValue.subtract(oldValue).divide(oldValue,4,RoundingMode.HALF_UP).divide(new BigDecimal("0.01"),2, RoundingMode.HALF_UP);
    }
    public static BigDecimal getRateOri(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue == null || oldValue.doubleValue()==0) {
            return BigDecimal.ZERO;
        }
        if (newValue == null) {
            newValue = new BigDecimal("0");
        }
        return newValue.subtract(oldValue).divide(oldValue,4, RoundingMode.HALF_UP);
    }

    /**
     * oldValue/newValue
     * @param oldValue
     * @param newValue
     * @return
     */
    public static BigDecimal divide(BigDecimal oldValue, BigDecimal newValue, int scale) {
        if (newValue == null || newValue.doubleValue()==0) {
            return BigDecimal.ZERO;
        }
        if (oldValue == null) {
            oldValue = new BigDecimal("0");
        }
        return oldValue.divide(newValue,scale, RoundingMode.HALF_UP);
    }

    /**
     * newValue - oldValue
     * @param oldValue
     * @param newValue
     * @return
     */
    public static BigDecimal subtract(BigDecimal newValue,BigDecimal oldValue) {
        if (oldValue == null ) {
            oldValue = new BigDecimal("0");
        }if (newValue == null ) {
            newValue = new BigDecimal("0");
        }
        return newValue.subtract(oldValue);
    }
}
