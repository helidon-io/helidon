/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.micrometer;

import static org.hamcrest.Matchers.is;

import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webserver.WebServer;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class MicrometerEndpointTests {

    private static Config overallTestConfig = Config.create(ConfigSources.classpath("/micrometerTestData.json"));


    @Test
    @Disabled("406 Not Acceptable")
    public void testDefaultEndpoint() throws ExecutionException, InterruptedException {
        runTest(MicrometerFeature.DEFAULT_CONTEXT, MicrometerFeature::create);
    }

    @Test
    @Disabled("406 Not Acceptable")
    public void testExplicitEndpointWithDefaultBuiltInRegistryViaConfig() throws ExecutionException, InterruptedException {
        String context = "/aa";
        runTest(context, () -> MicrometerFeature.builder()
                .config(overallTestConfig.get("explicitContext").get("metrics.micrometer"))
                .build());
    }

    @Test
    public void testExplicitEndpointWithExplicitBuiltInRegistryViaBuilder() throws ExecutionException, InterruptedException {
        String context = "/bb";
        runTest(context, () -> MicrometerFeature.builder()
                .meterRegistryFactorySupplier(MeterRegistryFactory.builder()
                        .enrollBuiltInRegistry(MeterRegistryFactory.BuiltInRegistryType.PROMETHEUS)
                        .build())
                .webContext(context)
                .build());
    }

    @Test
    public void testExplicitEndpointWithExplicitBuiltInRegistryViaConfig() throws ExecutionException, InterruptedException {
        String context = "/cc";
        runTest(context, () -> MicrometerFeature.builder()
                .config(overallTestConfig.get("explicitContextWithExplicitBuiltIn").get("metrics.micrometer"))
                .build());
    }

    private static void runTest(String contextForRequest, Supplier<MicrometerFeature> micrometerFeatureSupplier)
            throws ExecutionException, InterruptedException {

        WebServer webServer = null;

        try {
            webServer = WebServer.builder()
                    .host("localhost")
                    .port(-1)
                    .routing(router -> router.addFeature(() -> micrometerFeatureSupplier.get()))
                    .build()
                    .start();
            Http.Status status = WebClient.builder()
                    .baseUri(String.format("http://localhost:%d%s", webServer.port(), contextForRequest))
                    .build()
                    .get()
//                    .header(Header.ACCEPT, MediaTypes.TEXT_PLAIN.toString())
                    .request().status();

            MatcherAssert.assertThat(status, is(Http.Status.OK_200));
        } finally {
            webServer.stop();
        }
    }

}
