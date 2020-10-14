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

package io.helidon.config;

import java.util.List;
import java.util.Optional;

import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigNode.ValueNode;

import org.junit.jupiter.api.Test;

import static io.helidon.config.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link ObjectNodeBuilderImpl}.
 */
public class ObjectNodeBuilderImplTest {

    @Test
    public void testEmpty() {
        ObjectNodeBuilderImpl builder = new ObjectNodeBuilderImpl();

        assertThat(builder.build().entrySet(), is(empty()));
    }

    @Test
    public void testMergeValueToValue() {
        ObjectNode rootNode = new ObjectNodeBuilderImpl()
                .addValue("top1.prop1", "1")
                .addValue("top1.prop1", "2")
                .build();

        assertThat(rootNode.entrySet(), hasSize(1));
        assertThat(((ObjectNode) rootNode.get("top1")).get("prop1"), valueNode("2"));
    }

    @Test
    public void testMergeValueToList() {
        ObjectNode rootNode = new ObjectNodeBuilderImpl()
                .addList("top1.prop1", ListNode.builder().addValue("2").build())
                .addValue("top1.prop1", "1")
                .build();

        assertThat(((ObjectNode) rootNode.get("top1")).get("prop1").value(), is(Optional.of("1")));
    }

    @Test
    public void testMergeValueToObject() {
        ObjectNode rootNode = new ObjectNodeBuilderImpl()
                .addValue("top1.prop1.sub", "2")
                .addValue("top1.prop1", "1")
                .build();

        ObjectNode prop1 = (ObjectNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(prop1.value(), is(Optional.of("1")));
        assertThat(prop1.get("sub").value(), is(Optional.of("2")));
    }

    @Test
    public void testMergeObjectToValue() {
        ObjectNode rootNode = new ObjectNodeBuilderImpl()
                .addValue("top1.prop1", "2")
                .addValue("top1.prop1.sub", "1")
                .build();

        ObjectNode prop1 = (ObjectNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(prop1.value(), is(Optional.of("2")));
        assertThat(prop1.get("sub").value(), is(Optional.of("1")));
    }

    @Test
    public void testMergeObjectWithNonNumberKeyToList() {
        ConfigException ex = assertThrows(ConfigException.class, () -> {
            new ObjectNodeBuilderImpl()
                    .addList("top1.prop1", ListNode.builder().addValue("2").build())
                    .addValue("top1.prop1.sub1", "1")
                    .build();
        });
        assertThat(ex.getMessage(),
                   stringContainsInOrder(List.of("top1", "prop1", "merge", "OBJECT", "'sub1'", "LIST", "not a number")));
    }

    @Test
    public void testMergeObjectWithNumberKeyToList() {
        ObjectNode rootNode = new ObjectNodeBuilderImpl()
                .addList("top1.prop1", ListNode.builder().addValue("2").build())
                .addValue("top1.prop1.0", "1")
                .build();

        assertThat(rootNode.entrySet(), hasSize(1));

        ListNode listNode = (ListNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(listNode, hasSize(1));
        assertThat(listNode.get(0), valueNode("1"));
    }

    @Test
    public void testMergeObjectWithNumberKeyOutOfBoundsToList() {
        ConfigException ex = assertThrows(ConfigException.class, () -> {
            new ObjectNodeBuilderImpl()
                    .addList("top1.prop1", ListNode.builder().addValue("1").addValue("2").build())
                    .addValue("top1.prop1.2", "1")
                    .build();
        });
        assertThat(ex.getMessage(),
                   stringContainsInOrder(List.of("top1",
                                                 "prop1",
                                                 "merge",
                                                 "OBJECT",
                                                 "'2'",
                                                 "LIST",
                                                 "out of bounds")));
    }

    @Test
    public void testMergeObjectWithNegativeNumberKeyOutOfBoundsToList() {
        ConfigException ex = assertThrows(ConfigException.class, () -> {
            new ObjectNodeBuilderImpl()
                    .addList("top1.prop1", ListNode.builder().addValue("2").build())
                    .addValue("top1.prop1.-1", "1")
                    .build();
        });
        assertThat(ex.getMessage(),
                   stringContainsInOrder(List.of("top1",
                                                 "prop1",
                                                 "merge",
                                                 "OBJECT",
                                                 "'-1'",
                                                 "LIST",
                                                 "negative index")));
    }

    @Test
    public void testMergeObjectToObject() {
        ObjectNode rootNode = new ObjectNodeBuilderImpl()
                .addValue("top1.prop1.sub1", "1")
                .addValue("top1.prop1.sub2", "2")
                .build();

        assertThat(rootNode.entrySet(), hasSize(1));

        ObjectNode objectNode = (ObjectNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(objectNode.entrySet(), hasSize(2));
        assertThat(objectNode.get("sub1"), valueNode("1"));
        assertThat(objectNode.get("sub2"), valueNode("2"));
    }

    @Test
    public void testMergeListToValue() {
        ObjectNode rootNode = new ObjectNodeBuilderImpl()
                .addValue("top1.prop1", "2")
                .addList("top1.prop1", ListNode.builder().addValue("2").build())
                .build();

        assertThat(((ObjectNode) rootNode.get("top1")).get("prop1").value(), is(Optional.of("2")));
    }

    @Test
    public void testMergeListToList() {
        ObjectNode rootNode = new ObjectNodeBuilderImpl()
                .addList("top1.prop1", ListNode.builder().addValue("3").build())
                .addList("top1.prop1", ListNode.builder().addValue("1").addValue("2").build())
                .build();

        assertThat(rootNode.entrySet(), hasSize(1));

        ListNode listNode = (ListNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(listNode, hasSize(2));
        assertThat(listNode.get(0), valueNode("1"));
        assertThat(listNode.get(1), valueNode("2"));
    }

    @Test
    public void testMergeListToObjectWithNonNumberKey() {
        ObjectNode rootNode = new ObjectNodeBuilderImpl()
                .addValue("top1.prop1.sub1", "2")
                .addList("top1.prop1", ListNode.builder().addValue("1").build())
                .build();
        ObjectNode listNode = (ObjectNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(listNode.entrySet(), hasSize(2));
        assertThat(listNode.get("0"), valueNode("1"));
        assertThat(listNode.get("sub1"), valueNode("2"));
    }

    @Test
    public void testMergeListToObjectWithNumberKey() {
        ObjectNode rootNode = new ObjectNodeBuilderImpl()
                .addValue("top1.prop1.0", "2")
                .addList("top1.prop1", ListNode.builder().addValue("1").build())
                .build();
        ObjectNode listNode = (ObjectNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        assertThat(listNode.entrySet(), hasSize(1));
        assertThat(listNode.get("0"), valueNode("1"));
    }

    @Test
    public void testComplex() {
        ObjectNodeBuilderImpl builder = new ObjectNodeBuilderImpl();
        builder.addValue("key1", "value1");
        builder.addObject("obj1", ObjectNode.builder().addValue("key3", "value3").build());
        builder.addValue("obj1.key2", "value2");
        builder.addObject("obj2", ObjectNode.builder()
                .addValue("key4", "value4")
                .addValue("obj3.key5", "value5")
                .build());
        builder.addList("array1", ListNode.builder().addValue("another prop1").build());

        ObjectNode objectNode = builder.build();
        assertThat(objectNode.entrySet(), hasSize(4));
        //key1
        assertThat(objectNode.get("key1"), instanceOf(ValueNode.class));
        assertThat(objectNode.get("key1"), valueNode("value1"));
        //obj1.key2
        assertThat(((ObjectNode) objectNode.get("obj1")).get("key2"), instanceOf(ValueNode.class));
        assertThat(((ObjectNode) objectNode.get("obj1")).get("key2"), valueNode("value2"));
        //obj1.key3
        assertThat(((ObjectNode) objectNode.get("obj1")).get("key3"), instanceOf(ValueNode.class));
        assertThat(((ObjectNode) objectNode.get("obj1")).get("key3"), valueNode("value3"));
        //obj2.key4
        assertThat(((ObjectNode) objectNode.get("obj2")).get("key4"), instanceOf(ValueNode.class));
        assertThat(((ObjectNode) objectNode.get("obj2")).get("key4"), valueNode("value4"));
        //obj2.obj3.key5
        assertThat(((ObjectNode) ((ObjectNode) objectNode.get("obj2")).get("obj3")).get("key5"), instanceOf(ValueNode.class));
        assertThat(((ObjectNode) ((ObjectNode) objectNode.get("obj2")).get("obj3")).get("key5"), valueNode("value5"));
        //array1
        assertThat(objectNode.get("array1"), instanceOf(ListNode.class));
        assertThat(((ListNode) objectNode.get("array1")).get(0), valueNode("another prop1"));
    }

    @Test
    public void testComplexThroughSubNodes() {
        ObjectNodeBuilderImpl builder = new ObjectNodeBuilderImpl();
        builder.addValue("key1", "value1");
        builder.addObject("obj1", ObjectNode.builder()
                .addValue("key3", "value3")
                .addValue("key2", "value2")
                .build());
        builder.addObject("obj2", ObjectNode.builder()
                .addValue("key4", "value4")
                .addObject("obj3", ObjectNode.builder()
                        .addValue("key5", "value5")
                        .build())
                .build());
        builder.addList("array1", ListNode.builder().addValue("another prop1").build());

        ObjectNode objectNode = builder.build();
        assertThat(objectNode.entrySet(), hasSize(4));
        //key1
        assertThat(objectNode.get("key1"), instanceOf(ValueNode.class));
        assertThat(objectNode.get("key1"), valueNode("value1"));

        //obj1
        ObjectNode obj1 = (ObjectNode) objectNode.get("obj1");
        //obj1.key2
        assertThat(obj1.get("key2"), instanceOf(ValueNode.class));
        assertThat(obj1.get("key2"), valueNode("value2"));
        //obj1.key3
        assertThat(obj1.get("key3"), instanceOf(ValueNode.class));
        assertThat(obj1.get("key3"), valueNode("value3"));

        //obj2
        ObjectNode obj2 = (ObjectNode) objectNode.get("obj2");
        //obj2.key4
        assertThat(obj2.get("key4"), instanceOf(ValueNode.class));
        assertThat(obj2.get("key4"), valueNode("value4"));

        //obj2
        ObjectNode obj3 = (ObjectNode) obj2.get("obj3");
        //obj2.obj3.key5
        assertThat(obj3.get("key5"), instanceOf(ValueNode.class));
        assertThat(obj3.get("key5"), valueNode("value5"));
        //array1
        assertThat(objectNode.get("array1"), instanceOf(ListNode.class));
        assertThat(((ListNode) objectNode.get("array1")).get(0), valueNode("another prop1"));
    }

    @Test
    public void testResolveTokenFunction() {
        ObjectNode rootNode = new ObjectNodeBuilderImpl((s) ->
                                                        {
                                                            if (s.equals("$host")) {
                                                                return "localhost";
                                                            }
                                                            return s;
                                                        })
                .addValue("host", "localhost")
                .addValue("$host", "2")
                .build();

        assertThat(rootNode.entrySet(), hasSize(2));

        assertThat(rootNode.get("host"), valueNode("localhost"));
        assertThat(rootNode.get("localhost"), valueNode("2"));
    }

}
