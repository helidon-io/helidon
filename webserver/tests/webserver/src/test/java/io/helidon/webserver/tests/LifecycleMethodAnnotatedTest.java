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

package io.helidon.webserver.tests;

import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.AfterStop;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.webserver.tests.LifecycleMethodTest.validateAfterStart;
import static io.helidon.webserver.tests.LifecycleMethodTest.validateAfterStop;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class LifecycleMethodAnnotatedTest {
    private static final LifecycleMethodTest.TestFeature FEATURE = new LifecycleMethodTest.TestFeature();
    private static final LifecycleMethodTest.TestService SERVICE = new LifecycleMethodTest.TestService();
    private static final LifecycleMethodTest.TestRoute ROUTE = new LifecycleMethodTest.TestRoute();
    private static final LifecycleMethodTest.TestHandler HANDLER = new LifecycleMethodTest.TestHandler();
    private static final LifecycleMethodTest.TestFilter FILTER = new LifecycleMethodTest.TestFilter();

    @SetUpRoute
    static void setUpRoute(HttpRouting.Builder http) {
        http.addFeature(FEATURE)
                .register(SERVICE)
                .route(ROUTE)
                .get("/handler", HANDLER)
                .addFilter(FILTER);
    }

    @AfterStop
    static void afterStop() {
        validateAfterStop("Feature", FEATURE);
        validateAfterStop("Service", SERVICE);
        validateAfterStop("Route", ROUTE);
        validateAfterStop("Handler", HANDLER);
        validateAfterStop("Filter", FILTER);
    }

    @Test
    void testBeforeStartCalled(WebClient webClient) {
        ClientResponseTyped<String> request = webClient.get("/feature")
                .request(String.class);

        assertThat(request.entity(), is("feature"));

        validateAfterStart("Feature", FEATURE);
        validateAfterStart("Service", SERVICE);
        validateAfterStart("Route", ROUTE);
        validateAfterStart("Handler", HANDLER);
        validateAfterStart("Filter", FILTER);
    }


}
