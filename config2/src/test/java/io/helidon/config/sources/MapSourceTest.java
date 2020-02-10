/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config.sources;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.ConfigNode;
import io.helidon.config.ConfigNode.NodeType;
import io.helidon.config.ConfigNode.ObjectNode;
import io.helidon.config.ConfigNode.ValueNode;
import io.helidon.config.spi.Content;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class MapSourceTest {
    public static final String PROPERTY_NAME = "asdfghulkarghungherghpppaaa";

    @BeforeEach
    void initTest() {
        System.getProperties().remove(PROPERTY_NAME);
    }

    @AfterEach
    void cleanTest() {
        System.getProperties().remove(PROPERTY_NAME);
    }

    @Test
    void testSysProps() {
        String firstValue = "First value";

        MapConfigSource source = MapConfigSource.create(System.getProperties());

        assertThat("Map always exists", source.exists(), is(true));
        assertThat("Description should not be null", source.description(), notNullValue());

        Content.NodeContent content = source.load();

        ObjectNode node = content.data();
        Map<?, ?> stamp = testTypedOptional(content.stamp(), "Stamp", Map.class);
        assertThat(content.target(), is(Optional.empty()));

        assertThat(node, is(notNullValue()));
        testNode(node, PROPERTY_NAME, NodeType.VALUE, null);

        // make sure the value is not in the stamp
        assertThat("Property should not be in map", stamp.get(PROPERTY_NAME), is(nullValue()));

        System.setProperty(PROPERTY_NAME, firstValue);

        assertThat("Properties changed", source.isModified(stamp), is(true));
        assertThat("Property should not be in map (to ensure stamp is a different instance)",
                   stamp.get(PROPERTY_NAME),
                   is(nullValue()));

        // now reload
        content = source.load();

        node = content.data();
        Map<?, ?> newStamp = testTypedOptional(content.stamp(), "Stamp", Map.class);
        assertThat("Stamps differ", stamp, not(newStamp));
        assertThat(content.target(), is(Optional.empty()));

        assertThat(node, is(notNullValue()));
        testNode(node, PROPERTY_NAME, NodeType.VALUE, firstValue);
    }

    @Test
    void testComplexMap() {
        Map<String, String> properties = new HashMap<>(Map.of("http", "true",
                                                            "http.ssl", "true",
                                                            "http.ssl.protocol", "TLS",
                                                            "http.ssl.key", "myKey"));

        MapConfigSource source = MapConfigSource.create(properties);

        Content.NodeContent content = source.load();
        Map<?, ?> stamp = testTypedOptional(content.stamp(), "Stamp", Map.class);
        ObjectNode node = content.data();
        testNode(node, "http", NodeType.OBJECT, "true");

        // need to test other nodes
        ObjectNode httpNode = (ObjectNode) node.get("http");
        ObjectNode sslNode = (ObjectNode) httpNode.get("ssl");
        assertThat(sslNode.get(), is(Optional.of("true")));
        ValueNode protocolNode = (ValueNode) sslNode.get("protocol");
        assertThat(protocolNode.get(), is(Optional.of("TLS")));
        ValueNode keyNode = (ValueNode) sslNode.get("key");
        assertThat(keyNode.get(), is(Optional.of("myKey")));

        // change support
        properties.put("http", "false");
        assertThat(source.isModified(stamp), is(true));
        content = source.load();
        node = content.data();
        testNode(node, "http", NodeType.OBJECT, "false");
    }

    private void testNode(ObjectNode node, String key, NodeType type, String value) {
        assertThat(node.nodeType(), is(NodeType.OBJECT));
        if (null == value) {
            assertThat("Key should not be present", node.containsKey(key), is(false));
        } else {
            assertThat("Key should be present", node.containsKey(key), is(true));
            ConfigNode configNode = node.get(key);
            assertThat(configNode.nodeType(), is(type));
            assertThat(configNode.get(), is(Optional.of(value)));
        }
    }

    @SuppressWarnings({"OptionalGetWithoutIsPresent", "unchecked"})
    private <T> T testTypedOptional(Optional<Object> optional, String field, Class<T> type) {
        assertThat(field + " should not be empty", optional, not(Optional.empty()));
        Object object = optional.get();
        assertThat(field + " should be of correct type", object, instanceOf(type));
        return (T) object;
    }
}