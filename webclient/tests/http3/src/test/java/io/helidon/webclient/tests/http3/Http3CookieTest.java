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

package io.helidon.webclient.tests.http3;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictWebClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class Http3CookieTest {
    private TestEnvironment environment;

    @BeforeEach
    void beforeEach() throws Exception {
        environment = TestEnvironment.createSharedListener(rules -> rules
                .get("/cookie", Http3CookieTest::getHandler)
                .put("/cookie", Http3CookieTest::putHandler));
    }

    @AfterEach
    void afterEach() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void shouldHandleCookiesWithTypedHttp3Client() {
        Http3Client client = strictClientBuilder()
                .config(cookieConfig().get("client"))
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .build();

        try {
            try (Http3ClientResponse response = client.get("/cookie").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }

            try (Http3ClientResponse response = client.put("/cookie").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }

            try (Http3ClientResponse response = client.get("/cookie").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldHandleCookiesWithExplicitGenericHttp3Requests() {
        WebClient client = strictWebClientBuilder()
                .config(cookieConfig().get("client"))
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .build();

        try {
            try (HttpClientResponse response = client.get("/cookie")
                    .protocolId(Http3Client.PROTOCOL_ID)
                    .request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }

            try (HttpClientResponse response = client.put("/cookie")
                    .protocolId(Http3Client.PROTOCOL_ID)
                    .request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }

            try (HttpClientResponse response = client.get("/cookie")
                    .protocolId(Http3Client.PROTOCOL_ID)
                    .request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }
    }

    private static Config cookieConfig() {
        return Config.builder()
                .addSource(ConfigSources.classpath("cookies.yaml"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
    }

    private static void getHandler(ServerRequest req, ServerResponse res) {
        var cookies = req.headers().cookies();
        if (cookies.size() != 2) {
            res.status(Status.BAD_REQUEST_400).send();
            return;
        }

        if ("strawberry".equals(cookies.get("flavor3"))
                && "raspberry".equals(cookies.get("flavor4"))) {
            res.header(HeaderNames.SET_COOKIE, "flavor1=chocolate", "flavor2=vanilla");
            res.status(Status.OK_200).send();
        } else {
            res.status(Status.BAD_REQUEST_400).send();
        }
    }

    private static void putHandler(ServerRequest req, ServerResponse res) {
        var cookies = req.headers().cookies();
        if (cookies.size() != 4) {
            res.status(Status.BAD_REQUEST_400).send();
            return;
        }

        if ("chocolate".equals(cookies.get("flavor1"))
                && "vanilla".equals(cookies.get("flavor2"))
                && "strawberry".equals(cookies.get("flavor3"))
                && "raspberry".equals(cookies.get("flavor4"))) {
            res.header(HeaderNames.SET_COOKIE,
                       "flavor1=; Expires=Thu, 01-Jan-1970 00:00:10 GMT; Max-Age=0",
                       "flavor2=; Expires=Thu, 01-Jan-1970 00:00:10 GMT; Max-Age=0");
            res.status(Status.OK_200).send();
        } else {
            res.status(Status.BAD_REQUEST_400).send();
        }
    }

}
