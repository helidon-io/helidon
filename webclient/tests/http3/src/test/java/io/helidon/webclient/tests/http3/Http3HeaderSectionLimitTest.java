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

import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3ProtocolException;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webserver.http3.Http3RawTestServer;
import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3HeaderSectionLimitTest {
    @Test
    void shouldRejectResponseHeadersThatExceedConfiguredMaxHeadersSize() throws Exception {
        WritableHeaders<?> responseHeaders = WritableHeaders.create()
                .add(HeaderValues.create("x-big", "x".repeat(200)));

        try (Http3RawTestServer server = Http3RawTestServer.create((_, _, _, _) ->
                Http3RawTestServer.response(Status.OK_200.code(), responseHeaders, new byte[0]))) {
            Http3ClientProtocolConfig.Builder protocolConfig = Http3ClientProtocolConfig.builder()
                    .maxHeadersSize(128);
            Http3Client client = strictClientBuilder(protocolConfig)
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();
            try {
                RuntimeException exception = assertThrows(RuntimeException.class,
                                                         () -> client.get("/hello").request());
                Http3ProtocolException protocolException = Http3ProtocolException.find(exception).orElseThrow();

                assertThat(protocolException.errorCode(), is(Http3ErrorCode.MESSAGE_ERROR));
                assertThat(protocolException.scope(), is(Http3ProtocolException.Scope.STREAM));
            } finally {
                client.closeResource();
            }
        }
    }
}
