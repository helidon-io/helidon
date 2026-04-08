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

package io.helidon.webserver.hsts;

import java.time.Duration;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class HstsRequestedUriTest {
    private final Http1Client client;

    HstsRequestedUriTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder builder) {
        Config config = Config.just(ConfigSources.classpath("requestedUriHsts.yaml")).get("server");
        builder.config(config)
                .featuresDiscoverServices(false)
                .clearFeatures()
                .addFeature(HstsFeature.create(it -> it.maxAge(Duration.ofMinutes(10))));
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/", (req, res) -> res.send("ok"));
    }

    @Test
    void forwardedHttpsAddsHstsOnPlainSocket() {
        try (Http1ClientResponse response = client.get("/")
                .header(HeaderNames.FORWARDED, "for=theClient;by=trustedProxy;host=example.com;proto=https")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().get(HeaderNames.STRICT_TRANSPORT_SECURITY).get(), is("max-age=600"));
        }
    }
}
