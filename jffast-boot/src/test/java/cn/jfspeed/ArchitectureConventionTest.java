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
package cn.jfspeed;

import cn.jfspeed.common.data.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintStream;
import java.util.Collection;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构规范门禁：把「开发规范.md」中最容易违反的约束固化成会 FAIL 的测试。
 * <p>
 * 设计动机：AI 与新人靠模式匹配写代码，800 行文档的遵守率远低于一条跑一次就红的测试。
 * 与 {@link EndpointPermissionAnnotationTest}（权限注解硬门禁）同属「规范即测试」体系：
 * <ul>
 *   <li>Controller 的 @RequestBody 不得接收 entity 包类型（规范 §9，防 mass assignment）</li>
 *   <li>@TableName 实体必须继承 BaseEntity（规范 §9，关联表例外）</li>
 *   <li>业务代码禁止 System.out（规范 §14，统一走 log）</li>
 *   <li>业务代码禁止 printStackTrace()（规范 §14，堆栈要走 log.error(msg, e)）</li>
 *   <li>Controller 不得直接依赖 Mapper（规范 §9，一律经 Service，避免绕过数据权限）</li>
 *   <li>@RestController 必须标注 @Tag（OpenAPI 文档完备）</li>
 * </ul>
 * 新增约束时优先加到这里，而不是只写进文档。
 *
 * @author jffast
 */
class ArchitectureConventionTest {

    private static final ClassFileImporter IMPORTER =
            new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests());

    /**
     * Controller 不直接接收 Entity：Entity 继承 BaseEntity（id/tenantId/deptId/审计字段可被伪造），
     * 直收即 mass assignment。入参一律走 domain/dto 下的 Request DTO。
     * 只校验标注了 {@link RequestBody} 的写路径参数；@ModelAttribute 查询对象不在此列。
     */
    @Test
    void controllerWriteEndpointsMustNotTakeEntities() {
        ArchRuleDefinition.classes()
                .that().areAnnotatedWith(RestController.class)
                .should(notAcceptEntityRequestBody())
                .because("开发规范 §9：Controller 不直接接收 Entity（见 docs/开发规范.md §9），"
                        + "入参请使用 domain/dto 下的 *Request 并配合 @Valid")
                .check(IMPORTER.importPackages("cn.jfspeed"));
    }

    /**
     * 统一日志出口：System.out 绕过日志框架（无 TraceId、无脱敏、无级别控制）。
     * 例外：RsaKeyGenerator（一次性运维工具，无 Spring 环境）、CodeGeneratorCLI（CLI 的
     * stdout 即交互界面）。新增例外时必须在此登记理由。
     */
    @Test
    void businessCodeMustNotUseSystemOut() {
        noClasses()
                .that().resideInAPackage("cn.jfspeed..")
                .and().doNotHaveFullyQualifiedName("cn.jfspeed.common.security.util.RsaKeyGenerator")
                .and().doNotHaveFullyQualifiedName("cn.jfspeed.common.core.util.CodeGeneratorCLI")
                .should().dependOnClassesThat().areAssignableTo(PrintStream.class)
                .because("开发规范 §14：禁止 System.out，统一使用 @Slf4j 日志")
                .check(IMPORTER.importPackages("cn.jfspeed"));
    }

    /**
     * 禁止直接打印堆栈：printStackTrace() 同样绕过日志框架（堆栈无处可检索、无 TraceId/脱敏）。
     * 需要堆栈时用 {@code log.error("msg", e)}。注意此处只匹配无参重载；
     * {@code printStackTrace(PrintWriter)}（如 CommonUtil 把堆栈转字符串）是正常工具用法。
     */
    @Test
    void businessCodeMustNotPrintStackTrace() {
        noClasses()
                .that().resideInAPackage("cn.jfspeed..")
                .should().callMethod(Throwable.class, "printStackTrace")
                .because("开发规范 §14：禁止 printStackTrace()，请用 @Slf4j 的 log.error(msg, e)")
                .check(IMPORTER.importPackages("cn.jfspeed"));
    }

    /**
     * 实体统一继承 BaseEntity：由它提供 id 与审计字段的 MyBatis-Plus 填充/映射。
     * 自行声明这些列会与父类映射漂移。例外：纯关联表（sys_user_role 等）无主键 id，
     * 不继承——继承反而会映射出库中不存在的审计列。
     */
    @Test
    void tableMappedEntitiesMustExtendBaseEntity() {
        ArchRuleDefinition.classes()
                .that().areAnnotatedWith(TableName.class)
                .should(extendBaseEntityUnlessRelationTable())
                .because("开发规范 §9：@TableName 实体应继承 BaseEntity（纯关联表无 id 为例外）")
                .check(IMPORTER.importPackages("cn.jfspeed"));
    }

    /**
     * Controller 只经 Service 访问数据：直接依赖 Mapper 会绕过 Service 的事务与
     * 租户/数据权限（tenant/data-scope）拦截。
     */
    @Test
    void controllersMustNotDependOnMappers() {
        noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..mapper..")
                .because("开发规范 §9：Controller 不得直接依赖 Mapper，一律经 Service")
                .check(IMPORTER.importPackages("cn.jfspeed"));
    }

    /** 接口文档完备：每个对外 Controller 都要有分组名（@Tag，供接口文档分组展示）。 */
    @Test
    void restControllersMustBeTagged() {
        ArchRuleDefinition.classes()
                .that().areAnnotatedWith(RestController.class)
                .should().beAnnotatedWith(Tag.class)
                .because("开发规范 §9：所有 Controller 必须标注 @Tag 供接口文档分组展示")
                .check(IMPORTER.importPackages("cn.jfspeed"));
    }

    /**
     * 有主键 id 的 @TableName 实体必须继承 BaseEntity；无 id 的纯关联表放行。
     */
    private static ArchCondition<JavaClass> extendBaseEntityUnlessRelationTable() {
        return new ArchCondition<>("继承 BaseEntity（纯关联表除外）") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                if (clazz.isAssignableTo(BaseEntity.class)) {
                    return;
                }
                boolean ownsId = clazz.getAllFields().stream().anyMatch(field -> "id".equals(field.getName()));
                if (ownsId) {
                    events.add(SimpleConditionEvent.violated(clazz, clazz.getName()
                            + " 标注 @TableName 且有主键 id，却未继承 BaseEntity"
                            + "（审计字段填充/映射会缺失；若确为关联表请勿声明 id）"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notAcceptEntityRequestBody() {
        return new ArchCondition<>("不接收 entity 包类型作为 @RequestBody 入参") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                for (JavaMethod method : clazz.getMethods()) {
                    for (JavaParameter parameter : method.getParameters()) {
                        if (!parameter.isAnnotatedWith(RequestBody.class)) {
                            continue;
                        }
                        JavaClass rawType = parameter.getRawType();
                        if (rawType.getPackageName().contains(".entity.")) {
                            String message = "%s#%s 的 @RequestBody 参数 %s 来自 entity 包，"
                                    .formatted(clazz.getName(), method.getName(), rawType.getName())
                                    + "违反开发规范 §9（请改用 *Request DTO + BeanUtil.copyProperties）";
                            events.add(SimpleConditionEvent.violated(method, message));
                        }
                    }
                }
            }
        };
    }
}
