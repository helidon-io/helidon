/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.telemetry;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(BaggageCheckingResource.class)
class TestMultipleBaggageHeaders {

    @Inject
    private WebTarget webTarget;

    @Test
    void testMultipleBaggageHeaders() throws IOException, InterruptedException, URISyntaxException {
        List<String> baggageSettings = List.of("k1=val1", "k2=val2");
        try (HttpClient client = HttpClient.newHttpClient()) {
            URI uri = new URI(webTarget.getUri().getScheme(),
                              webTarget.getUri().getUserInfo(),
                              webTarget.getUri().getHost(),
                              webTarget.getUri().getPort(),
                              BaggageCheckingResource.PATH,
                              null,
                              null);
            var requestBuilder = HttpRequest.newBuilder(uri)
                    .GET();
            // Set the Baggage header multiple times to trigger the error condition.
            baggageSettings.forEach(setting -> requestBuilder.header("Baggage", setting));
            var request = requestBuilder.build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat("Baggage-checking endpoint status", response.statusCode(), is(200));
            assertThat("Baggage-checking endpoint response", response.body(), allOf(containsString("k1=val1"),
                                                                                    containsString("k2=val2")));
        }
        //        String requestBody = new StringJoiner(System.lineSeparator())
        //                .add("GET " + webTarget.getUri() + " HTTP/1.1")
        //                .add("Host: localhost")
        //                .add("Connection: close")
        //                .add("Content-Type: application/json")
        //                .add("Accept: text/plain")
        //                .add("Accept-Language: en-US")
        //                .add("Accept-Charset: utf-8")
        //                .add("Baggage: k1=val1")
        //                .add("Baggage: k2=val2")
        //                .toString();
        //         result = URI.create(webTarget.getUri().toString() + "/baggage").toURL().getContent();

        //        assertThat("Response status from /baggage", response.statusCode(), is(equalTo(200)));
        //        assertThat("Returned baggage settings",
        //                   response.body(),
        //                   allOf(containsString("k1=val1"),
        //                         containsString("k2=val2")));
    }

}
