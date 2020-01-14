/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static io.helidon.common.HelidonFeatures.register;
import static io.helidon.common.HelidonFlavor.SE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * Unit test for {@link HelidonFeatures}.
 */
class HelidonFeaturesTest {
    @Test
    void testFeatureTree() {
        register(SE, "security");
        register(SE, "security", "authorization", "ABAC");
        register(SE, "security", "authentication", "OIDC");
        register(SE, "security", "outbound", "OIDC");
        register(SE, "tracing");
        register(SE, "tracing", "zipkin");
        register(SE, "complex");
        register(SE, "complex", "first");
        register(SE, "complex", "first", "second");
        register(SE, "complex", "first", "second", "third");
        register(SE, "complex", "first", "second", "third2");

        Map<String, HelidonFeatures.Node> features = HelidonFeatures.rootFeatureNodes(SE);

        assertThat(features.keySet(), containsInAnyOrder("security", "tracing", "complex"));

        HelidonFeatures.Node security = features.get("security");
        assertThat(security.name(), is("security"));

        Map<String, HelidonFeatures.Node> children = security.children();
        assertThat(children.keySet(), containsInAnyOrder("authorization", "authentication", "outbound"));
        HelidonFeatures.Node node = children.get("authentication");
        assertThat(node.name(), is("authentication"));
        HelidonFeatures.Node oidc = node.children().get("OIDC");
        assertThat(oidc.name(), is("OIDC"));
        assertThat(oidc.children(), is(Map.of()));

        HelidonFeatures.Node second = features.get("complex").children().get("first").children().get("second");
        Map<String, HelidonFeatures.Node> secondChildren = second.children();
        assertThat(secondChildren.keySet(), containsInAnyOrder("third", "third2"));

        HelidonFeatures.print(SE, true);
    }
}