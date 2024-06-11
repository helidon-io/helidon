/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.echo;

import io.helidon.http.Header;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;

/**
 * A client that invokes the echo server.
 */
public class EchoClient {
    private static final Header HEADER = HeaderValues.create("MY-HEADER", "header-value");
    private static final Header HEADERS = HeaderValues.create("MY-HEADERS", "ha", "hb", "hc");

    private EchoClient() {
    }

    /**
     * Main method.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        WebClient client = WebClient.create();

        HttpClientRequest request = client.get("http://localhost:8080/echo;param={param}/{name}");

        try (HttpClientResponse response = request.pathParam("name", "param-placeholder")
                .pathParam("param", "path-param-placeholder")
                .queryParam("query-param", "single_value")
                .queryParam("query-params", "a", "b", "c")
                .header(HEADER)
                .header(HEADERS)
                .request()) {

            Headers headers = response.headers();
            for (Header header : headers) {
                System.out.println("Header: " + header.name() + "=" + header.get());
            }
            System.out.println("Entity:");
            System.out.println(response.as(String.class));
        }
    }
}
