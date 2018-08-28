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

package io.helidon.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import static org.hamcrest.MatcherAssert.assertThat;


import static org.hamcrest.Matchers.empty;
import static org.hamcrest.core.Is.is;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        assertThat(Config.empty().asNodeList().size(), is(0));
    }

    @Test
    public void testTraverse() {
        assertThat(Config.empty().traverse().count(), is(0L));
    }

    @Test
    public void testAsString() {
        Assertions.assertThrows(ConfigMappingException.class, () -> {
            //separate lines to see the line that fails
            Config empty = Config.empty();
            empty.asString();
        });
    }

    @Test
    public void testAsStringDefault() {
        Assertions.assertThrows(ConfigMappingException.class, () -> {
            assertThat(Config.empty().asString("default"), is("default"));
        });
    }

    @Test
    public void testAsInt() {
        Assertions.assertThrows(ConfigMappingException.class, () -> {
            Config.empty().asInt();
        });
    }

    @Test
    public void testAsIntDefault() {
        Assertions.assertThrows(ConfigMappingException.class, () -> {
            assertThat(Config.empty().asInt(5), is(5));
        });
    }

    @Test
    public void testAsStringList() {
        assertThat(Config.empty().asStringList(), is(empty()));
    }

    @Test
    public void testAsStringListDefault() {
        List<String> list = new ArrayList<String>() {{
            add("record");
        }};
        assertThat(Config.empty().asStringList(list), is(empty()));
    }

    @Test
    public void testAsIntList() {
        assertThat(Config.empty().asList(Integer.class), is(empty()));
    }

    @Test
    public void testAsIntListDefault() {
        List<Integer> list = new ArrayList<Integer>() {{
            add(5);
        }};
        assertThat(Config.empty().asList(Integer.class, list), is(empty()));
    }

    @Test
    public void testType() {
        assertThat(Config.empty().type(), is(Config.Type.OBJECT));
    }

    @Test
    public void testMap() {
        Assertions.assertThrows(ConfigMappingException.class, () -> {
            Config.empty().map(ConfigMappers::toBigInteger);
        });
    }

    @Test
    public void testKeyViaSupplier() {
        assertThat(Config.empty().nodeSupplier().get().get().get("one.two").key().toString(), is("one.two"));
    }

    @Test
    public void testChildrenSupplier() {
        assertThat(Config.empty().asNodeListSupplier().get().size(), is(0));
    }

    @Test
    public void testTraverseSupplier() {
        assertThat(Config.empty().nodeSupplier().get().get().traverse().count(), is(0L));
    }

    @Test
    public void testAsStringSupplier() {
        Assertions.assertThrows(ConfigMappingException.class, () -> {
            // separate lines to see which statement fails
            Config empty = Config.empty();
            Supplier<String> supp = empty.asStringSupplier();
            supp.get();
        });
    }

    @Test
    public void testAsStringDefaultSupplier() {
        Assertions.assertThrows(ConfigMappingException.class, () -> {
            assertThat(Config.empty().asStringSupplier("default").get(), is("default"));
        });
    }

    @Test
    public void testAsIntSupplier() {
        Assertions.assertThrows(ConfigMappingException.class, () -> {
            Config.empty().asIntSupplier().get();
        });
    }

    @Test
    public void testAsIntDefaultSupplier() {
        Assertions.assertThrows(ConfigMappingException.class, () -> {
            assertThat(Config.empty().asIntSupplier(5).get(), is(5));
        });
    }

    @Test
    public void testAsStringListSupplier() {
        assertThat(Config.empty().asStringListSupplier().get(), is(empty()));
    }

    @Test
    public void testAsStringListDefaultSupplier() {
        List<String> list = new ArrayList<String>() {{
            add("record");
        }};
        assertThat(Config.empty().asStringListSupplier(list).get(), is(empty()));
    }

    @Test
    public void testAsIntListSupplier() {
        assertThat(Config.empty().asListSupplier(Integer.class).get(), is(empty()));
    }

    @Test
    public void testAsIntListDefaultSupplier() {
        List<Integer> list = new ArrayList<Integer>() {{
            add(5);
        }};
        assertThat(Config.empty().asListSupplier(Integer.class, list).get(), is(empty()));
    }

    @Test
    public void testTypeSupplier() {
        assertThat(Config.empty().nodeSupplier().get().get().type(), is(Config.Type.OBJECT));
    }

    @Test
    public void testMapSupplier() {
        Assertions.assertThrows(ConfigMappingException.class, () -> {
            Config.empty().mapSupplier(ConfigMappers::toBigInteger).get();
        });
    }

}
