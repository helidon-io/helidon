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
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@AddBean(TestPerMethod.Instances.class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@HelidonTest
class TestPerMethod {

    @Inject
    private Instances instances;

    @Test
    void firstTest() {
        assertThat(instances.add(this), is(true));
    }

    @Test
    void secondTest() {
        assertThat(instances.add(this), is(true));
    }

    @ApplicationScoped
    static class Instances {
        final Set<Integer> instances = new HashSet<>();

        boolean add(Object instance) {
            return instances.add(System.identityHashCode(instance));
        }
    }
}
