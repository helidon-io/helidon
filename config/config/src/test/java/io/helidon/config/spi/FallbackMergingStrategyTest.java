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

package io.helidon.config.spi;

import java.util.List;
import java.util.Optional;

import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;

import org.junit.jupiter.api.Test;

import static io.helidon.config.ValueNodeMatcher.valueNode;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link io.helidon.config.spi.FallbackMergingStrategy}.
 */
public class FallbackMergingStrategyTest {

    private ObjectNode mergeLoads(ObjectNode... nodes) {
        FallbackMergingStrategy strategy = new FallbackMergingStrategy();
        return strategy.merge(List.of(nodes));
    }

    @Test
    public void testMergeEmptyList() {
        ObjectNode rootNode = mergeLoads();

        assertThat(rootNode.entrySet(), hasSize(0));
    }

    @Test
    public void testMergeSingleSource() {
        ObjectNode rootNode = mergeLoads(ObjectNode.builder().addValue("top1.prop1", "1").build());

        assertThat(rootNode.entrySet(), hasSize(1));
        assertThat(((ObjectNode) rootNode.get("top1")).get("prop1"), valueNode("1"));
    }

    @Test
    public void testMergeValueToValueNew() {
        ObjectNode rootNode = mergeLoads(
                ObjectNode.builder().addValue("top1.prop1", "1").build(),
                ObjectNode.builder().addValue("top1.prop1", "2").build());

        assertThat(rootNode.entrySet(), hasSize(1));
        assertThat(((ObjectNode) rootNode.get("top1")).get("prop1"), valueNode("1"));
    }

    @Test
    public void testMergeValueToValue() {
        ObjectNode rootNode = mergeLoads(
                ObjectNode.builder().addValue("top1.prop1", "1").build(),
                ObjectNode.builder().addValue("top1.prop1", "2").build());

        assertThat(rootNode.entrySet(), hasSize(1));
        assertThat(((ObjectNode) rootNode.get("top1")).get("prop1"), valueNode("1"));
    }

    @Test
    public void testMergeValueToList() {
        ObjectNode rootNode = mergeLoads(
                ObjectNode.builder().addValue("top1.prop1", "1").build(),
                ObjectNode.builder().addList("top1.prop1", ListNode.builder()
                        .addValue("2").build()).build());

        assertThat(((ObjectNode) rootNode.get("top1")).get("prop1").value(), is(Optional.of("1")));
    }

