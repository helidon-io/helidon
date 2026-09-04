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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictWebClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class Http3ExpectContinueTest {
    private static final String PAYLOAD = "payload";

    @Test
    void shouldSupportExpectContinueOnTypedHttp3Client() throws Exception {
        AtomicBoolean sawExpect = new AtomicBoolean();

        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing.post("/expect-continue", (req, res) -> {
                 sawExpect.set(req.headers().contains(HeaderValues.EXPECT_100));
                 res.send(req.content().as(String.class));
             }))) {
            Http3Client client = strictClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTlsHttp3())
                    .build();

            try {
                try (Http3ClientResponse response = client.post("/expect-continue")
                        .sendExpectContinue(true)
                        .readContinueTimeout(Duration.ofSeconds(1))
                        .submit(PAYLOAD)) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.entity().as(String.class), is(PAYLOAD));
                }
            } finally {
                client.closeResource();
            }
        }

        assertThat(sawExpect.get(), is(true));
    }

    @Test
    void shouldSupportExpectContinueOnGenericClientWhenHttp3IsExplicitlySelected() throws Exception {
        AtomicBoolean sawExpect = new AtomicBoolean();

        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing.post("/expect-continue", (req, res) -> {
                 sawExpect.set(req.headers().contains(HeaderValues.EXPECT_100));
                 res.send(req.content().as(String.class));
             }))) {
            WebClient client = strictWebClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTls())
                    .build();

            try {
                try (HttpClientResponse response = client.post("/expect-continue")
                        .protocolId(Http3Client.PROTOCOL_ID)
                        .sendExpectContinue(true)
                        .readContinueTimeout(Duration.ofSeconds(1))
                        .submit(PAYLOAD)) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.entity().as(String.class), is(PAYLOAD));
                }
            } finally {
                client.closeResource();
            }
        }

        assertThat(sawExpect.get(), is(true));
    }

    @Test
    void shouldExposeFinalResponseWhenRouteFailsBeforeReadingEntity() throws Exception {
        AtomicBoolean sawExpect = new AtomicBoolean();

        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing.post("/expect-continue-fail", (req, res) -> {
                 sawExpect.set(req.headers().contains(HeaderValues.EXPECT_100));
                 throw new IllegalStateException("boom");
             }))) {
            Http3Client client = strictClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTlsHttp3())
                    .build();

            try {
                try (Http3ClientResponse response = client.post("/expect-continue-fail")
                        .sendExpectContinue(true)
                        .readContinueTimeout(Duration.ofSeconds(1))
                        .submit(PAYLOAD)) {
                    assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.entity().as(String.class), is("Internal Server Error"));
                }
            } finally {
                client.closeResource();
            }
        }

        assertThat(sawExpect.get(), is(true));
    }
}
