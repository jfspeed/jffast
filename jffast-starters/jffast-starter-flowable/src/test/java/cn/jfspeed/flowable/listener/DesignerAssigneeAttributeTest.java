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
package cn.jfspeed.flowable.listener;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 设计器产出 XML（bpmn-js + flowable moddle 描述符）在引擎侧的可解析性契约：
 * {@code flowable:assigneeType/assigneeValue} 作为 UserTask 的扩展属性，必须被
 * {@link BpmnXMLConverter} 保留进 {@link UserTask#getAttributes()}，供
 * {@link AssigneeTaskListener} 按任务级解析审批人。设计器 XML 形态与引擎解析
 * 任一侧单方面改动，本测试先红。
 *
 * @author jffast
 */
class DesignerAssigneeAttributeTest {

    /** 与 jffast-ui 流程设计器 + flowable-moddle.ts 的产出形态一致（属性式扩展）。 */
    private static final String DESIGNER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://jffast.io/flow">
              <process id="process_1" name="新流程" isExecutable="true">
                <startEvent id="start" name="开始" />
                <userTask id="task_1" name="审批"
                          flowable:assigneeType="role" flowable:assigneeValue="manager" />
                <endEvent id="end" name="结束" />
              </process>
            </definitions>
            """;

    @Test
    @DisplayName("引擎转换器保留 UserTask 上的 assigneeType/assigneeValue 扩展属性")
    void converterKeepsAssigneeAttributes() throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(
                new ByteArrayInputStream(DESIGNER_XML.getBytes(StandardCharsets.UTF_8)));
        BpmnModel model;
        try {
            model = new BpmnXMLConverter().convertToBpmnModel(reader);
        } finally {
            reader.close();
        }

        assertNotNull(model.getMainProcess(), "主流程应解析成功");
        FlowElement element = model.getMainProcess().getFlowElement("task_1");
        assertInstanceOf(UserTask.class, element, "task_1 应解析为 UserTask");
        UserTask userTask = (UserTask) element;

        List<ExtensionAttribute> type = userTask.getAttributes().get("assigneeType");
        List<ExtensionAttribute> value = userTask.getAttributes().get("assigneeValue");
        assertNotNull(type, "assigneeType 扩展属性应被引擎保留");
        assertEquals("role", type.get(0).getValue());
        assertNotNull(value, "assigneeValue 扩展属性应被引擎保留");
        assertEquals("manager", value.get(0).getValue());
    }
}
