/*
 * Copyright (c) 2017, 2025 Oracle and/or its affiliates.
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
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link Config#empty()} implementation.
 */
public class ConfigEmptyTest {

    @Test
    public void testKey() {
        assertThat(Config.empty().get("one.two").key().toString(), is("one.two"));
    }

    @Test
    public void testChildren() {
        // updated based on javadoc, it should throw a missing value exception
        assertThrows(MissingValueException.class, () -> Config.empty().asNodeList().get());
    }

    @Test
    public void testTraverse() {
        assertThat(Config.empty().traverse().count(), is(0L));
    }

    @Test
    public void testAsString() {
        assertThrows(MissingValueException.class, () -> {
            //separate lines to see the line that fails
            Config empty = Config.empty();
            empty.asString().get();
        });
    }

    @Test
    public void testAsStringDefault() {
        assertThat(Config.empty().asString().orElse("default"), is("default"));
    }

    @Test
    public void testAsInt() {
        assertThrows(MissingValueException.class, () -> {
            Config.empty().asInt().get();
        });
    }

    @Test
    public void testAsIntDefault() {
        assertThat(Config.empty().asInt().orElse(5), is(5));
    }

    @Test
    public void testAsStringList() {
        assertThrows(MissingValueException.class,
                     () -> Config.empty().asList(String.class).get());
    }

    @Test
    public void testAsStringListDefault() {
        List<String> list = List.of("record");

        // config is empty, we should get the default
        assertThat(Config.empty().asList(String.class).orElse(list), hasItem("record"));
    }

    @Test
    public void testAsIntList() {
        // as documented - if empty, throw a missing value
        assertThrows(MissingValueException.class, () -> Config.empty().asList(Integer.class).get());
    }

    @Test
    public void testAsIntListDefault() {
        List<Integer> list = List.of(5);

        // empty config does not exist, i.e. it uses a default
        assertThat(Config.empty().asList(Integer.class).orElse(list), hasItem(5));
    }

    @Test
    public void testType() {
        // empty config is not present, i.e. it should be missing by definition
        assertThat(Config.empty().type(), is(Config.Type.MISSING));
    }

    @Test
    public void testMap() {
        assertThrows(MissingValueException.class, () -> {
            Config.empty().asString().as(ConfigMappers::toBigInteger).get();
        });
    }

    @Test
    public void testAsStringSupplier() {
        assertThrows(MissingValueException.class, () -> {
            // separate lines to see which statement fails
            Config empty = Config.empty();
            Supplier<String> supp = empty.asString().supplier();
            supp.get();
        });
    }

    @Test
    public void testAsStringDefaultSupplier() {
        assertThat(Config.empty()
                           .asString()
                           .supplier("default")
                           .get(),
                   is("default"));
    }

    @Test
    public void testAsIntSupplier() {
        assertThrows(MissingValueException.class, () -> {
            Config.empty().asInt().supplier().get();
        });
    }

    @Test
    public void testAsIntDefaultSupplier() {
        assertThat(Config.empty()
                           .asInt()
                           .supplier(5)
                           .get(),
                   is(5));
    }

    @Test
    public void testAsStringListDefaultSupplier() {
        List<String> list = List.of("record");

        // once again fixed according to javadoc of `supplier(T)` method, if the config does not have a value, use default
        assertThat(Config.empty()
                           .asList(String.class)
                           .supplier(list)
                           .get(),
                   hasItem("record"));
    }

    @Test
    public void testAsIntListSupplier() {
        assertThrows(MissingValueException.class,
                     () -> Config.empty()
                           .asList(Integer.class)
                           .supplier()
                           .get());
    }

    @Test
    public void testAsIntListDefaultSupplier() {
        List<Integer> list = List.of(5);

        assertThat(Config.empty()
                           .asList(Integer.class)
                           .supplier(list)
                           .get(),
                   hasItem(5));
    }

    @Test
    public void testMapSupplier() {
        assertThrows(MissingValueException.class, () -> {
            Config.empty().asString().as(ConfigMappers::toBigInteger).supplier().get();
        });
    }
}
