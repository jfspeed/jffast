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
package cn.jfspeed.system.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.core.config.JfFastProperties;
import cn.jfspeed.common.core.util.IdUtils;
import cn.jfspeed.common.web.util.ClientIpResolver;
import cn.jfspeed.system.domain.dto.SysLoginRequest;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.security.spi.LoginPolicyProvider;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.security.util.RSAUtil;
import cn.jfspeed.common.security.util.EncryptionUtil;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.core.util.TreeUtil;
import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.system.constant.SystemErrorCode;
import cn.jfspeed.system.domain.dto.RouterVo;
import cn.jfspeed.system.domain.vo.SysUserVO;
import cn.jfspeed.system.entity.SysLog;
import cn.jfspeed.system.entity.SysMenu;
import cn.jfspeed.system.entity.SysSafe;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.service.SysLogService;
import cn.jfspeed.system.service.SysMenuService;
import cn.jfspeed.system.service.SysSafeService;
import cn.jfspeed.system.service.SysUserService;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.security.ratelimit.IpRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@RestController
@RequestMapping("/sys")
@Tag(name = "登录管理")
// 匿名端点：验证码 / 登录 / 手机号登录 / 登出在登录前必须可达 —— 由 Sa-Token 登录态校验兜底，
// 故不加 {@code @SaCheckPermission}（白名单与理由见 jffast-boot 的 EndpointPermissionAnnotationTest）。
/**
 * @author jffast
 */
public class SysLoginController {

    @Resource
    private SysSafeService sysSafeService;
    @Resource
    private SysUserService sysUserService;
    @Resource
    private SysLogService sysLogService;
    @Resource
    private SysMenuService sysMenuService;
    @Resource
    private JfFastProperties rsaProperties;

    /**
     * 登录安全策略扩展点（可选）：可插拔安全策略 Starter 提供实现后生效，
     * Base 单独运行时为空实现，行为与改造前一致。
     */
    private final ObjectProvider<LoginPolicyProvider> loginPolicyProvider;

    /**
     * 按客户端 IP 的独立限流（可选）：默认注册，覆盖登录与验证码两个场景。
     * 未装配安全 Starter 时 getIfAvailable() 返回 null，两处调用自动跳过。
     */
    private final ObjectProvider<IpRateLimiter> ipRateLimiter;

    /**
     * 真实客户端 IP 解析（安全场景专用；与 {@link IpRateLimiter} 同口径的可选依赖）。
     * <p>
     * 这里必须用 {@link ClientIpResolver} 而不是 {@code cn.jfspeed.common.web.util.IPUtil#getIpAddress}：
     * 后者取 {@code X-Forwarded-For} 首跳，而该头客户端可任意伪造，写进审计日志的
     * 来源 IP 就能被污染（既能让攻击者隐身，也能把锅甩给别人）。
     * <p>
     * 用 {@link ObjectProvider} 而非硬注入：安全 Starter 声明自己为可选（允许「只打日志、
     * 不装配鉴权」的部署形态），未注册 Bean 时回落到「不信任任何转发头」的空实例，
     * 保证本类在任何装配组合下都能实例化。
     */
    private final ClientIpResolver clientIpResolver;

    public SysLoginController(ObjectProvider<LoginPolicyProvider> loginPolicyProvider,
                              ObjectProvider<IpRateLimiter> ipRateLimiter,
                              ObjectProvider<ClientIpResolver> clientIpResolver) {
        this.loginPolicyProvider = loginPolicyProvider;
        this.ipRateLimiter = ipRateLimiter;
        this.clientIpResolver = clientIpResolver.getIfAvailable(() -> new ClientIpResolver(java.util.List.of()));
    }

    private static final int DEFAULT_ERROR_COUNT = 3;

    private Map<String,Object> userLogin(SysUser user,SysLog sysLog,SysSafe safe,String info){
        sysUserService.setDataScope(user);
        String token = LoginUtil.login(user);

        // 会话对象始终不带凭据（LoginUtil.login 内部已清空，这里再兜一次）
        user.setPassword(null);
        user.setSalt(null);
        Map<String, Object> map = new HashMap<>();
        map.put("token",token);
        // 出参走 VO 白名单：password / salt / dataScope 等内部字段不再随响应外泄
        map.put("user", SysUserVO.from(user));

        // 密码过期检查：pwdCycle > 0 时启用，单位天
        if (safe.getPwdCycle() != null && safe.getPwdCycle() > 0 && user.getUpdatePwdTime() != null) {
            long daysBetween = java.time.Duration.between(
                    user.getUpdatePwdTime(), java.time.LocalDateTime.now()).toDays();
            if (daysBetween >= safe.getPwdCycle()) {
                map.put("passwordExpired", true);
                map.put("passwordExpiredMsg", "密码已过期，请修改密码");
            }
        }

        if (user.getTenantId() != null) {
            map.put("tenantId", user.getTenantId());
        }

        sysLog.setInfo(info+"登录成功");
        sysLogService.save(sysLog);
        return map;
    }

