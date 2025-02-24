/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.testing.testng;

import java.util.HashSet;
import java.util.Set;

import io.helidon.microprofile.testing.testng.AddBean;
import io.helidon.microprofile.testing.testng.HelidonTest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test executed programmatically from {@link TestImplicitReset}.
 */
@EnabledIfParameter(key = "TestImplicitReset", value = "true")
@HelidonTest
public class TestImplicitResetOrderExtra {

    private final static Set<Integer> CONTAINERS = new HashSet<>();
    private static int count = 0;

    @Test(priority = 1)
    void first() {
        assertThat(++count, is(1));
        assertThat(CONTAINERS.add(System.identityHashCode(CDI.current())), is(true));
    }

    @Test(priority = 2)
    @AddBean(DummyBean.class)
    void secondTest() {
        assertThat(++count, is(2));
        assertThat(CONTAINERS.add(System.identityHashCode(CDI.current())), is(true));
    }

    @Test(priority = 3)
    void thirdTest() {
        assertThat(++count, is(3));
        assertThat(CONTAINERS.add(System.identityHashCode(CDI.current())), is(true));
    }

    @Test(priority = 4)
    @AddBean(DummyBean.class)
    void fourthTest() {
        assertThat(++count, is(4));
        assertThat(CONTAINERS.add(System.identityHashCode(CDI.current())), is(true));
    }

    @Test(priority = 5)
    void fifthTest() {
        assertThat(++count, is(5));
        assertThat(CONTAINERS.add(System.identityHashCode(CDI.current())), is(true));
    }

    @AfterClass
    static void afterClass() {
        CONTAINERS.clear();
        count = 0;
    }

    @ApplicationScoped
    static class DummyBean {
    }
}
