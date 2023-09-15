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

import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.AfterStop;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Extension;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.tests.server.WebServerLifecycleTest.validateAfterStart;
import static io.helidon.microprofile.tests.server.WebServerLifecycleTest.validateAfterStop;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddExtension(WebServerLifecycleAnnotationTest.TestExtension.class)
class WebServerLifecycleAnnotationTest {
    private static final WebServerLifecycleTest.TestFeature FEATURE = new WebServerLifecycleTest.TestFeature();
    private static final WebServerLifecycleTest.TestService SERVICE = new WebServerLifecycleTest.TestService();
    private static final WebServerLifecycleTest.TestRoute ROUTE = new WebServerLifecycleTest.TestRoute();
    private static final WebServerLifecycleTest.TestHandler HANDLER = new WebServerLifecycleTest.TestHandler();
    private static final WebServerLifecycleTest.TestFilter FILTER = new WebServerLifecycleTest.TestFilter();

    static ServerCdiExtension server;

    @AfterStop
    static void afterAll() {
        assertThat(server, notNullValue());
        assertThat(server.started(), is(false));

        validateAfterStop("Feature", FEATURE);
        validateAfterStop("Service", SERVICE);
        validateAfterStop("Route", ROUTE);
        validateAfterStop("Handler", HANDLER);
        validateAfterStop("Filter", FILTER);
    }

    @Test
    void testLifecycleMethods() {
        validateAfterStart("Feature", FEATURE);
        validateAfterStart("Service", SERVICE);
        validateAfterStart("Route", ROUTE);
        validateAfterStart("Handler", HANDLER);
        validateAfterStart("Filter", FILTER);
    }

    public static final class TestExtension implements Extension {
        void registerService(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class) Object adv,
                             ServerCdiExtension server) {
            WebServerLifecycleAnnotationTest.server = server;
            server.serverRoutingBuilder()
                    .addFeature(FEATURE)
                    .register(SERVICE)
                    .route(ROUTE)
                    .get("/handler", HANDLER)
                    .addFilter(FILTER);
        }
    }
}
