/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.Map;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static io.helidon.config.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class EnvVarsTest {
    @Test
    void testValueNodes() {
        String javaHome = System.getenv("JAVA_HOME");
        assertThat(javaHome, notNullValue());

        assertValueNode("simple", "unmapped-env-value");
        assertValueNode("java.home", javaHome);
        assertValueNode("server.executor-service.max-pool-size", "mapped-env-value");
        assertValueNode("helidon.env.tree.branch.leaf", "tree-leaf");
        assertValueNode("helidon.env.tree.service-foo.max-pool-size", "42");
    }

    @Test
    void testJavaParentObjectNode() {
        String javaHome = System.getenv("JAVA_HOME");
        assertThat(javaHome, notNullValue());

        ObjectNode javaNode = objectNode("java");

        assertThat(javaNode.get("home").value(), optionalValue(is(javaHome)));
    }

    @Test
    void testNestedObjectNode() {
        ObjectNode root = objectNode("helidon.env.tree");

        assertThat(ConfigHelper.flattenNodes(root), is(Map.of(
                "branch.leaf", "tree-leaf",
                "branch.child.grandchild", "tree-grandchild",
                "branch.underscore.leaf", "underscore-leaf",
                "branches.other", "boundary-other",
                "service-foo.max-pool-size", "42",
                "service-foo.core-pool-size", "21"
        )));
    }

    @Test
    void testNestedBranchObjectNodeBoundary() {
        ObjectNode branch = objectNode("helidon.env.tree.branch");

        assertThat(ConfigHelper.flattenNodes(branch), is(Map.of(
                "leaf", "tree-leaf",
                "child.grandchild", "tree-grandchild",
                "underscore.leaf", "underscore-leaf"
        )));
    }

    @Test
    void testDashParentObjectNode() {
        ObjectNode serviceFoo = objectNode("helidon.env.tree.service-foo");

        assertThat(ConfigHelper.flattenNodes(serviceFoo), is(Map.of(
                "max-pool-size", "42",
                "core-pool-size", "21"
        )));
    }

    private static void assertValueNode(String key, String expectedValue) {
        ConfigNode node = EnvVars.node(key).orElseThrow(() -> new AssertionError("Missing env node for " + key));
        assertThat(node, instanceOf(ConfigNode.ValueNode.class));
        assertThat(node, valueNode(expectedValue));
    }

    private static ObjectNode objectNode(String key) {
        ConfigNode node = EnvVars.node(key).orElseThrow(() -> new AssertionError("Missing env node for " + key));
        assertThat(node, instanceOf(ObjectNode.class));
        return (ObjectNode) node;
    }
}
