/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.prometheus;

import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.webserver.Routing;
import io.helidon.webserver.testsupport.TestClient;
import io.helidon.webserver.testsupport.TestRequest;
import io.helidon.webserver.testsupport.TestResponse;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import org.hamcrest.core.StringStartsWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PrometheusSupportTest {

    private Routing routing;
    private Counter alpha;
    private Counter beta;

    @BeforeEach
    public void prepareRouting() {
        CollectorRegistry registry = new CollectorRegistry();
        // Routing
        this.routing = Routing.builder()
                .register(PrometheusSupport.create(registry))
                .build();
        // Metrics
        this.alpha = Counter.build()
                               .name("alpha")
                               .help("Alpha help with \\ and \n.")
                               .labelNames("method")
                               .register(registry);
        this.beta = Counter.build()
                               .name("beta")
                               .help("Beta help.")
                               .register(registry);
        for (int i = 0; i < 5; i++) {
            alpha.labels("\"foo\" \\ \n").inc();
        }
        for (int i = 0; i < 6; i++) {
            alpha.labels("bar").inc();
        }
        for (int i = 0; i < 3; i++) {
            beta.inc();
        }
    }

    private TestResponse doTestRequest(String nameQuery) throws Exception {
        TestRequest request = TestClient.create(routing)
                                        .path("/metrics");
        if (nameQuery != null && !nameQuery.isEmpty()) {
            request.queryParameter("name[]", nameQuery);
        }
        TestResponse response = request.get();
        assertEquals(200, response.status().code());
        return response;
    }

    @Test
    public void simpleCall() throws Exception {
        TestResponse response = doTestRequest(null);
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null),
                   StringStartsWith.startsWith("text/plain"));
        String body = response.asString().get(5, TimeUnit.SECONDS);
        assertThat(body, containsString("# HELP beta"));
        assertThat(body, containsString("# TYPE beta counter"));
        assertThat(body, containsString("beta 3.0"));
        assertThat(body, containsString("# TYPE alpha counter"));
        assertThat(body, containsString("# HELP alpha Alpha help with \\\\ and \\n."));
        assertThat(body, containsString("alpha{method=\"bar\",} 6.0"));
        assertThat(body, containsString("alpha{method=\"\\\"foo\\\" \\\\ \\n\",} 5.0"));
    }

    @Test
    public void doubleCall() throws Exception {
        TestResponse response = doTestRequest(null);
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null),
                   StringStartsWith.startsWith("text/plain"));
        String body = response.asString().get(5, TimeUnit.SECONDS);
        assertThat(body, containsString("alpha{method=\"bar\",} 6.0"));
        assertThat(body, not(containsString("alpha{method=\"baz\"")));
        alpha.labels("baz").inc();
        response = doTestRequest(null);
        body = response.asString().get(5, TimeUnit.SECONDS);
        assertThat(body, containsString("alpha{method=\"baz\",} 1.0"));
    }

    @Test
    public void filter() throws Exception {
        TestResponse response = doTestRequest("alpha");
        assertEquals(200, response.status().code());
        String body = response.asString().get(5, TimeUnit.SECONDS);
        assertThat(body, not(containsString("# TYPE beta")));
        assertThat(body, not(containsString("beta 3.0")));
        assertThat(body, containsString("# TYPE alpha counter"));
        assertThat(body, containsString("alpha{method=\"bar\",} 6.0"));
    }
}