    @Test
    public void testMergeValueToObject() {
        ObjectNode rootNode = mergeLoads(
                ObjectNode.builder().addValue("top1.prop1", "1").build(),
                ObjectNode.builder().addValue("top1.prop1.sub", "2").build());

        ObjectNode prop1 = (ObjectNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(prop1.value(), is(Optional.of("1")));
        assertThat(prop1.get("sub").value(), is(Optional.of("2")));
    }

    @Test
    public void testMergeObjectToValue() {
        ObjectNode rootNode = mergeLoads(
                ObjectNode.builder().addValue("top1.prop1.sub", "1").build(),
                ObjectNode.builder().addValue("top1.prop1", "2").build());

        ObjectNode prop1 = (ObjectNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(prop1.value(), is(Optional.of("2")));
        assertThat(prop1.get("sub").value(), is(Optional.of("1")));
    }

    @Test
    public void testMergeObjectWithNonNumberKeyToList() {
        ConfigException ex = assertThrows(ConfigException.class, () -> {
            mergeLoads(
                    ObjectNode.builder().addValue("top1.prop1.sub1", "1").build(),
                    ObjectNode.builder().addList("top1.prop1", ListNode.builder()
                            .addValue("2").build()).build());
        });
        assertThat(ex.getMessage(),
                   stringContainsInOrder(List.of("top1", "prop1", "merge", "OBJECT", "[sub1]", "LIST")));
    }

    @Test
    public void testMergeObjectWithNumberKeyToList() {
        ObjectNode rootNode = mergeLoads(
                ObjectNode.builder().addValue("top1.prop1.0", "1").build(),
                ObjectNode.builder().addList("top1.prop1", ListNode.builder()
                        .addValue("2").build()).build());
        assertThat(rootNode.entrySet(), hasSize(1));

        ListNode listNode = (ListNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(listNode, hasSize(1));
        assertThat(listNode.get(0), valueNode("1"));
    }

    @Test
    public void testMergeObjectWithNumberKeyOutOfBoundsToList() {
        ConfigException ex = assertThrows(ConfigException.class, () -> {
            mergeLoads(
                    ObjectNode.builder().addValue("top1.prop1.1", "1").build(),
                    ObjectNode.builder()
                            .addList("top1.prop1", ListNode.builder().addValue("2").build())
                            .build());
        });
        assertThat(ex.getMessage(),
                   stringContainsInOrder(List.of("top1", "prop1", "merge", "OBJECT", "[1]", "LIST")));
    }

    @Test
    public void testMergeObjectToObject() {
        ObjectNode rootNode = mergeLoads(
                ObjectNode.builder().addValue("top1.prop1.sub1", "1").build(),
                ObjectNode.builder().addValue("top1.prop1.sub2", "2").build());

        assertThat(rootNode.entrySet(), hasSize(1));

        ObjectNode objectNode = (ObjectNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(objectNode.entrySet(), hasSize(2));
        assertThat(objectNode.get("sub1"), valueNode("1"));
        assertThat(objectNode.get("sub2"), valueNode("2"));
    }

    @Test
    public void testMergeListToValue() {
        ObjectNode rootNode = mergeLoads(
                ObjectNode.builder().addList("top1.prop1", ListNode.builder()
                        .addValue("1").build()).build(),
                ObjectNode.builder().addValue("top1.prop1", "2").build());

        ListNode prop1 = (ListNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(prop1.value(), is(Optional.of("2")));
    }

    @Test
    public void testMergeListToList() {
        ObjectNode rootNode = mergeLoads(
                ObjectNode.builder().addList("top1.prop1", ListNode.builder()
                        .addValue("1")
                        .addValue("2")
                        .build()).build(),
                ObjectNode.builder().addList("top1.prop1", ListNode.builder()
                        .addValue("3").build()).build());

        assertThat(rootNode.entrySet(), hasSize(1));

        ListNode listNode = (ListNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(listNode, hasSize(2));
        assertThat(listNode.get(0), valueNode("1"));
        assertThat(listNode.get(1), valueNode("2"));
    }

    @Test
    public void testMergeListToObjectWithNonNumberKey() {
        ObjectNode rootNode = mergeLoads(
                ObjectNode.builder().addList("top1.prop1", ListNode.builder()
                        .addValue("1").build())
                        .build(),
                ObjectNode.builder().addValue("top1.prop1.sub1", "2").build());
        ObjectNode listNode = (ObjectNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(listNode.entrySet(), hasSize(2));
        assertThat(listNode.get("0"), valueNode("1"));
        assertThat(listNode.get("sub1"), valueNode("2"));
    }

    @Test
    public void testMergeListToObjectWithNumberKey() {
        ObjectNode rootNode = mergeLoads(
                ObjectNode.builder()
                        .addList("top1.prop1", ListNode.builder()
                                .addValue("1").build())
                        .build(),
                ObjectNode.builder().addValue("top1.prop1.0", "2").build());
        ObjectNode listNode = (ObjectNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(listNode.entrySet(), hasSize(1));
        assertThat(listNode.get("0"), valueNode("1"));
    }

    @Test
    public void testMergeWithNewKeys() {
        ObjectNode rootNode = mergeLoads(
                ObjectNode.builder()
                        .addValue("a-prop1", "1")
                        .addList("a-list", ListNode.builder()
                                .addValue("2")
                                .addValue("3").build())
                        .addObject("a-object", ObjectNode.builder()
                                .addValue("prop1", "4")
                                .build())
                        .build(),
                ObjectNode.builder()
                        .addValue("b-prop1", "11")
                        .addList("b-list", ListNode.builder()
                                .addValue("12")
                                .addValue("13").build())
                        .addObject("b-object", ObjectNode.builder()
                                .addValue("prop1", "14")
                                .build())
                        .build());

        assertThat(rootNode.entrySet(), hasSize(6));
        //values
        assertThat(rootNode.get("a-prop1"), valueNode("1"));
        assertThat(rootNode.get("b-prop1"), valueNode("11"));
        //lists
        ListNode aListNode = (ListNode) rootNode.get("a-list");
        assertThat(aListNode, hasSize(2));
        assertThat(aListNode.get(0), valueNode("2"));
        assertThat(aListNode.get(1), valueNode("3"));
        ListNode bListNode = (ListNode) rootNode.get("b-list");
        assertThat(bListNode, hasSize(2));
        assertThat(bListNode.get(0), valueNode("12"));
        assertThat(bListNode.get(1), valueNode("13"));
        //objects
        ObjectNode aObjectNode = (ObjectNode) rootNode.get("a-object");
        assertThat(aObjectNode.entrySet(), hasSize(1));
        assertThat(aObjectNode.get("prop1"), valueNode("4"));
        ObjectNode bObjectNode = (ObjectNode) rootNode.get("b-object");
        assertThat(bObjectNode.entrySet(), hasSize(1));
        assertThat(bObjectNode.get("prop1"), valueNode("14"));
    }

}
