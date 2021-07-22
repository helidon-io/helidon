/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.core;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

public class InstanceTest {

    @Test
    public void shouldCreateSingletonSupplier() {
        Value singleton = new Value();
        Supplier<Value> supplier = Instance.singleton(singleton);
        assertThat(supplier, is(notNullValue()));
        assertThat(supplier.get(), is(sameInstance(singleton)));
    }

    @Test
    public void shouldSupplySameSingleton() {
        Value singleton = new Value();
        Supplier<Value> supplier = Instance.singleton(singleton);

        assertThat(supplier.get(), is(sameInstance(singleton)));
        assertThat(supplier.get(), is(sameInstance(singleton)));
        assertThat(supplier.get(), is(sameInstance(singleton)));
    }

    @Test
    public void shouldCreateSingletonSupplierFromClass() {
        Supplier<Value> supplier = Instance.singleton(Value.class);
        assertThat(supplier, is(notNullValue()));
        assertThat(supplier.get(), is(instanceOf(Value.class)));
    }

    @Test
    public void shouldSupplySameSingletonFromClass() {
        Supplier<Value> supplier = Instance.singleton(Value.class);
        Object value = supplier.get();

        assertThat(value, is(instanceOf(Value.class)));
        assertThat(supplier.get(), is(sameInstance(value)));
        assertThat(supplier.get(), is(sameInstance(value)));
    }

    @Test
    public void shouldCreateNewInstanceSupplier() {
        Supplier<Value> supplier = Instance.create(Value.class);
        assertThat(supplier, is(notNullValue()));
        assertThat(supplier.get(), is(instanceOf(Value.class)));
    }

    @Test
    public void shouldSupplyNewInstance() {
        Supplier<Value> supplier = Instance.create(Value.class);
        Object valueOne = supplier.get();
        Object valueTwo = supplier.get();

        assertThat(valueOne, is(instanceOf(Value.class)));
        assertThat(valueTwo, is(instanceOf(Value.class)));
        assertThat(valueOne, is(not(sameInstance(valueTwo))));
    }

    public static class Value {
    }
}
