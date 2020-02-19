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

import io.helidon.config.spi.ConfigNode;
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

/**
 * Tests {@link ListNodeBuilderImpl}.
 */
public class ListNodeBuilderImplTest {

    @Test
    public void testEmpty() {
        ListNodeBuilderImpl builder = new ListNodeBuilderImpl();
        assertThat(builder.build(), is(empty()));
    }

    @Test
    public void testComplex() {
        ListNodeBuilderImpl builder = new ListNodeBuilderImpl();
        builder.addValue("text value");
        builder.addList(ListNode.builder().addValue("another value").build());
        builder.addObject(ObjectNode.builder().addValue("obj1.key1", "value1").build());

        ListNode listNode = builder.build();
        assertThat(listNode, hasSize(3));
        //0
        assertThat(listNode.get(0), instanceOf(ValueNode.class));
        assertThat(listNode.get(0), valueNode("text value"));
        //1
        assertThat(listNode.get(1), instanceOf(ListNode.class));
        assertThat(((ListNode) listNode.get(1)).get(0), valueNode("another value"));
        //2
        assertThat(listNode.get(2), instanceOf(ObjectNode.class));
        assertThat(((ObjectNode) ((ObjectNode) listNode.get(2)).get("obj1")).get("key1"), valueNode("value1"));
    }

    @Test
    public void testMerge() {
        ObjectNode objectNode = new ObjectNodeBuilderImpl()
                .addList("top1.prop1", new ListNodeBuilderImpl()
                        .addValue("text value")
                        .addList(ListNode.builder().addValue("another value").build())
                        .addObject(ObjectNode.builder().addValue("obj1.key1", "value1").build())
                        .build())
                .addValue("top1.prop1.0", "new text value")
                .addValue("top1.prop1.1.0", "another another value")
                .addValue("top1.prop1.2.obj1.key1", "value2")
                .build();

        ListNode listNode = (ListNode) ((ObjectNode) objectNode.get("top1")).get("prop1");

        assertThat(listNode, hasSize(3));
        //0
        assertThat(listNode.get(0), instanceOf(ValueNode.class));
        assertThat(listNode.get(0), valueNode("new text value"));
        //1
        assertThat(listNode.get(1), instanceOf(ListNode.class));
        assertThat(((ListNode) listNode.get(1)).get(0), valueNode("another another value"));
        //2
        assertThat(listNode.get(2), instanceOf(ObjectNode.class));
        assertThat(((ObjectNode) ((ObjectNode) listNode.get(2)).get("obj1")).get("key1"), valueNode("value2"));
    }

    @Test
    public void testMergeListToValue() {
        ObjectNode rootNode = new ObjectNodeBuilderImpl()
                .addList("top1.prop1", ListNode.builder().addValue("another value").build())
                .addValue("top1.prop1.0.sub", "text")
                .build();

        ListNode prop1 = (ListNode) ((ObjectNode) rootNode.get("top1")).get("prop1");
        ConfigNode firstElement = prop1.get(0);
        assertThat(((ObjectNode) firstElement).get("sub"), valueNode("text"));
    }

}
