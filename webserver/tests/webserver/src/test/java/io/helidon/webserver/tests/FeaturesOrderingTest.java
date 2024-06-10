/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.helidon.common.Weighted;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class FeaturesOrderingTest {
    private static final TestServerFeature F_1000 = new TestServerFeature(1000);
    private static final TestServerFeature F_99 = new TestServerFeature(99);
    private static final TestHttpFeature HF_999 = new TestHttpFeature(999);
    private static final TestHttpFeature HF_98 = new TestHttpFeature(98);
    private static final TestFilter FIRST = new TestFilter("routing-first", 100);
    private static final TestFilter SECOND = new TestFilter("routing-second", 100);

    private static final TestFilter F_FILTER_1000 = F_1000.filter();
    private static final TestFilter F_FILTER_99 = F_99.filter();
    private static final TestFilter HF_FILTER_999 = HF_999.filter();
    private static final TestFilter HF_FILTER_98 = HF_98.filter();

    private final Http1Client client;

    FeaturesOrderingTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder ws) {
        ws.addFeature(F_99)
                .addFeature(F_1000);
    }

    @SetUpRoute
    static void setUpRoute(HttpRouting.Builder routing) {
        routing.addFeature(HF_98)
                .addFeature(HF_999)
                .get("/no-service", (req, res) -> res.send("routing"));
        updateRouting(routing, "routing-1", 0, FIRST);
        updateRouting(routing, "routing-2", 0, SECOND);
    }

    @Test
    void testServiceRegistration() {
        assertThat(client.get("/service")
                           .requestEntity(String.class), is("service:server-feature:1000"));
    }

    @Test
    void testServiceWithPathRegistration() {
        assertThat(client.get("/path/service")
                           .requestEntity(String.class), is("service:server-feature:1000"));
    }

    @Test
    void testRouteRegistration() {
        assertThat(client.get("/route")
                           .requestEntity(String.class), is("route:server-feature:1000"));
    }

    @Test
    void testErrorRegistration() {
        assertThat(client.get("/error")
                           .requestEntity(String.class), is("error:server-feature:1000"));
    }

    @Test
    void testFilterRegistration() {
        List<TestFilter> filters = new CopyOnWriteArrayList<>();
        FIRST.filters(filters);
        SECOND.filters(filters);
        F_FILTER_1000.filters(filters);
        F_FILTER_99.filters(filters);
        HF_FILTER_999.filters(filters);
        HF_FILTER_98.filters(filters);

        assertThat(client.get("/no-service")
                           .requestEntity(String.class),
                   is("routing"));

        /*
        server-feature(1000)
        http-feature(999)
        routing-first(N/A)
        routing-second(N/A)
        server-feature(99)
        http-feature(98)
         */

        /*
         now the order of filers should be as follows:
         F_FILTER_1000 - highest weight
         HF_FILTER_999
         FIRST      - default weight (registered through routing)
         SECOND     - dtto
         F_FILTER_99 - lower than default, should be handled after default routing
         HF_FILTER_98
         */
        assertThat("All 6 filters should have been called. Actual list: " + filters, filters.size(), is(6));
        assertThat("First should be filter from server feature, weight 1000. Actual list: " + filters,
                   filters.get(0),
                   sameInstance(F_FILTER_1000));
        assertThat("Second should be filter from HTTP feature, weight 999. Actual list: " + filters,
                   filters.get(1),
                   sameInstance(HF_FILTER_999));
        assertThat("Third should be first filter registered to routing. Actual list: " + filters,
                   filters.get(2),
                   sameInstance(FIRST));
        assertThat("Fourth should be second filter registered to routing. Actual list: " + filters,
                   filters.get(3),
                   sameInstance(SECOND));
        assertThat("Fifth should be filter from server feature, weight 99. Actual list: " + filters,
                   filters.get(4),
                   sameInstance(F_FILTER_99));
        assertThat("Last should be filter from HTTP feature, weight 98. Actual list: " + filters,
                   filters.get(5),
                   sameInstance(HF_FILTER_98));

    }

    private static void updateRouting(HttpRouting.Builder routing, String type, int weight, Filter filter) {
        routing.addFilter(filter)
                .register(new TestHttpService(type, weight))
                .register("/path", new TestHttpService(type, weight))
                .route(HttpRoute.builder()
                               .path("/route")
                               .handler((req, res) -> res.send("route:" + type + ":" + weight))
                               .build())
                .get("/error", (req, res) -> {
                    throw new TestException(type, weight);
                })
                .error(TestException.class, (req, res, throwable) -> {
                    res.send("error:" + throwable.type + ":" + throwable.weight);
                });
    }

    private static class TestHttpService implements HttpService {
        private final String type;
        private final int weight;

        private TestHttpService(String type, int weight) {
            this.type = type;
            this.weight = weight;
        }

        @Override
        public void routing(HttpRules rules) {
            rules.get("/service", (req, res) -> res.send("service:" + type + ":" + weight));
        }
    }

    private static class TestException extends RuntimeException {
        private final String type;
        private final int weight;

        private TestException(String type, int weight) {
            this.type = type;
            this.weight = weight;
        }
    }

    private static class TestFilter implements Filter {
        private final String message;
        private final int weight;

        private volatile List<TestFilter> filters;

        private TestFilter(String message, int weight) {
            this.message = message;
            this.weight = weight;
        }

        @Override
        public void filter(FilterChain filterChain, RoutingRequest routingRequest, RoutingResponse routingResponse) {
            if (filters != null) {
                filters.add(this);
            }
            filterChain.proceed();
        }

        public void filters(List<TestFilter> filters) {
            this.filters = filters;
        }

        @Override
        public String toString() {
            return message + "(" + weight + ")";
        }
    }

    private static class TestHttpFeature implements HttpFeature, Weighted {
        private final int weight;
        private final TestFilter filter;

        private TestHttpFeature(int weight) {
            this.weight = weight;
            this.filter = new TestFilter("http-feature", weight);
        }

        @Override
        public void setup(HttpRouting.Builder routing) {
            updateRouting(routing, "http-feature", weight, filter);
        }

        @Override
        public double weight() {
            return weight;
        }

        @Override
        public String toString() {
            return "http-feature(" + weight + ")";
        }

        TestFilter filter() {
            return filter;
        }
    }

    private static class TestServerFeature implements ServerFeature, Weighted {
        private final int weight;
        private final TestFilter filter;

        private TestServerFeature(int weight) {
            this.weight = weight;
            this.filter = new TestFilter("server-feature", weight);
        }

        @Override
        public void setup(ServerFeatureContext featureContext) {
            updateRouting(featureContext.socket(WebServer.DEFAULT_SOCKET_NAME)
                                  .httpRouting(), "server-feature", weight, filter);
        }

        @Override
        public String name() {
            return toString();
        }

        @Override
        public String type() {
            return toString();
        }

        @Override
        public double weight() {
            return weight;
        }

        @Override
        public String toString() {
            return "server-feature(" + weight + ")";
        }

        TestFilter filter() {
            return filter;
        }
    }
}