    @GetMapping("/getUserInfo")
    @Operation(summary = "获取当前登录用户信息（菜单+权限）")
    public R getUserInfo() {
        SysUser user = (SysUser) LoginUtil.getLoginSysUser();
        if (user == null) {
            return R.error(AuthErrorCode.NOT_LOGIN.getCode(), AuthErrorCode.NOT_LOGIN.getMsg());
        }

        Map<String, Object> map = new HashMap<>();
        map.put("user", SysUserVO.from(user));

        List<Long> roleIdList = LoginUtil.getRoleIdList();
        map.put("permissions", LoginUtil.getPermList(roleIdList));

        boolean isSuperAdmin = roleIdList != null && roleIdList.stream()
                .anyMatch(id -> id.equals(BaseConstant.ROLE_ID_SUPER_ADMIN_LONG));
        List<SysMenu> menuList;
        if (isSuperAdmin) {
            menuList = sysMenuService.selectMenuList(new HashMap<>());
        } else if (roleIdList == null || roleIdList.isEmpty()) {
            // 无角色 = 无菜单（fail-closed）
            menuList = List.of();
        } else {
            // 多角色取并集
            menuList = sysMenuService.selectMenuListByRoleIds(roleIdList);
        }
        menuList = menuList.stream()
                .filter(m -> !"F".equals(m.getType()))
                .collect(Collectors.toList());
        List<SysMenu> tree = TreeUtil.buildTreeIsRoot(menuList, BaseConstant.PARENT_ID);
        List<RouterVo> routers = sysMenuService.buildMenus(tree);
        map.put("menuRoutes", routers);

        if (user.getTenantId() != null) {
            map.put("tenantId", user.getTenantId());
        }

        return R.success(map);
    }

