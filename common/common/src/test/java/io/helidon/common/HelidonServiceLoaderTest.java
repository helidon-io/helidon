/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.util.List;
import java.util.ServiceLoader;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link HelidonServiceLoader}.
 */
class HelidonServiceLoaderTest {
    private static ServiceLoader<ServiceInterface> javaLoader;

    @BeforeAll
    static void initClass() {
        javaLoader = ServiceLoader.load(ServiceInterface.class);
    }

    @Test
    void testJavaLoader() {
        List<ServiceInterface> loaded = HelidonServiceLoader.create(javaLoader).asList();

        assertThat(loaded, hasSize(2));
        assertThat(loaded.get(0).message(), is(ServiceImpl2.class.getName()));
        assertThat(loaded.get(1).message(), is(ServiceImpl1.class.getName()));
    }

    @Test
    void testCustomService() {
        List<ServiceInterface> loaded = HelidonServiceLoader.builder(javaLoader)
                .addService(new ServiceImpl3())
                .build()
                .asList();

        assertThat(loaded, hasSize(3));
        assertThat(loaded.get(0).message(), is(ServiceImpl2.class.getName()));
        assertThat(loaded.get(1).message(), is(ServiceImpl3.class.getName()));
        assertThat(loaded.get(2).message(), is(ServiceImpl1.class.getName()));
    }

    @Test
    void testCustomServiceWithCustomPrio() {
        List<ServiceInterface> loaded = HelidonServiceLoader.builder(javaLoader)
                .addService(new ServiceImpl3(), 1024)
                .build()
                .asList();

        assertThat(loaded, hasSize(3));
        assertThat(loaded.get(0).message(), is(ServiceImpl3.class.getName()));
        assertThat(loaded.get(1).message(), is(ServiceImpl2.class.getName()));
        assertThat(loaded.get(2).message(), is(ServiceImpl1.class.getName()));
    }

    @Test
    void testExcludeService() {
        List<ServiceInterface> loaded = HelidonServiceLoader.builder(javaLoader)
                .addService(new ServiceImpl3())
                .addExcludedClass(ServiceImpl2.class)
                .build()
                .asList();

        assertThat(loaded, hasSize(2));
        assertThat(loaded.get(0).message(), is(ServiceImpl3.class.getName()));
        assertThat(loaded.get(1).message(), is(ServiceImpl1.class.getName()));
    }

    @Test
    void testExcludeServiceNames() {
        List<ServiceInterface> loaded = HelidonServiceLoader.builder(javaLoader)
                .addService(new ServiceImpl3())
                .addExcludedClassName(ServiceImpl1.class.getName())
                .addExcludedClassName(ServiceImpl3.class.getName())
                .build()
                .asList();

        assertThat(loaded, hasSize(1));
        assertThat(loaded.get(0).message(), is(ServiceImpl2.class.getName()));
    }

    @Test
    void testWithoutSystemServiceLoader() {
        List<ServiceInterface> loaded = HelidonServiceLoader.builder(javaLoader)
                .addService(new ServiceImpl3())
                .addService(new ServiceImpl2())
                .useSystemServiceLoader(false)
                .build()
                .asList();

        assertThat(loaded, hasSize(2));
        assertThat(loaded.get(0).message(), is(ServiceImpl2.class.getName()));
        assertThat(loaded.get(1).message(), is(ServiceImpl3.class.getName()));
    }

    @Test
    void testUniqueImplementations() {
        String TEST_STRING = "custom messsage";

        List<ServiceInterface> loaded = HelidonServiceLoader.builder(javaLoader)
                .addService(new ServiceImpl2(TEST_STRING))
                .build()
                .asList();

        assertThat(loaded, hasSize(2));
        assertThat(loaded.get(0).message(), is(TEST_STRING));
        assertThat(loaded.get(1).message(), is(ServiceImpl1.class.getName()));
    }

    @Test
    void testNoUniqueImplementations() {
        String TEST_STRING = "custom messsage";

        List<ServiceInterface> loaded = HelidonServiceLoader.builder(javaLoader)
                .addService(new ServiceImpl2(TEST_STRING), 1024)
                .replaceImplementations(false)
                .build()
                .asList();

        assertThat(loaded, hasSize(3));
        assertThat(loaded.get(0).message(), is(TEST_STRING));
        assertThat(loaded.get(1).message(), is(ServiceImpl2.class.getName()));
        assertThat(loaded.get(2).message(), is(ServiceImpl1.class.getName()));
    }

    @Test
    void testNegativePrioFails() {
        assertThrows(IllegalArgumentException.class, () -> HelidonServiceLoader.builder(javaLoader)
                .addService(new ServiceImpl2("something"), -11)
                .replaceImplementations(false)
                .build()
                .asList());
    }

    @Test
    void testZeropPrioWorks() {
        HelidonServiceLoader.builder(javaLoader)
                .addService(new ServiceImpl2("something"), 0)
                .replaceImplementations(false)
                .build()
                .asList();
    }

}

