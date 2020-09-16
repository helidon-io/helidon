/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.junit5;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Extension;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@HelidonTest(resetPerTest = true)
class TestPerMethod {
    private final static List<SeContainer> ALL_CONTAINERS = new LinkedList<>();

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
    @AddBean(TestPerMethod.MyBean.class)
    void testWithAdditionalConfig() {
        String configured = CDI.current()
                .select(MyBean.class)
                .get()
                .configured();

        assertThat(configured, is("value-1"));
    }

    @Test
    @AddExtension(TestPerMethod.MyExtension.class)
    void testCustomExtension() {
        assertThat("Extension should have been called, as it observes application scope", MyExtension.called, is(true));
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

        void observer(@Observes @Initialized(ApplicationScoped.class) final Object event) {
            called = true;
        }
    }
}