    @PostMapping("/login")
    @Operation(summary = "用户名密码登录")
    @Parameter(name = "loginDTO", description = "登录用户对象")
    // 登录是全站最该限流的接口，此前恰好是唯一没有限流的敏感接口。
    // 失败锁定本身已有防护，但缺少频率限制时仍可被低成本暴破并放大 Redis 写入。
    @RateLimit(value = "strictApi")
    public R login(@RequestBody @Validated SysLoginRequest loginDTO, HttpServletRequest request){
        String userName = loginDTO.getUserName();
        String password = loginDTO.getPassword();
        try {
            // per-IP 限流必须最先做：它是唯一「按来源分桶」的一层，放在取密码/验密之前
            // 才能挡住撞库本身（而不是挡住撞库的后果）。全局桶 @RateLimit 由 AOP 在外层兜总量。
            IpRateLimiter ipLimiter = ipRateLimiter.getIfAvailable();
            if (ipLimiter != null) {
                ipLimiter.check(request, IpRateLimiter.SCENE_LOGIN);
            }

            // 登录安全策略前置校验（IP 白名单 / 登录频率限制）；未装配策略时为空实现
            LoginPolicyProvider policy = loginPolicyProvider.getIfAvailable();
            if (policy != null) {
                policy.checkLoginAllowed(userName, request);
            }

            // 检查是否需要验证码（登录失败 >= 2 次时需要）
            if (currentErrorNum(BaseConstant.CACHE_ERROR_COUNT + userName) >= 2) {
                String code = loginDTO.getCode();
                String key = loginDTO.getKey();
                if (StrUtil.isBlank(code) || StrUtil.isBlank(key)) {
                    return R.error(SystemErrorCode.CAPTCHA_REQUIRED.getCode(), SystemErrorCode.CAPTCHA_REQUIRED.getMsg());
                }
                String cachedCode = RedisUtil.get(BaseConstant.CACHE_VER_CODE+key);
                if (cachedCode == null) {
                    return R.error(SystemErrorCode.CAPTCHA_EXPIRED.getCode(), SystemErrorCode.CAPTCHA_EXPIRED.getMsg());
                }
                if (!code.equalsIgnoreCase(cachedCode)) {
                    return R.error(SystemErrorCode.CAPTCHA_INVALID.getCode(), SystemErrorCode.CAPTCHA_INVALID.getMsg());
                }
                // 验证码使用后立即删除，防止重放
                RedisUtil.del(BaseConstant.CACHE_VER_CODE+key);
            }

            // 用带时间戳校验的解密：拒绝过期的登录报文（防重放）
            password = RSAUtil.decryptPwdWithTimestamp(password);
            // sys_safe 是单行策略表；只导入了 schema 基线而未导入 init-data 时表为空，
            // 直接 list().get(0) 会抛 IndexOutOfBoundsException，表现为登录接口 500。
            // 取不到策略时退回"零策略"实例：不限制、不锁定，优先保证可用。
            SysSafe safe = sysSafeService.list().stream().findFirst().orElseGet(SysSafe::new);
            SysUser user = passwordErrorNum(userName, password, safe, request);
            SysLog sysLog = new SysLog(clientIpResolver.resolve(request),"用户登录",request.getRequestURI());
            sysLog.setId(IdUtils.snowflakeShort());
            sysLog.setCreateBy(user.getId());
            sysLog.setState("成功");
            Map<String,Object> map = userLogin(user,sysLog,safe,userName);
            return R.success(map);
        } catch (BusinessException e) {
            SysUser user = findUserQuietly(userName);
            SysLog sysLog = new SysLog(clientIpResolver.resolve(request),"用户登录",request.getRequestURI());
            sysLog.setId(IdUtils.snowflakeShort());
            sysLog.setCreateBy(user != null ? user.getId() : null);
            sysLog.setInfo("用户名："+userName+"，异常信息："+e.getMessage());
            sysLog.setState("失败");
            sysLogService.save(sysLog);
            return R.error(e.getCode(),e.getMessage());
        }catch (Exception e) {
            SysUser user = findUserQuietly(userName);
            SysLog sysLog = new SysLog(clientIpResolver.resolve(request),"用户登录",request.getRequestURI());
            sysLog.setId(IdUtils.snowflakeShort());
            sysLog.setCreateBy(user != null ? user.getId() : null);
            sysLog.setInfo("用户名："+userName+"，异常信息："+e.getMessage());
            sysLog.setState("失败");
            sysLogService.save(sysLog);
            log.error("",e);
            return R.error(BaseConstant.UNKNOWN_EXCEPTION);
        }
    }

    @GetMapping(value = "/getCode")
    @Operation(summary = "获取图形验证码")
    public void getCode(@RequestParam String key, HttpServletRequest request, HttpServletResponse response) {
        // 该接口匿名可达且每次调用都往 Redis 写一个带 TTL 的 key（key 由前端传入），
        // 不限流就是零成本的 Redis 写入放大点。必须在碰 response 之前判定，
        // 否则响应头已写出、再抛异常只会得到半个响应。
        IpRateLimiter ipLimiter = ipRateLimiter.getIfAvailable();
        if (ipLimiter != null) {
            ipLimiter.check(request, IpRateLimiter.SCENE_CAPTCHA);
        }
        try {
            response.setHeader("Pragma", "No-cache");
            response.setHeader("Cache-Control", "no-cache");
            response.setDateHeader("Expires", 0);
            response.setContentType("image/jpeg");

            RandomGenerator randomGenerator = new RandomGenerator(BaseConstant.CACHE_CAPTCHA_CHARS, 4);
            LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(100, 42,4,50);
            lineCaptcha.setGenerator(randomGenerator);
            BufferedImage image = lineCaptcha.getImage();
            OutputStream out = response.getOutputStream();
            // 以前端传入的 key 存储，有效期 60 秒
            RedisUtil.set(BaseConstant.CACHE_VER_CODE+key, lineCaptcha.getCode(), 60);
            ImageIO.write(image, "png", out);
        } catch (Exception e) {
            log.error("",e);
        }
    }

    @GetMapping("/needCaptcha")
    @Operation(summary = "检查是否需要验证码（登录失败超过阈值时需要）")
    public R needCaptcha(@RequestParam String userName, HttpServletRequest request) {
        // 该接口匿名可达且 userName 由调用方自由指定：不加限制时可被用来批量轮询，
        // 把布尔返回值当作「某账号是否已被连续尝试」的生存探测。
        IpRateLimiter ipLimiter = ipRateLimiter.getIfAvailable();
        if (ipLimiter != null) {
            ipLimiter.check(request, IpRateLimiter.SCENE_CAPTCHA_CHECK);
        }
        boolean need = currentErrorNum(BaseConstant.CACHE_ERROR_COUNT + userName) >= 2;
        return R.success(need);
    }

