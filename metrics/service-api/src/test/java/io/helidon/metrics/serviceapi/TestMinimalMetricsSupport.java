/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.metrics.serviceapi;

import java.util.concurrent.ExecutionException;

import io.helidon.metrics.api.MetricsSettings;
import io.helidon.servicecommon.rest.RestServiceSettings;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class TestMinimalMetricsSupport {

    @Test
    void testEndpoint() throws ExecutionException, InterruptedException {
        MetricsSupport metricsSupport = MetricsSupport.
                create(MetricsSettings.create(),
                       RestServiceSettings.builder().webContext("/metrics").build());

        Routing routing = Routing.builder()
                .register(metricsSupport)
                .build();

        WebServer webServer = null;

        try {
            webServer = WebServer.builder()
                    .port(0)
                    .routing(routing)
                    .build()
                    .start()
                    .toCompletableFuture()
                    .get();


            WebClientResponse webClientResponse = WebClient.builder()
                    .baseUri("http://localhost:" + webServer.port())
                    .get()
                    .get()
                    .path("/metrics")
                    .request()
                    .get();
            assertThat("Response code from /metrics endpoint", webClientResponse.status().code(), is(404));
            assertThat("Response text from /metrics endpoint",
                       webClientResponse.content().as(String.class).get(),
                       is(equalTo(MinimalMetricsSupport.DISABLED_ENDPOINT_MESSAGE)));
        } finally {
            if (webServer != null) {
                webServer.shutdown()
                        .get();
            }
        }
    }
}
