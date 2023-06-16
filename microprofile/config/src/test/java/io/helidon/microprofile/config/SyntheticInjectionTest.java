/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.config;

import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for synthetic bean injection
 */
@HelidonTest
@AddExtension(SyntheticInjectionTest.MyExtension.class)
class SyntheticInjectionTest {

    /**
     * Make sure that the registration of synthetic observers doesn't break the config extension.
     */
    @Test
    void testSyntheticObserver() {
        assertTrue(MyExtension.observerCalled, "Synthetic observer wasn't registered");
    }

    public static class MyExtension implements Extension {

        private static volatile boolean observerCalled = false;

        void registerSyntheticObserver(@Observes AfterBeanDiscovery event) {
            event.addObserverMethod()
                    .addQualifier(Initialized.Literal.of(ApplicationScoped.class))
                    .observedType(Object.class)
                    .notifyWith((context) -> observerCalled = true);
        }
    }

}
