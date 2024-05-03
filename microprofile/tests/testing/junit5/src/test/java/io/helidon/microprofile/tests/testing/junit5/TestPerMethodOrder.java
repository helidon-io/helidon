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

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(TestPerMethodOrder.Counter.class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestPerMethodOrder {

    @Inject
    Counter counter;

    @Test
    @Order(1)
    void firstTest() {
        assertThat(counter.incrementAndGet(), is(1));
    }

    @Test
    @Order(2)
    void secondTest() {
        assertThat(counter.incrementAndGet(), is(2));
    }

    @Test
    @Order(3)
    void thirdTest() {
        assertThat(counter.incrementAndGet(), is(3));
    }

    @ApplicationScoped
    static class Counter {

        private int i;

        int incrementAndGet() {
            return ++i;
        }
    }
}
