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
package cn.jfspeed.flow.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import org.flowable.engine.repository.ProcessDefinition;

import java.io.Serializable;

/**
 * 流程定义（对外视图对象）。
 *
 * <p><b>为什么要有这个 VO，而不是直接把 Flowable 的实体丢给前端</b>：
 * {@code ProcessDefinitionEntityImpl} 上挂着一批<b>惰性</b>能力（{@code identityLinks}、
 * {@code processDefinition} 等），脱离引擎上下文后会 NPE。序列化它会直接炸在整个响应体上：
 * <pre>
 * HttpMessageNotWritableException: Cannot invoke
 *   ProcessDefinitionEntityImpl.getIdentityLinkServiceConfiguration() because
 *   "processEngineConfiguration" is null
 * through reference chain: R["data"]->HashMap["list"]->ArrayList[0]
 *   ->ProcessDefinitionEntityImpl["identityLinks"]
 * </pre>
 * 表现为 {@code GET /flow/process/definition/page} 返回 {@code code=500}、前端「流程定义」页空白。
 * 端到端实测才暴露（单元测试用 mock 的 service，照不出来）。
 *
 * <p>同理，**任何把 Flowable 实体直接返回给前端的接口都要先映射**。字段与前端表格列一一对应
 * （{@code pages/flow/definition/index.vue} 的 columns：name/key/version/category/suspended + 操作用的 id/deploymentId）。
 *
 * @param id                   流程定义 ID（挂起/激活/查 XML/查图都要用）
 * @param key                  流程标识（BPMN 里的 process id）
 * @param name                 流程名称
 * @param version              版本号
 * @param category             分类（对应 wf_category.category_code）
 * @param suspended            是否已挂起
 * @param deploymentId         所属部署 ID（删除部署用）
 * @param resourceName         BPMN 资源文件名
 * @param diagramResourceName  流程图资源文件名
 * @param description          描述
 * @author jffast
 */
@Schema(description = "流程定义")
public record WfProcessDefinitionVO(
        @Schema(description = "流程定义ID") String id,
        @Schema(description = "流程标识") String key,
        @Schema(description = "流程名称") String name,
        @Schema(description = "版本号") Integer version,
        @Schema(description = "分类") String category,
        @Schema(description = "是否已挂起") boolean suspended,
        @Schema(description = "部署ID") String deploymentId,
        @Schema(description = "BPMN 资源名") String resourceName,
        @Schema(description = "流程图资源名") String diagramResourceName,
        @Schema(description = "描述") String description
) implements Serializable {

    /** 由 Flowable 实体映射为对外 VO（只读取安全字段，不触碰惰性属性）。 */
    public static WfProcessDefinitionVO from(ProcessDefinition definition) {
        if (definition == null) {
            return null;
        }
        return new WfProcessDefinitionVO(
                definition.getId(),
                definition.getKey(),
                definition.getName(),
                definition.getVersion(),
                definition.getCategory(),
                definition.isSuspended(),
                definition.getDeploymentId(),
                definition.getResourceName(),
                definition.getDiagramResourceName(),
                definition.getDescription()
        );
    }
}
