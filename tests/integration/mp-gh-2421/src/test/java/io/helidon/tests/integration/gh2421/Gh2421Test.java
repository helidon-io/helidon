/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.gh2421;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test that an application scoped destroyed observer can use {@link jakarta.enterprise.inject.spi.CDI#current()}.
 */
public class Gh2421Test {
    @Test
    void testShutdownWorks() {
        SeContainer container = SeContainerInitializer.newInstance()
                .addBeanClasses(ListenerBean.class)
                .initialize();

        container.close();

        assertThat(ListenerBean.called.get(), is(true));
    }

    @ApplicationScoped
    public static class ListenerBean {
        static AtomicBoolean called = new AtomicBoolean();

        public void method(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
            // need to call CDI.current() as that is not working
            CDI.current().getBeanManager();
            called.set(true);
        }
    }
}
