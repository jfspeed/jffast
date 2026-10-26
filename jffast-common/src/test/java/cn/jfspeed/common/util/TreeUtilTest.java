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
package cn.jfspeed.common.util;

import cn.jfspeed.common.core.util.TreeUtil;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jffast
 */
class TreeUtilTest {

    @Data
    static class TreeNode {
        private Long id;
        private Long parentId;
        private String name;
        private List<TreeNode> children;

        public TreeNode(Long id, Long parentId, String name) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
        }
    }

    @Nested
    @DisplayName("buildTreeIsRoot")
    class BuildTreeIsRoot {

        @Test
        @DisplayName("应以指定 parentId 为根构建树")
        void shouldBuildTreeWithRoot() {
            List<TreeNode> list = new ArrayList<>(Arrays.asList(
                    new TreeNode(1L, 0L, "root"),
                    new TreeNode(2L, 1L, "child1"),
                    new TreeNode(3L, 1L, "child2"),
                    new TreeNode(4L, 2L, "grandchild1")
            ));

            List<TreeNode> tree = TreeUtil.buildTreeIsRoot(list, 0L);

            assertEquals(1, tree.size());
            assertEquals("root", tree.get(0).getName());
            assertNotNull(tree.get(0).getChildren());
            assertEquals(2, tree.get(0).getChildren().size());
            assertNotNull(tree.get(0).getChildren().get(0).getChildren());
            assertEquals(1, tree.get(0).getChildren().get(0).getChildren().size());
        }

        @Test
        @DisplayName("无匹配根节点应返回空列表")
        void shouldReturnEmptyForNoMatch() {
            List<TreeNode> list = new ArrayList<>(Arrays.asList(
                    new TreeNode(1L, 100L, "orphan")
            ));

            List<TreeNode> tree = TreeUtil.buildTreeIsRoot(list, 0L);
            assertTrue(tree.isEmpty());
        }
    }

    @Nested
    @DisplayName("buildTree")
    class BuildTree {

        @Test
        @DisplayName("应筛选出 parentId 匹配的节点")
        void shouldFilterByParentId() {
            List<TreeNode> list = new ArrayList<>(Arrays.asList(
                    new TreeNode(1L, 0L, "a"),
                    new TreeNode(2L, 0L, "b"),
                    new TreeNode(3L, 1L, "c")
            ));

            List<TreeNode> roots = TreeUtil.buildTree(list, 0L);

            assertEquals(2, roots.size());
            assertEquals("a", roots.get(0).getName());
            assertEquals("b", roots.get(1).getName());
        }
    }
}
