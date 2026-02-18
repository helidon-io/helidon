/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.spi.ServerFeature;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RoutingTest
class TestFilterTest {

    private final DirectClient client;

    TestFilterTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.get("/get", (req, res) -> {
                res.status(Status.OK_200).send();
        });
    }

    @SetUpFeatures
    static List<ServerFeature> features() {
         return List.of(new TestFeature());
    }

    @Test
    void testGet() {
        Http1ClientResponse response = client.get("/get").request();
        assertThat(response.status(), is(Status.OK_200));
        assertThat(TestFilter.counter.get(), is(1));
    }

    private static class TestFeature implements ServerFeature {

        @Override
        public void setup(ServerFeatureContext featureContext) {
            TestFilter filter = new TestFilter();
            featureContext.socket(WebServer.DEFAULT_SOCKET_NAME).httpRouting().addFilter(filter);
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public String type() {
            return "test";
        }
    }

    private static class TestFilter implements Filter {

        private static AtomicInteger counter = new AtomicInteger(0);

        @Override
        public void filter(FilterChain arg0, RoutingRequest arg1, RoutingResponse arg2) {
            counter.incrementAndGet();
            arg0.proceed();
        }
        
    }
}
