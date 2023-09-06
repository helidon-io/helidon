/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.server;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.AfterStop;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Extension;
import org.junit.jupiter.api.Test;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddExtension(WebServerLifecycleAnnotationTest.TestExtension.class)
class WebServerLifecycleAnnotationTest {
    private static final TestFeature FEATURE = new TestFeature();
    private static ServerCdiExtension server;

    @AfterStop
    static void afterAll() {
        assertThat(server, notNullValue());
        assertThat(server.started(), is(false));
        assertThat("Before start should only have been called on server startup", FEATURE.beforeStart.get(), is(1));
        assertThat("After stop should have been called on server stop", FEATURE.afterStop.get(), is(1));
    }

    @Test
    void testLifecycleMethods() {
        assertThat("Before start should have been called on server startup", FEATURE.beforeStart.get(), is(1));
        assertThat("After stop should not have been called on server startup", FEATURE.afterStop.get(), is(0));
    }

    public static final class TestExtension implements Extension {
        void registerService(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class) Object adv,
                             ServerCdiExtension server) {
            server.serverRoutingBuilder().addFeature(FEATURE);
            WebServerLifecycleAnnotationTest.server = server;
        }
    }

    private static final class TestFeature implements HttpFeature {
        final AtomicInteger beforeStart = new AtomicInteger();
        final AtomicInteger afterStop = new AtomicInteger();

        @Override
        public void setup(HttpRouting.Builder routing) {
            routing.get("/", (req, res) -> res.send("works"));
        }

        @Override
        public void beforeStart() {
            beforeStart.incrementAndGet();
        }

        @Override
        public void afterStop() {
            afterStop.incrementAndGet();
        }
    }
}
