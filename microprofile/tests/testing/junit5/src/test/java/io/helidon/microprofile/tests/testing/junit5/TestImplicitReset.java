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

package io.helidon.microprofile.tests.testing.junit5;

import java.util.HashSet;
import java.util.Set;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
class TestImplicitReset {

    private final static Set<Integer> CONTAINERS = new HashSet<>();

    @Test
    void first() {
        boolean empty = CONTAINERS.isEmpty();
        assertThat(CONTAINERS.add(System.identityHashCode(CDI.current())), is(empty));
    }

    @Test
    @AddBean(DummyBean.class)
    void secondTest() {
        assertThat(CONTAINERS.add(System.identityHashCode(CDI.current())), is(true));
    }

    @Test
    void thirdTest() {
        boolean empty = CONTAINERS.isEmpty();
        assertThat(CONTAINERS.add(System.identityHashCode(CDI.current())), is(empty));
    }

    @Test
    @AddBean(DummyBean.class)
    void fourthTest() {
        assertThat(CONTAINERS.add(System.identityHashCode(CDI.current())), is(true));
    }

    @Test
    void fifthTest() {
        boolean empty = CONTAINERS.isEmpty();
        assertThat(CONTAINERS.add(System.identityHashCode(CDI.current())), is(empty));
    }

    @AfterAll
    static void afterClass() {
        CONTAINERS.clear();
    }

    @ApplicationScoped
    static class DummyBean {
    }
}
