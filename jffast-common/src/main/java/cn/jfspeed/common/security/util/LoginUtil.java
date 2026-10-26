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

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jffast
 */
@Slf4j
public class LoginUtil {

    /** 角色菜单 ID 缓存的存活秒数，与 SysRoleMenuService 的权限缓存 TTL 保持同口径 */
    private static final long MENU_ID_CACHE_TTL_SECONDS = 3600L;

    @SuppressWarnings("unchecked")
    private static <T> T redisHget(String key, String field) {
        try {
            Class<?> redisUtilClass = Class.forName("cn.jfspeed.redis.util.RedisUtil");
            Object redisUtil = SpringUtil.getBean(redisUtilClass);
            return (T) redisUtilClass.getMethod("hget", String.class, String.class).invoke(redisUtil, key, field);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            log.debug("RedisUtil.hget failed", e);
            return null;
        }
    }

    /**
     * 写哈希字段，{@code ttlSeconds > 0} 时同时给整个哈希键续期。
     *
     * <p>菜单 ID 缓存必须带 TTL：绑定的权威存储在 sys_role_menu，Redis 里的字段只是加速副本。
     * 无 TTL 的字段一旦因任何路径漏删而残留就会永久命中旧绑定——权限收紧在鉴权上永远不生效，
     * 且没有自愈时间。带 TTL 后漏删最坏只影响一个周期。</p>
     */
    private static void redisHset(String key, String field, Object value, long ttlSeconds) {
        try {
            Class<?> redisUtilClass = Class.forName("cn.jfspeed.redis.util.RedisUtil");
            Object redisUtil = SpringUtil.getBean(redisUtilClass);
            if (ttlSeconds > 0L) {
                redisUtilClass.getMethod("hset", String.class, String.class, Object.class, long.class)
                        .invoke(redisUtil, key, field, value, ttlSeconds);
                return;
            }
            redisUtilClass.getMethod("hset", String.class, String.class, Object.class).invoke(redisUtil, key, field, value);
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            log.debug("RedisUtil.hset failed", e);
        }
    }

    /** 会话里的用户对象绝不携带密码/盐：任何写入会话的路径都必须先清空 */
    private static void clearCredentials(Object user) {
        try {
            user.getClass().getMethod("setPassword", String.class).invoke(user, (String) null);
            user.getClass().getMethod("setSalt", String.class).invoke(user, (String) null);
        } catch (Exception e) {
            log.warn("Failed to clear password/salt", e);
        }
    }

    public static String login(Object user) {
        clearCredentials(user);
        try {
            Object id = user.getClass().getMethod("getId").invoke(user);
            StpUtil.login(id);
            StpUtil.getSession().set("user", user);
        } catch (Exception e) {
            throw new BusinessException("登录失败: " + e.getMessage());
        }
        return StpUtil.getTokenValue();
    }

    public static void logout(String token) {
        StpUtil.logoutByTokenValue(token);
    }

    public static String getToken(HttpServletRequest request) {
        String tokenValue = StpUtil.getTokenValue();
        if (!StpUtil.isLogin()) {
            log.error("登录失效，请重新登录,uri=" + request.getRequestURI());
            throw new BusinessException(AuthErrorCode.LOGIN_EXPIRED);
        }
        return tokenValue;
    }

    public static SaTokenInfo getLogin(String token) {
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return tokenInfo;
    }

    public static String getLoginUserName() {
        LoginUser user = getLoginUser();
        return user != null ? user.getUserName() : null;
    }

    public static LoginUser getLoginUser() {
        Object userObj = getLoginSysUser();
        if (userObj == null) {
            return null;
        }
        LoginUser loginUser = new LoginUser();
        BeanUtils.copyProperties(userObj, loginUser);
        return loginUser;
    }

