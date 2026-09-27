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

package io.helidon.webserver.tests.http2;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2Headers;
import io.helidon.security.Security;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.security.SecurityFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.http2.Http2TestClient;
import io.helidon.webserver.testing.junit5.http2.Http2TestConnection;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class SpecialRequestTargetTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        server.featuresDiscoverServices(false)
                .addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(Security.builder().build())
                                    .build())
                .addProtocol(Http2Config.builder().build())
                .routing(routing -> routing.any((req, res) -> {
                    var path = req.prologue().uriPath();
                    res.send(path.rawPath() + '|' + path.path() + '|' + path.absolute().path());
                }));
    }

    @Test
    void optionsAsteriskReachesRouting(Http2TestClient client) {
        try (Http2TestConnection connection = client.createConnection()) {
            Http2Headers headers = Http2Headers.create(WritableHeaders.create());
            headers.method(Method.OPTIONS);
            headers.path("*");
            headers.scheme(connection.clientUri().scheme());
            headers.authority(connection.clientUri().authority());
            connection.writer()
                    .writeHeaders(headers,
                                  1,
                                  Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                  FlowControl.Outbound.NOOP);

            connection.assertSettings(TIMEOUT);
            connection.assertWindowsUpdate(0, TIMEOUT);
            connection.assertSettings(TIMEOUT);

            assertThat(connection.assertHeaders(1, TIMEOUT).status(), is(Status.OK_200));
            byte[] responseBytes = connection.assertNextFrame(Http2FrameType.DATA, TIMEOUT).data().readBytes();
            assertThat(new String(responseBytes, StandardCharsets.UTF_8), is("*|*|/"));
        }
    }
}
