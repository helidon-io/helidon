/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;

/**
 * Test support for hybrid nodes - e.g. a node is both a branch and
 * a value.
 */
public class HybridNodeTest {
    private static Config config;

    @BeforeAll
    public static void initClass() {
        config = Config.builder()
                .sources(ConfigSources.classpath("io/helidon/config/application.properties"))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
    }

    @Test
    public void testBuilderOverlapParentLast() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("app.port", "8080");
        map.put("app", "app-name");

        MapConfigSource mapConfigSource = ConfigSources.create(map).build();
        mapConfigSource.init(mock(ConfigContext.class));
        ConfigNode.ObjectNode objectNode = mapConfigSource.load().get().data();

        assertThat(objectNode.entrySet(), hasSize(1));
        assertThat(((ConfigNode.ObjectNode) objectNode.get("app")).get("port").value(), is(Optional.of("8080")));
        assertThat(objectNode.get("app").value(), is(Optional.of("app-name")));
    }

    @Test
    public void testNodeValue() {
        assertThat("app1.node.value value should be present",
                   config.get("app1.node.value").asBoolean().get(),
                   is(true));
        assertThat("app1.node.value value should be present",
                   config.get("app1.node.value").hasValue(),
                   is(true));
    }

    @Test
    public void testSubnodeValue() {
        assertThat("app1.node.value.sub1 value should be present",
                   config.get("app1.node.value.sub1").asString().get(),
                   is("subvalue1"));

        assertThat("app1.node.value.sub2 value should be present",
                   config.get("app1.node.value.sub2").asString().get(),
                   is("subvalue2"));
    }

    @Test
    public void testListNodeValue() {
        assertThat("app1.node1.value should be present",
                   config.get("app1.node1.value").asBoolean().get(),
                   is(true));
    }

    @Test
    public void testListValue() {
        assertThat("app1.node1.value should be reachable as list",
                   config.get("app1.node1.value").asList(Integer.class).get(),
                   is(List.of(14, 15, 16)));
    }
}