    /**
     * 本 JVM 的 classpath 上是否有 Sa-Token。
     * <p>
     * <b>为什么需要它</b>：本框架的 {@code jffast-starter-web} 把 {@code sa-token-core} 声明为
     * {@code provided}，因此「鉴权在网关做完、自身不需要登录态」的业务服务可以完全不引 Sa-Token。
     * 但 {@code LoginUtil} 位于 {@code jffast-common}，被所有模块共享，其字节码里引用了若干
     * Sa-Token 类型（方法签名里的 {@code SaTokenInfo}、catch 子句里的 {@code SaTokenContextException}）。
     * <p>
     * 只要这些引用出现在**被执行到的代码路径**上（典型如 {@code getLoginSysUser()}），
     * 无 Sa-Token 的服务就会抛 {@code NoClassDefFoundError}。而
     * {@code GlobalExceptionConfig} 的处理器会调用 {@link #getRequest()} 打日志 ——
     * 一旦整个 {@code LoginUtil} 因缺少类型而无法使用，**全局异常处理就会连带崩掉**，
     * 业务异常（例如「OpenAI 未配置 apiKey」）会退化成 Tomcat 的裸 500 默认错误页，
     * 调用方拿不到任何可读信息。2026-09-22 实测（无 Sa-Token 的 ai 服务调 Pro AI 接口）。
     * <p>
     * 用 {@code Class.forName(..., false, ...)} 探测（不触发初始化），失败即视为「本服务不使用 Sa-Token」，
     * 此时所有登录态查询一律返回空，语义正确：无鉴权能力的服务本来就没有登录用户。
     */
    private static final boolean SA_TOKEN_PRESENT = detectSaToken();

