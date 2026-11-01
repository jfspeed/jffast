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
package cn.jfspeed.security.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sa-Token 拦截器配置。
 * <p>
 * 本类仅负责登录校验；具体接口权限由 Controller 方法上的 {@code @SaCheckPermission}
 * 注解通过 Sa-Token AOP 完成，避免在拦截器里用字符串 contains 做模糊匹配导致权限绕过。
 * <p>
 * 免登录白名单 = 内置基础设施路径 + {@code jffast.security.auth-excludes} 追加项。
 * 框架不预置业务向的宽泛匿名前缀（如 /oauth2/**、/open/**），由启用对应能力的
 * 应用按需声明，且需自行保证独立鉴权。
 * <p>
 * <b>服务间调用：</b>分布式部署下，服务→服务调用可能不透传 {@code token}，
 * 只透传经共享密钥签名的身份头，因此打到"需登录"接口会抛 {@code NotLoginException}。
 * 若容器里存在 {@link InternalCallDetector} 且它判定当前请求为"已验签的内部调用"，
 * 本类跳过 {@code StpUtil.checkLogin()}（信任边界＝共享密钥签名，详见该接口的 Javadoc）。
 * 单体形态不提供该实现，行为不变。
 *
 * @author jffast
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SaTokenConfig implements WebMvcConfigurer {

    // 免登录即可访问的路径。
    // 注意：/sys/getUserInfo **不在**此列——它需要登录态，放行会让未登录请求走到 Controller
    // 里 `return R.error(AuthErrorCode.NOT_LOGIN...)`（HTTP 200 + body code 401），
    // 前端仍拿不到 HTTP 401；交给下方 StpUtil.checkLogin() 抛 NotLoginException，
    // 由 SaTokenExceptionConfig 统一返回 HTTP 401。
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/sys/login", "/sys/getCode", "/sys/needCaptcha", "/sys/phoneLogin", "/sys/sendPhoneCode",
            "/sys/logout"
    );

    // 仅保留本框架真实对外提供的基础设施路径；业务向匿名路径由应用按需声明（见类注释）。
    // /upload/**：本地文件存储访问映射（jffast.file.storage.local.access-path）；
    // /ws/**：WebSocket 握手路径（jffast.web.websocket.path）；/error：Spring 错误页分发。
    private static final List<String> AUTH_WHITELIST = Arrays.asList(
            "/upload/**", "/error", "/ws/**"
    );

    /**
     * 接口文档路径：仅在 {@code jffast.swagger.enabled=true}（默认）时加入免登录白名单。
     * <p>
     * Spring Boot 4 起文档 UI 为 springdoc 原生（{@code /swagger-ui.html} → {@code /swagger-ui/**}）；
     * {@code /doc.html} 为历史 Knife4j 入口，当前 404 但保留在白名单中，
     * 以便将来官方适配后回切时无需再动此处。
     */
    private static final List<String> SWAGGER_WHITELIST = Arrays.asList(
            "/swagger-ui.html", "/swagger-ui/**",
            "/doc.html", "/webjars/**", "/v3/api-docs/swagger-config", "/v3/api-docs/**"
    );

    private final SecurityProperties securityProperties;

    /** 接口文档是否开启（与 swagger 开关同源：jffast.swagger.enabled，默认 true） */
    private final boolean swaggerEnabled;

    /** 可选的「已验签的内部调用」识别器；单体形态下为空（ObjectProvider 永远可注入，无需 null 判断容器） */
    private final ObjectProvider<InternalCallDetector> internalCallDetector;

    public SaTokenConfig(SecurityProperties securityProperties,
                         ObjectProvider<InternalCallDetector> internalCallDetector,
                         @Value("${jffast.swagger.enabled:true}") boolean swaggerEnabled) {
        this.securityProperties = securityProperties;
        this.internalCallDetector = internalCallDetector;
        this.swaggerEnabled = swaggerEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            String path = SaHolder.getRequest().getRequestPath();

            if (PUBLIC_PATHS.contains(path)) {
                return;
            }

            // 服务间调用：已验签的内部调用视为已认证。
            // 注意判定必须发生在 checkLogin() 之前，且实现只能依据共享密钥签名这类不可伪造的证据。
            InternalCallDetector detector = internalCallDetector.getIfAvailable();
            if (detector != null && detector.isTrustedInternalCall()) {
                return;
            }

            StpUtil.checkLogin();
        })).addPathPatterns("/**")
           .excludePathPatterns(excludePaths());
    }

    /** 内置白名单 + 配置追加项合并（追加项在前，便于业务覆盖冲突时优先生效） */
    private List<String> excludePaths() {
        List<String> excludes = new ArrayList<>();
        if (securityProperties.getAuthExcludes() != null) {
            excludes.addAll(securityProperties.getAuthExcludes());
        }
        excludes.addAll(AUTH_WHITELIST);
        if (swaggerEnabled) {
            excludes.addAll(SWAGGER_WHITELIST);
        }
        return excludes;
    }
}
