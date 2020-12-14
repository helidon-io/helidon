/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.metrics.micrometer;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.media.common.MediaSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EndpointTests {

    private static Config overallTestConfig = Config.create(ConfigSources.classpath("/testData.json"));


    @Test
    public void testDefaultEndpoint() throws ExecutionException, InterruptedException {
        runTest(MicrometerSupport.DEFAULT_CONTEXT, () -> MicrometerSupport.create());
    }

    @Test
    public void testExplicitEndpointWithExplicitBuiltInRegistryViaBuilder() throws ExecutionException, InterruptedException {
        String context = "/mm";
        runTest(context, () -> MicrometerSupport.builder()
                    .webContext(context)
                    .enrollBuiltInRegistry(MicrometerSupport.BuiltInRegistryType.PROMETHEUS)
                    .build());
    }

    @Test
    private void testExplicitEndpointWithDefaultBuiltInRegistryViaConfig() throws ExecutionException, InterruptedException {
        String context = "/cc";
        runTest(context, () -> MicrometerSupport.builder()
                    .config(overallTestConfig.get("explicitContext"))
                    .build());
    }

    @Test
    private void testExplicitEndpointWithExplicitBuiltInRegistryViaConfig() throws ExecutionException, InterruptedException {
        String context = "/dd";
        runTest(context, () -> MicrometerSupport.builder()
                .config(overallTestConfig.get("explicitContext"))
                .build());
    }

    private static void runTest(String contextForRequest, Supplier<MicrometerSupport> micrometerSupportSupplier)
            throws ExecutionException, InterruptedException {

        WebServer webServer = null;

        try {
            webServer = WebServer.builder()
                    .port(-1)
                    .routing(prepareRouting(micrometerSupportSupplier))
                    .build()
                    .start()
                    .await();

            WebClientResponse webClientResponse = WebClient.builder()
                    .baseUri(String.format("http://localhost:%d%s", webServer.port(), contextForRequest))
                    .build()
                    .get()
                    .accept(MediaType.TEXT_PLAIN)
                    .request()
                    .get();

            assertThat(webClientResponse.status(), is(Http.Status.OK_200));
        } finally {
            if (webServer != null) {
                webServer.shutdown()
                        .await();
            }
        }
    }

    private static Routing.Builder prepareRouting(Supplier<MicrometerSupport> micrometerSupportSupplier) {
        return Routing.builder()
                .register(micrometerSupportSupplier.get());
    }

}
