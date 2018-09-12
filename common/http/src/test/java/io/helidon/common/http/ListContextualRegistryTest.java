/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests {@link ListContextualRegistry} and {@link ContextualRegistry}.
 */
public class ListContextualRegistryTest {

    @Test
    public void create() throws Exception {
        assertNotNull(ContextualRegistry.create());
        assertNotNull(ContextualRegistry.create(null));
        assertNotNull(ContextualRegistry.create(ContextualRegistry.create()));
    }

    @Test
    public void registerAndGetLast() throws Exception {
        ContextualRegistry context = ContextualRegistry.create();
        assertFalse(context.get(String.class).isPresent());
        assertFalse(context.get(Integer.class).isPresent());
        context.register("aaa");
        assertEquals("aaa", context.get(String.class).orElse(null));
        assertFalse(context.get(Integer.class).isPresent());
        context.register(1);
        assertEquals("aaa", context.get(String.class).orElse(null));
        assertEquals(Integer.valueOf(1), context.get(Integer.class).orElse(null));
        assertEquals(Integer.valueOf(1), context.get(Object.class).orElse(null));
        context.register("bbb");
        assertEquals("bbb", context.get(String.class).orElse(null));
        assertEquals("bbb", context.get(Object.class).orElse(null));
    }

    @Test
    public void registerAndGetLastClassifier() throws Exception {
        ContextualRegistry context = ContextualRegistry.create();
        String classifier = "classifier";
        assertFalse(context.get(classifier, String.class).isPresent());
        assertFalse(context.get(classifier, Integer.class).isPresent());
        context.register(classifier, "aaa");
        assertEquals("aaa", context.get(classifier, String.class).orElse(null));
        assertFalse(context.get(String.class).isPresent());
        assertFalse(context.get(classifier, Integer.class).isPresent());
        context.register(classifier, 1);
        assertEquals("aaa", context.get(classifier, String.class).orElse(null));
        assertEquals(Integer.valueOf(1), context.get(classifier, Integer.class).orElse(null));
        assertEquals(Integer.valueOf(1), context.get(classifier, Object.class).orElse(null));
        context.register(classifier, "bbb");
        assertEquals("bbb", context.get(classifier, String.class).orElse(null));
        context.register("ccc");
        assertEquals("bbb", context.get(classifier, String.class).orElse(null));
        assertEquals("bbb", context.get(classifier, Object.class).orElse(null));
        assertEquals("ccc", context.get(String.class).orElse(null));
    }

    @Test
    public void emptyParent() throws Exception {
        ContextualRegistry parent = ContextualRegistry.create();
        ContextualRegistry context = ContextualRegistry.create(parent);
        assertFalse(context.get(String.class).isPresent());
        context.register("aaa");
        assertEquals("aaa", context.get(String.class).orElse(null));
    }

    @Test
    public void testParent() throws Exception {
        ContextualRegistry parent = ContextualRegistry.create();
        parent.register("ppp");
        ContextualRegistry context = ContextualRegistry.create(parent);
        assertEquals("ppp", context.get(String.class).orElse(null));
        context.register(1);
        assertEquals("ppp", context.get(String.class).orElse(null));
        context.register("aaa");
        assertEquals("aaa", context.get(String.class).orElse(null));
        assertEquals("ppp", parent.get(String.class).orElse(null));
    }

    @Test
    public void testParentWithClassifier() throws Exception {
        String classifier = "classifier";
        ContextualRegistry parent = ContextualRegistry.create();
        parent.register(classifier, "ppp");
        ContextualRegistry context = ContextualRegistry.create(parent);
        assertEquals("ppp", context.get(classifier, String.class).orElse(null));
        context.register(classifier, 1);
        assertEquals("ppp", context.get(classifier, String.class).orElse(null));
        context.register(classifier, "aaa");
        assertEquals("aaa", context.get(classifier, String.class).orElse(null));
        assertEquals("ppp", parent.get(classifier, String.class).orElse(null));
    }

    @Test
    public void testSupply() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        ContextualRegistry context = ContextualRegistry.create();
        context.register(1);
        Date date = new Date();
        context.register(date);
        context.register("aaa");
        context.supply(String.class, () -> {
            counter.incrementAndGet();
            return "bbb";
        });
        context.register(2);
        assertEquals(date, context.get(Date.class).orElse(null));
        assertEquals(0, counter.get());
        assertEquals("bbb", context.get(String.class).orElse(null));
        assertEquals(1, counter.get());
        assertEquals("bbb", context.get(String.class).orElse(null));
        assertEquals(1, counter.get());
        assertEquals(date, context.get(Date.class).orElse(null));
        assertEquals("bbb", context.get(String.class).orElse(null));
        assertEquals(1, counter.get());
    }

    @Test
    public void testSupplyClassifier() throws Exception {
        String classifier = "classifier";
        AtomicInteger counter = new AtomicInteger(0);
        ContextualRegistry context = ContextualRegistry.create();
        context.register(classifier, 1);
        Date date = new Date();
        context.register(classifier, date);
        context.register(classifier, "aaa");
        context.supply(classifier, String.class, () -> {
            counter.incrementAndGet();
            return "bbb";
        });
        context.register(classifier, 2);
        assertEquals(date, context.get(classifier, Date.class).orElse(null));
        assertEquals(0, counter.get());
        assertEquals("bbb", context.get(classifier, String.class).orElse(null));
        assertEquals(1, counter.get());
        assertEquals("bbb", context.get(classifier, String.class).orElse(null));
        assertEquals(1, counter.get());
        assertEquals(date, context.get(classifier, Date.class).orElse(null));
        assertEquals("bbb", context.get(classifier, String.class).orElse(null));
        assertEquals(1, counter.get());
    }
}
