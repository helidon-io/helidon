/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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

package io.helidon.common.context;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

/**
 * Tests {@link io.helidon.common.context.ListContext} and {@link Context}.
 */
public class ListContextTest {

    @Test
    public void testId() {
        Context first = Context.create();
        Context second = Context.create();
        Context third = Context.create(first);
        Context fourth = Context.create(second);
        Context fifth = Context.create(fourth);

        assertThat(first.id(), not(second.id()));
        assertThat(first.id(), not(third.id()));
        assertThat(first.id(), not(fourth.id()));
        assertThat(first.id(), not(fifth.id()));

        assertThat(second.id(), not(third.id()));
        assertThat(second.id(), not(fourth.id()));
        assertThat(second.id(), not(fifth.id()));

        assertThat(third.id(), not(fourth.id()));
        assertThat(third.id(), not(fifth.id()));

        assertThat(fourth.id(), not(fifth.id()));
    }

    @Test
    public void create() {
        assertThat(Context.create(), notNullValue());
        assertThat(Context.create(null), notNullValue());
        assertThat(Context.create(Context.create()), notNullValue());
    }

    @Test
    public void registerAndGetLast() {
        Context context = Context.create();
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
    public void unregisterOldInstanceKeepsReplacement() {
        Context context = Context.create();
        TestValue first = new TestValue();
        TestValue second = new TestValue();

        context.register(first);
        context.register(second);

        assertThat(first, is(second));
        assertThat(first, not(sameInstance(second)));
        assertThat(context.get(TestValue.class).get(), sameInstance(second));

        context.unregister(first);

        assertThat(context.get(TestValue.class).get(), sameInstance(second));

        context.unregister(second);

        assertThat(context.get(TestValue.class), is(Optional.empty()));
    }

    @Test
    public void unregisterOldClassifiedInstanceKeepsReplacement() {
        Context context = Context.create();
        String classifier = "classifier";
        TestValue first = new TestValue();
        TestValue second = new TestValue();

        context.register(classifier, first);
        context.register(classifier, second);

        assertThat(first, is(second));
        assertThat(first, not(sameInstance(second)));
        assertThat(context.get(classifier, TestValue.class).get(), sameInstance(second));

        context.unregister(classifier, first);

        assertThat(context.get(classifier, TestValue.class).get(), sameInstance(second));

        context.unregister(classifier, second);

        assertThat(context.get(classifier, TestValue.class), is(Optional.empty()));
    }

    @Test
    public void unregisterSuppliedInstanceUsesLoadedIdentity() {
        Context context = Context.create();
        AtomicInteger counter = new AtomicInteger();
        TestValue supplied = new TestValue();
        TestValue equal = new TestValue();

        context.supply(TestValue.class, () -> {
            counter.incrementAndGet();
            return supplied;
        });

        context.unregister(supplied);

        assertThat(counter.get(), is(0));
        assertThat(context.get(TestValue.class).get(), sameInstance(supplied));
        assertThat(counter.get(), is(1));

        context.unregister(equal);

        assertThat(context.get(TestValue.class).get(), sameInstance(supplied));
        assertThat(counter.get(), is(1));

        context.unregister(supplied);

        assertThat(context.get(TestValue.class), is(Optional.empty()));
        assertThat(counter.get(), is(1));
    }

    @Test
    public void unregisterClassifiedSuppliedInstanceUsesLoadedIdentity() {
        Context context = Context.create();
        String classifier = "classifier";
        AtomicInteger counter = new AtomicInteger();
        TestValue supplied = new TestValue();
        TestValue equal = new TestValue();

        context.supply(classifier, TestValue.class, () -> {
            counter.incrementAndGet();
            return supplied;
        });

        context.unregister(classifier, supplied);

        assertThat(counter.get(), is(0));
        assertThat(context.get(classifier, TestValue.class).get(), sameInstance(supplied));
        assertThat(counter.get(), is(1));

        context.unregister(classifier, equal);

        assertThat(context.get(classifier, TestValue.class).get(), sameInstance(supplied));
        assertThat(counter.get(), is(1));

        context.unregister(classifier, supplied);

        assertThat(context.get(classifier, TestValue.class), is(Optional.empty()));
        assertThat(counter.get(), is(1));
    }

    @Test
    public void unregisterRemovesAllRegistrationsForTheSameInstance() {
        Context context = Context.create();
        TestValue supplied = new TestValue();

        context.supply(TestValueInterface.class, () -> supplied);
        assertThat(context.get(TestValueInterface.class).get(), sameInstance(supplied));

        context.register(supplied);
        assertThat(context.get(TestValueInterface.class).get(), sameInstance(supplied));

        context.unregister(supplied);

        assertThat(context.get(TestValueInterface.class), is(Optional.empty()));
        assertThat(context.get(TestValue.class), is(Optional.empty()));
    }

    @Test
    public void unregisterRemovesAllClassifiedRegistrationsForTheSameInstance() {
        Context context = Context.create();
        String classifier = "classifier";
        TestValue supplied = new TestValue();

        context.supply(classifier, TestValueInterface.class, () -> supplied);
        assertThat(context.get(classifier, TestValueInterface.class).get(), sameInstance(supplied));

        context.register(classifier, supplied);
        assertThat(context.get(classifier, TestValueInterface.class).get(), sameInstance(supplied));

        context.unregister(classifier, supplied);

        assertThat(context.get(classifier, TestValueInterface.class), is(Optional.empty()));
        assertThat(context.get(classifier, TestValue.class), is(Optional.empty()));
    }

    @Test
    public void defaultUnregisterRejectsNulls() {
        Context context = new DefaultUnregisterContext();

        assertThrows(NullPointerException.class, () -> context.unregister((Object) null));
        assertThrows(NullPointerException.class, () -> context.unregister(null, new TestValue()));
        assertThrows(NullPointerException.class, () -> context.unregister("classifier", (TestValue) null));
    }

    @Test
    public void registerAndGetLastClassifier() {
        Context context = Context.create();
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
        Context parent = Context.create();
        Context context = Context.create(parent);
        assertThat(context.get(String.class), is(Optional.empty()));
        context.register("aaa");
        assertThat(context.get(String.class), is(Optional.of("aaa")));
    }

    @Test
    public void testParent() {
        Context parent = Context.create();
        parent.register("ppp");
        Context context = Context.create(parent);
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
        Context parent = Context.create();
        parent.register(classifier, "ppp");
        Context context = Context.create(parent);
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
        Context context = Context.create();
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
        Context context = Context.create();
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

    private static class TestValue implements TestValueInterface {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof TestValue;
        }

        @Override
        public int hashCode() {
            return TestValue.class.hashCode();
        }
    }

    private interface TestValueInterface {
    }

    private static class DefaultUnregisterContext implements Context {
        @Override
        public <T> void register(T instance) {
        }

        @Override
        public <T> void supply(Class<T> type, Supplier<T> supplier) {
        }

        @Override
        public <T> Optional<T> get(Class<T> type) {
            return Optional.empty();
        }

        @Override
        public <T> void register(Object classifier, T instance) {
        }

        @Override
        public <T> void supply(Object classifier, Class<T> type, Supplier<T> supplier) {
        }

        @Override
        public <T> Optional<T> get(Object classifier, Class<T> type) {
            return Optional.empty();
        }

        @Override
        public String id() {
            return "default-unregister";
        }
    }
}
