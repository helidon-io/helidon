/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link ListContextualRegistry} and {@link ContextualRegistry}.
 */
public class ListContextTest {

    @Test
    public void create() {
        assertThat(ContextualRegistry.create(), notNullValue());
        assertThat(ContextualRegistry.create(null), notNullValue());
        assertThat(ContextualRegistry.create(ContextualRegistry.create()), notNullValue());
    }

    @Test
    public void registerAndGetLast() {
        ContextualRegistry context = ContextualRegistry.create();
        assertThat(context.get(String.class), is(Optional.empty()));
        assertThat(context.get(Integer.class), is(Optional.empty()));
        context.register("aaa");
        assertThat(context.get(String.class), is(Optional.of("aaa")));
        assertThat(context.get(Integer.class), is(Optional.empty()));
        context.register(1);
        assertThat(context.get(String.class), is(Optional.of("aaa")));
        assertThat(context.get(Integer.class), is(Optional.of(1)));
        assertThat(context.get(Object.class), is(Optional.of(1)));
        context.register("bbb");
        assertThat(context.get(String.class), is(Optional.of("bbb")));
        assertThat(context.get(Object.class), is(Optional.of("bbb")));
    }

    @Test
    public void registerAndGetLastClassifier() {
        ContextualRegistry context = ContextualRegistry.create();
        String classifier = "classifier";
        assertThat(context.get(classifier, String.class), is(Optional.empty()));
        assertThat(context.get(classifier, Integer.class), is(Optional.empty()));
        context.register(classifier, "aaa");
        assertThat(context.get(classifier, String.class), is(Optional.of("aaa")));
        assertThat(context.get(String.class), is(Optional.empty()));
        assertThat(context.get(classifier, Integer.class), is(Optional.empty()));
        context.register(classifier, 1);
        assertThat(context.get(classifier, String.class), is(Optional.of("aaa")));
        assertThat(context.get(classifier, Integer.class), is(Optional.of(1)));
        assertThat(context.get(classifier, Object.class), is(Optional.of(1)));
        context.register(classifier, "bbb");
        assertThat(context.get(classifier, String.class), is(Optional.of("bbb")));
        context.register("ccc");
        assertThat(context.get(classifier, String.class), is(Optional.of("bbb")));
        assertThat(context.get(classifier, Object.class), is(Optional.of("bbb")));
        assertThat(context.get(String.class), is(Optional.of("ccc")));
    }

    @Test
    public void emptyParent() {
        ContextualRegistry parent = ContextualRegistry.create();
        ContextualRegistry context = ContextualRegistry.create(parent);
        assertThat(context.get(String.class), is(Optional.empty()));
        context.register("aaa");
        assertThat(context.get(String.class), is(Optional.of("aaa")));
    }

    @Test
    public void testParent() {
        ContextualRegistry parent = ContextualRegistry.create();
        parent.register("ppp");
        ContextualRegistry context = ContextualRegistry.create(parent);
        assertThat(context.get(String.class), is(Optional.of("ppp")));
        context.register(1);
        assertThat(context.get(String.class), is(Optional.of("ppp")));
        context.register("aaa");
        assertThat(context.get(String.class), is(Optional.of("aaa")));
        assertThat(parent.get(String.class), is(Optional.of("ppp")));
    }

    @Test
    public void testParentWithClassifier() {
        String classifier = "classifier";
        ContextualRegistry parent = ContextualRegistry.create();
        parent.register(classifier, "ppp");
        ContextualRegistry context = ContextualRegistry.create(parent);
        assertThat(context.get(classifier, String.class), is(Optional.of("ppp")));
        context.register(classifier, 1);
        assertThat(context.get(classifier, String.class), is(Optional.of("ppp")));
        context.register(classifier, "aaa");
        assertThat(context.get(classifier, String.class), is(Optional.of("aaa")));
        assertThat(parent.get(classifier, String.class), is(Optional.of("ppp")));
    }

    @Test
    public void testSupply() {
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
        assertThat(context.get(Date.class), is(Optional.of(date)));
        assertThat(counter.get(), is(0));
        assertThat(context.get(String.class), is(Optional.of("bbb")));
        assertThat(counter.get(), is(1));
        assertThat(context.get(String.class), is(Optional.of("bbb")));
        assertThat(counter.get(), is(1));
        assertThat(context.get(Date.class), is(Optional.of(date)));
        assertThat(context.get(String.class), is(Optional.of("bbb")));
        assertThat(counter.get(), is(1));
    }

    @Test
    public void testSupplyClassifier() {
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
        assertThat(context.get(classifier, Date.class), is(Optional.of(date)));
        assertThat(counter.get(), is(0));
        assertThat(context.get(classifier, String.class), is(Optional.of("bbb")));
        assertThat(counter.get(), is(1));
        assertThat(context.get(classifier, String.class), is(Optional.of("bbb")));
        assertThat(counter.get(), is(1));
        assertThat(context.get(classifier, Date.class), is(Optional.of(date)));
        assertThat(context.get(classifier, String.class), is(Optional.of("bbb")));
        assertThat(counter.get(), is(1));
    }
}