    @PostMapping("/phoneLogin")
    @Operation(summary = "手机号码和短信验证码登录（未启用）")
    public R phoneLogin(@RequestParam String phone, @RequestParam String code, HttpServletRequest request) {
        // 与 sendPhoneCode 一致：Base 未内置短信服务商，半残实现不可达成功路径，统一返回中性未启用
        return R.error("短信登录未启用：请在配置中接入短信服务商");
    }

    @GetMapping(value = "/sendPhoneCode")
    @Operation(summary = "发送手机短信验证码")
    public R sendPhoneCode(String phone) {
        return R.error("短信登录未启用：请在配置中接入短信服务商");
    }

    @SLog(title = "退出登录")
    @DeleteMapping("/logout")
    @Operation(summary = "退出登录")
    public R logout(HttpServletRequest request){
        String token = request.getHeader(BaseConstant.TOKEN_HEADER);
        LoginUtil.logout(token);
        return R.success("退出登录成功");
    }

    /**
     * 下发 RSA 公钥，供前端加密登录 / 改密请求中的密码明文。
     * <p>
     * 公钥本身不是机密，但必须保证「前端用于加密的公钥」与「后端用于解密的私钥」是同一对。
     * dev / test 下密钥由 RsaKeyInitializer 在启动时生成（每次启动都不同），前端无法预先内置，
     * 只能动态获取；因此本端点必须匿名可达，已登记在 {@code jffast.security.auth-excludes}。
     */
    @GetMapping("/rsaPublicKey")
    @Operation(summary = "获取 RSA 公钥（登录前可用，用于加密密码）")
    public R rsaPublicKey() {
        String publicKey = rsaProperties.getPublicKey();
        if (StrUtil.isBlank(publicKey)) {
            return R.error(SystemErrorCode.RSA_PUBLIC_KEY_MISSING.getCode(), "RSA公钥未配置");
        }
        return R.success(publicKey);
    }

    /**
     * 失败计数是否已触发锁定。
     *
     * <p>口径必须是「达到或超过」，不能是「刚好等于」：hincr 在并发下会跳号，运维把阈值改小也会让
     * 存量计数直接越过上限。用 {@code ==} 判等时，这两条路径都永不命中，锁定提示与计数重置双双失效，
     * 表现是计数单调增长、每次失败都把解锁时间往后推——合法用户被永久锁死，文案还会算出
     * 「剩余次数: -3」。
     */
    static boolean reachedLimit(int errorNum, int maxErrorCount) {
        return errorNum >= maxErrorCount;
    }

    /**
     * 读取当前登录失败次数；没有计数返回 0。
     *
     * <p>刻意不用 {@code RedisUtil.hasKey} 判存在：hasKey 把连接异常吞成 false，于是
     * 「Redis 抖一下」就等于「本轮不需要验证码」，而暴破正好挑这一秒发起。以 hget 的结果为准后，
     * 读不到字段就是真没有计数，连不上 Redis 则由 hget 抛出、由登录接口的外层 catch 转成失败，
     * 防护门槛不会因为基础设施抖动而失效开放。
     */
    private int currentErrorNum(String countKey) {
        Object errorNum = RedisUtil.hget(countKey, "errorNum");
        return errorNum == null ? 0 : Integer.parseInt(errorNum.toString());
    }

    private boolean lockedUser(long currentTime, String userName, int maxErrorCount){
        String countKey = BaseConstant.CACHE_ERROR_COUNT + userName;
        Object errorNum = RedisUtil.hget(countKey, "errorNum");
        Object loginTime = RedisUtil.hget(countKey, "loginTime");
        // 计数尚未建立（或只剩半个字段）时不算锁定，交给后面的分支重新起算
        if (errorNum == null || loginTime == null) {
            return false;
        }
        int i = Integer.parseInt(errorNum.toString());
        long unlockAt = Long.parseLong(loginTime.toString());
        if (reachedLimit(i, maxErrorCount) && currentTime < unlockAt) {
            Duration between = LocalDateTimeUtil.between(LocalDateTimeUtil.of(currentTime), LocalDateTimeUtil.of(unlockAt));
            throw new BusinessException(SystemErrorCode.ACCOUNT_LOCKED.getCode(),"账号锁定中，还没到允许登录的时间，请"+between.toMinutes()+"分钟后再尝试");
        }
        return true;
    }

