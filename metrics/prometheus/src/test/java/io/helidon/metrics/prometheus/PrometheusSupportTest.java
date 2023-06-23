/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.metrics.prometheus;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import org.hamcrest.core.StringStartsWith;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

@ServerTest
public class PrometheusSupportTest {

    private Counter alpha;
    private Counter beta;
    private static CollectorRegistry registry = new CollectorRegistry();
    private final Http1Client client;

    PrometheusSupportTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder){
        builder.addFeature(PrometheusSupport.create(registry)).build();
    }

    @BeforeEach
    public void prepareRegistry() throws InterruptedException {
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

    @AfterEach
    public void clearRegistry() {
        registry.clear();
    }

    @Test
    public void simpleCall() {
        try (Http1ClientResponse response = client.get("/metrics").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null),
                    StringStartsWith.startsWith("text/plain"));
            String body = response.as(String.class);
            assertThat(body, containsString("# HELP beta"));
            assertThat(body, containsString("# TYPE beta counter"));
            assertThat(body, containsString("beta 3.0"));
            assertThat(body, containsString("# TYPE alpha counter"));
            assertThat(body, containsString("# HELP alpha Alpha help with \\\\ and \\n."));
            assertThat(body, containsString("alpha{method=\"bar\",} 6.0"));
            assertThat(body, containsString("alpha{method=\"\\\"foo\\\" \\\\ \\n\",} 5.0"));
        }
    }

    @Test
    public void doubleCall() {
        try (Http1ClientResponse response = client.get("/metrics").request()) {
            assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null),
                    StringStartsWith.startsWith("text/plain"));
            String body = response.as(String.class);
            assertThat(body, containsString("alpha{method=\"bar\",} 6.0"));
            assertThat(body, not(containsString("alpha{method=\"baz\"")));
            alpha.labels("baz").inc();
        }
        try (Http1ClientResponse response = client.get("/metrics").request()) {
            String body = response.as(String.class);
            assertThat(body, containsString("alpha{method=\"baz\",} 1.0"));
        }
    }

    @Test
    public void filter() {
        try (Http1ClientResponse response = client.get("/metrics").queryParam("name[]", "alpha").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            String body = response.as(String.class);
            assertThat(body, not(containsString("# TYPE beta")));
            assertThat(body, not(containsString("beta 3.0")));
            assertThat(body, containsString("# TYPE alpha counter"));
            assertThat(body, containsString("alpha{method=\"bar\",} 6.0"));
        }
    }
}
