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

package io.helidon.webserver.tests.http3;

import java.nio.charset.StandardCharsets;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

class Http3DynamicQpackTest {
    private static final HeaderName QPACK_USER = HeaderNames.create("x-qpack-user");
    private static final HeaderName QPACK_ENV = HeaderNames.create("x-qpack-env");
    private static final HeaderName QPACK_CLUSTER = HeaderNames.create("x-qpack-cluster");
    private static final String USER_VALUE = "alpha-user-1234567890";
    private static final String ENV_VALUE = "dev-eu-central-1";
    private static final String CLUSTER_VALUE = "shared-http3-connection";

    @Test
    void shouldReuseDynamicQpackAcrossRepeatedResponsesOnSameConnection() throws Exception {
        try (Http3TestSupport.TestEnvironment environment = Http3TestSupport.sharedListener(Http3DynamicQpackTest::routing);
             Http3TestSupport.LowLevelHttp3Client client = environment.lowLevelHttp3Client()) {
            Http3TestSupport.DecodedResponse warmupResponse = client.get(environment.uri("/hello"));
            Http3TestSupport.DecodedResponse firstResponse = client.get(environment.uri("/dynamic-qpack"));
            Http3TestSupport.DecodedResponse secondResponse = client.get(environment.uri("/dynamic-qpack"));
            Http3TestSupport.DecodedResponse thirdResponse = client.get(environment.uri("/dynamic-qpack"));

            assertThat(warmupResponse.status(), equalTo(200));
            assertThat(new String(warmupResponse.body(), StandardCharsets.UTF_8), equalTo("hello"));

            assertThat(firstResponse.status(), equalTo(200));
            assertThat(firstResponse.headers().first(QPACK_USER).orElseThrow(), equalTo(USER_VALUE));
            assertThat(firstResponse.headers().first(QPACK_ENV).orElseThrow(), equalTo(ENV_VALUE));
            assertThat(firstResponse.headers().first(QPACK_CLUSTER).orElseThrow(), equalTo(CLUSTER_VALUE));
            assertThat(new String(firstResponse.body(), StandardCharsets.UTF_8), equalTo("dynamic"));

            assertThat(secondResponse.status(), equalTo(200));
            assertThat(secondResponse.headers().first(QPACK_USER).orElseThrow(), equalTo(USER_VALUE));
            assertThat(new String(secondResponse.body(), StandardCharsets.UTF_8), equalTo("dynamic"));

            assertThat(thirdResponse.status(), equalTo(200));
            assertThat(thirdResponse.headers().first(QPACK_CLUSTER).orElseThrow(), equalTo(CLUSTER_VALUE));
            assertThat(new String(thirdResponse.body(), StandardCharsets.UTF_8), equalTo("dynamic"));

            assertThat(Math.min(secondResponse.headersPayloadLength(), thirdResponse.headersPayloadLength()),
                       lessThan(firstResponse.headersPayloadLength()));
        }
    }

    private static void routing(HttpRouting.Builder router) {
        router.get("/hello", (req, res) -> res.send("hello"))
                .get("/dynamic-qpack", (req, res) -> {
                    res.header(QPACK_USER, USER_VALUE);
                    res.header(QPACK_ENV, ENV_VALUE);
                    res.header(QPACK_CLUSTER, CLUSTER_VALUE);
                    res.send("dynamic");
                });
    }
}