    /**
     * 静默查用户：登录失败记录日志时用，用户不存在返回 null 而不抛异常。
     *
     * <p>必须兜住所有 RuntimeException，而不只是 BusinessException：本方法会在
     * catch 分支里被再次调用，一旦抛出未捕获的运行时异常就会逃出外层 catch，
     * 客户端收到裸 500，且 sys_log 里没有这次登录的审计记录。
     */
    private SysUser findUserQuietly(String userName) {
        try {
            return sysUserService.getUser(null, userName, null);
        } catch (BusinessException e) {
            return null;
        } catch (RuntimeException e) {
            // 数据异常（如存量库里有重名/重号用户）不该让登录变成裸 500；
            // 按「查不到用户」处理，口径与上面一致。
            log.error("登录失败记录日志时查询用户异常，userName={}", userName, e);
            return null;
        }
    }

    private SysUser passwordErrorNum(String userName,String password,SysSafe sysSafe,HttpServletRequest request) throws InvalidKeySpecException, NoSuchAlgorithmException {
        // 用户不存在与密码错误走同一失败计数/锁定/文案路径，避免账号枚举
        SysUser user = findUserQuietly(userName);
        boolean authenticate = user != null && EncryptionUtil.verifyPassword(password, user.getPassword());
        LoginPolicyProvider policy = loginPolicyProvider.getIfAvailable();
        if (authenticate) {
            // 密码校验通过后再拦停用账号（state=2），避免暴露账号是否存在
            if (user.getState() != null && user.getState() == 2) {
                throw new BusinessException(SystemErrorCode.ACCOUNT_DISABLED.getCode(), "账号已停用，请联系管理员");
            }
            RedisUtil.del(BaseConstant.CACHE_ERROR_COUNT+userName);
            // 策略回调：清零失败计数、记录成功登录等
            if (policy != null) {
                policy.onLoginSuccess(user.getId(), userName, request);
            }
        } else {
            // 策略回调：按可配置阈值累加失败次数并触发锁定
            if (policy != null) {
                policy.onLoginFailure(userName, request);
            }
            long currentTime = System.currentTimeMillis();

            int maxErrorCount = DEFAULT_ERROR_COUNT;
            String str = "15";
            long timeStamp = System.currentTimeMillis()+900000;
            // pwdLoginLimit 类型为 Integer 且该列可空：用 == 与字面量比较会自动拆箱，
            // 列值为 NULL 时抛 NPE 直接打断登录流程。改用 equals 判等。
            if (Integer.valueOf(1).equals(sysSafe.getPwdLoginLimit())){
                maxErrorCount = 5;
                str = "30";
                timeStamp = System.currentTimeMillis()+1800000;
            }

            boolean flag = lockedUser(currentTime, userName, maxErrorCount);
            String countKey = BaseConstant.CACHE_ERROR_COUNT + userName;
            // 计数键的存活时间 = 锁定窗口：让计数随窗口自然过期，而不是等一次成功登录才清
            long countTtlSeconds = (timeStamp - currentTime) / 1000;
            int i;
            if (!flag) {
                Map<String,Object> map = new HashMap<>();
                map.put("errorNum",1);
                map.put("loginTime",timeStamp);
                RedisUtil.hmset(countKey, map, countTtlSeconds);
                i = 1;
            } else {
                int previous = currentErrorNum(countKey);
                if (reachedLimit(previous, maxErrorCount)) {
                    // 走到这里说明上一轮已达上限且锁定窗口已过（lockedUser 未抛异常）：重新起算，
                    // 而不是在上次的基础上继续加——否则计数只增不减，用户永远解不开。
                    RedisUtil.hset(countKey, "errorNum", 1);
                    i = 1;
                } else {
                    RedisUtil.hincr(countKey, "errorNum", 1);
                    i = previous + 1;
                }
                RedisUtil.hset(countKey, "loginTime", timeStamp);
                RedisUtil.expire(countKey, countTtlSeconds);
            }
            if (reachedLimit(i, maxErrorCount)){
                throw new BusinessException(SystemErrorCode.ACCOUNT_LOCKED.getCode(),"您的密码已错误"+maxErrorCount+"次，现已被锁定，"+str+"分钟后再尝试");
            }
            throw new BusinessException(SystemErrorCode.PASSWORD_ERROR.getCode(),"用户名或密码错误，总登录次数"+maxErrorCount+"次，剩余次数: " + (maxErrorCount-i));
        }
        return user;
    }
}