    private static boolean detectSaToken() {
        try {
            Class.forName("cn.dev33.satoken.stp.StpUtil", false, LoginUtil.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Object getLoginSysUser() {
        if (!SA_TOKEN_PRESENT) {
            // 本服务未引入 Sa-Token（鉴权在网关完成），永远没有登录态
            return null;
        }
        try {
            return StpUtil.getSession().get("user");
        } catch (Exception e) {
            // 无 Sa-Token 上下文（如启动 Runner、无请求的非 Web 线程）或当前请求无有效登录态（如匿名访问）时视为未登录。
            // 注意：这里**不要**再写具体 Sa-Token 异常类型——那会重新把类型引用钉进 catch 子句，
            // 让「无 Sa-Token 的服务」在类校验阶段就抛 NoClassDefFoundError。
            return null;
        }
    }

    public static LoginUser getLoginUser(String token) {
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            throw new BusinessException(AuthErrorCode.LOGIN_EXPIRED);
        }
        Object userObj = StpUtil.getSessionByLoginId(loginId).get("user");
        if (userObj == null) {
            return null;
        }
        LoginUser loginUser = new LoginUser();
        BeanUtils.copyProperties(userObj, loginUser);
        return loginUser;
    }

    public static Long getLoginUserId() {
        // 统一走 getLoginSysUser()：它带 SA_TOKEN_PRESENT 守卫与异常吞并，
        // 无 Sa-Token 的服务（sa-token-core 为 provided，如独立部署的 ai 服务）不炸 NoClassDefFoundError。
        Object userObj = getLoginSysUser();
        if (userObj == null) {
            return null;
        }
        try {
            Object id = userObj.getClass().getMethod("getId").invoke(userObj);
            return (Long) id;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Long> getAllMenuIdList() {
        return getAllMenuIdList(null);
    }

    @SuppressWarnings("unchecked")
    public static List<Long> getAllMenuIdList(List<Long> roleList) {
        if (roleList == null) {
            roleList = getRoleIdList();
        }
        List<Long> menuIdList = new ArrayList<>();

        for (Long roleId : roleList) {
            List<Long> cached = redisHget(BaseConstant.CACHE_ALL_MENU_ID_LIST, roleId + "");
            if (cached != null) {
                menuIdList.addAll(cached);
            } else {
                try {
                    Class<?> serviceClass = Class.forName("cn.jfspeed.system.service.SysRoleMenuService");
                    Object service = SpringUtil.getBean(serviceClass);
                    List<Long> menuids = (List<Long>) serviceClass.getMethod("listAllMenuIdList", Long.class).invoke(service, roleId);
                    menuIdList.addAll(menuids);
                    redisHset(BaseConstant.CACHE_ALL_MENU_ID_LIST, roleId + "", menuids, MENU_ID_CACHE_TTL_SECONDS);
                } catch (ClassNotFoundException e) {
                    log.warn("SysRoleMenuService not available");
                } catch (Exception e) {
                    log.error("Failed to get menu id list", e);
                }
            }
        }

        return menuIdList;
    }

    @SuppressWarnings("unchecked")
    public static List<String> getPermList() {
        return getPermList(null);
    }

    @SuppressWarnings("unchecked")
    public static List<String> getPermList(List<Long> roleIdList) {
        if (roleIdList == null) {
            roleIdList = getRoleIdList();
        }
        List<String> permList = new ArrayList<>();

        if (CollUtil.isNotEmpty(roleIdList)) {
            if (roleIdList.contains(BaseConstant.ROLE_ID_SUPER_ADMIN_LONG)) {
                try {
                    // 超管权限全集：SysMenuService 内部带 TTL 缓存，菜单变更时失效，
                    // 避免每次鉴权都全表扫描 sys_menu
                    Class<?> serviceClass = Class.forName("cn.jfspeed.system.service.SysMenuService");
                    Object service = SpringUtil.getBean(serviceClass);
                    List<String> perms = (List<String>) serviceClass.getMethod("listAllBtnPerm").invoke(service);
                    permList.addAll(perms);
                } catch (ClassNotFoundException e) {
                    log.warn("SysMenuService not available");
                } catch (Exception e) {
                    log.error("Failed to get perm list", e);
                }
            } else {
                // 统一走 SysRoleMenuService 的带 TTL 缓存；旧的无 TTL 哈希路径已废弃，
                // 否则角色绑定变更后权限会永久停留在旧值
                for (Long roleId : roleIdList) {
                    try {
                        Class<?> serviceClass = Class.forName("cn.jfspeed.system.service.SysRoleMenuService");
                        Object service = SpringUtil.getBean(serviceClass);
                        List<String> perms = (List<String>) serviceClass.getMethod("listAllBtnPerm", Long.class).invoke(service, roleId);
                        permList.addAll(perms);
                    } catch (ClassNotFoundException e) {
                        log.warn("SysRoleMenuService not available");
                    } catch (Exception e) {
                        log.error("Failed to get perm list", e);
                    }
                }
            }
        }

        return permList.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getRoleList() {
        Object userObj = getLoginSysUser();
        if (userObj == null) {
            return new ArrayList<>();
        }
        try {
            Object roleList = userObj.getClass().getMethod("getRoleList").invoke(userObj);
            if (roleList == null) {
                return new ArrayList<>();
            }
            List<?> roles = (List<?>) roleList;
            return roles.stream().map(role -> {
                Map<String, Object> map = new HashMap<>();
                try {
                    map.put("id", role.getClass().getMethod("getId").invoke(role));
                    map.put("roleKey", role.getClass().getMethod("getRoleKey").invoke(role));
                } catch (Exception e) {
                    log.error("Failed to extract role info", e);
                }
                return map;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get role list", e);
            return new ArrayList<>();
        }
    }

    public static List<Long> getRoleIdList() {
        return getRoleList().stream()
                .map(m -> (Long) m.get("id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static List<String> getRoleCodeList() {
        return getRoleList().stream()
                .map(m -> (String) m.get("roleKey"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 当前登录用户是否超级管理员（角色标识或内置角色 ID 命中其一即可）。
     *
     * <p><b>只认 {@link BaseConstant#ROLE_SUPER_ADMIN}（"super_admin"）或内置超管角色 ID（1L）</b>。
     *
     * <p>⚠️ 这里曾误用 {@code BaseConstant.SUPER_ADMIN}（值 {@code "admin"}）——那是**超管用户名**
     * 常量，不是角色标识；而 {@code BaseConstant.ROLE_ADMIN}（同样是 {@code "admin"}）是
     * **部门级管理员**角色的 roleKey。两者字面量相同，于是任何持有 roleKey="admin" 角色的
     * 普通用户都会被本方法判成超管，进而跳过请假单归属校验、异步任务归属过滤、角色写入校验
     * （{@code validateRoleWrite} 的 superAdmin 早退）等**授权/归属短路开关**。
     * 本仓其余三处同类判定（{@code AiConversationService} / {@code FlowAccessService} /
     * {@code SysUserController}）用的都是 {@code ROLE_SUPER_ADMIN}，本方法应与之保持一致。
     */
    public static boolean isSuperAdmin() {
        if (getLoginSysUser() == null) {
            return false;
        }
        return isSuperAdminByRole(getRoleCodeList(), getRoleIdList());
    }

    /**
     * 超管判定的纯逻辑（包内可见，供单测钉住语义）。
     *
     * <p><b>不变量</b>：只有 roleKey = {@link BaseConstant#ROLE_SUPER_ADMIN}（"super_admin"）
     * 或内置超管角色 ID（1L）才算超管。
     *
     * <p>⚠️ 历史上此处误用 {@code BaseConstant.SUPER_ADMIN}（值 "admin"，那是**超管用户名**）。
     * 由于 {@code BaseConstant.ROLE_ADMIN}（"admin"）是**部门级管理员**角色的 roleKey，
     * 字面量相同 ⇒ 持有 roleKey="admin" 角色的普通用户会被误判为超管，从而跳过
     * 请假单归属校验、异步任务归属过滤、角色写入校验等授权/归属短路开关。
     *
     * @param roleCodes 当前用户的角色标识列表（roleKey）
     * @param roleIds   当前用户的角色 ID 列表
     * @return 是否超级管理员
     */
    static boolean isSuperAdminByRole(List<String> roleCodes, List<Long> roleIds) {
        boolean byRoleKey = roleCodes != null && roleCodes.contains(BaseConstant.ROLE_SUPER_ADMIN);
        boolean byRoleId = roleIds != null && roleIds.contains(BaseConstant.ROLE_ID_SUPER_ADMIN_LONG);
        return byRoleKey || byRoleId;
    }

    public static void updateUser(Object user) {
        clearCredentials(user);
        try {
            Object id = user.getClass().getMethod("getId").invoke(user);
            StpUtil.getSessionByLoginId(id).set("user", user);
        } catch (Exception e) {
            log.error("Failed to update user session", e);
        }
    }

    public static void updateLoginUser(LoginUser loginUser) {
        Object sysUser = getLoginSysUser();
        if (sysUser == null) {
            return;
        }
        try {
            if (loginUser.getUserName() != null) {
                sysUser.getClass().getMethod("setUserName", String.class).invoke(sysUser, loginUser.getUserName());
            }
            if (loginUser.getNickName() != null) {
                sysUser.getClass().getMethod("setNickName", String.class).invoke(sysUser, loginUser.getNickName());
            }
            if (loginUser.getAvatar() != null) {
                sysUser.getClass().getMethod("setAvatar", String.class).invoke(sysUser, loginUser.getAvatar());
            }
            if (loginUser.getEmail() != null) {
                sysUser.getClass().getMethod("setEmail", String.class).invoke(sysUser, loginUser.getEmail());
            }
            if (loginUser.getPhone() != null) {
                sysUser.getClass().getMethod("setPhone", String.class).invoke(sysUser, loginUser.getPhone());
            }
            if (loginUser.getDeptId() != null) {
                sysUser.getClass().getMethod("setDeptId", Long.class).invoke(sysUser, loginUser.getDeptId());
            }
            StpUtil.getSession().set("user", sysUser);
        } catch (Exception e) {
            log.error("Failed to update login user", e);
        }
    }

    public static void refreshLoginUser() {
        Object current = getLoginSysUser();
        if (current == null) {
            return;
        }
        try {
            Object id = current.getClass().getMethod("getId").invoke(current);
            Class<?> serviceClass = Class.forName("cn.jfspeed.system.service.SysUserService");
            Object service = SpringUtil.getBean(serviceClass);
            Object fresh = serviceClass.getMethod("getById", Serializable.class).invoke(service, id);
            if (fresh != null) {
                Object roleList = current.getClass().getMethod("getRoleList").invoke(current);
                fresh.getClass().getMethod("setRoleList", List.class).invoke(fresh, roleList);
                clearCredentials(fresh);
                StpUtil.getSession().set("user", fresh);
            }
        } catch (ClassNotFoundException e) {
            log.warn("SysUserService not available");
        } catch (Exception e) {
            log.error("Failed to refresh login user", e);
        }
    }

    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return attrs.getRequest();
    }
}
