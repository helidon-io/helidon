/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@AddBean(TestResetPerTest.DummyBean.class)
@HelidonTest(resetPerTest = true)
class TestResetPerTest {
    private final static List<SeContainer> ALL_CONTAINERS = new LinkedList<>();

    private final DummyBean dummyBean;

    @Inject
    TestResetPerTest(DummyBean dummyBean) {
        this.dummyBean = dummyBean;
    }

    @Test
    void testWithParameter(SeContainer container) {
        ALL_CONTAINERS.add(container);
    }

    @Test
    @DisableDiscovery
    void testWithParameterNoDiscovery(SeContainer container) {
        ALL_CONTAINERS.add(container);
    }

    @Test
    @AddConfig(key = "key-1", value = "value-1")
    @AddBean(MyBean.class)
    void testWithAdditionalConfig() {
        String configured = CDI.current()
                .select(MyBean.class)
                .get()
                .configured();

        assertThat(configured, is("value-1"));
    }

    @Test
    @AddExtension(MyExtension.class)
    void testCustomExtension() {
        assertThat(MyExtension.called, is(true));
    }

    @AfterAll
    static void validateContainerInstances() {
        Set<Integer> used = new HashSet<>();
        try {
            for (SeContainer container : ALL_CONTAINERS) {
                if (!used.add(System.identityHashCode(container))) {
                    fail("Container instance used twice: " + container);
                }
            }
        } finally {
            ALL_CONTAINERS.clear();
        }
    }

    @ApplicationScoped
    static class DummyBean {
    }

    @ApplicationScoped
    static class MyBean {
        private final String configured;

        @Inject
        MyBean(@ConfigProperty(name = "key-1") String configured) {
            this.configured = configured;
        }

        String configured() {
            return configured;
        }
    }

    public static class MyExtension implements Extension {
        private static volatile boolean called = false;

        void observer(@Observes @Initialized(ApplicationScoped.class) Object event) {
            called = true;
        }
    }
}
