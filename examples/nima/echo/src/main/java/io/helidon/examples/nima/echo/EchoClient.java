/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.nima.echo;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;

/**
 * A client that invokes the echo server.
 */
public class EchoClient {
    private static final HeaderValue HEADER = Header.create(Header.create("MY-HEADER"), "header-value");
    private static final HeaderValue HEADERS = Header.create(Header.create("MY-HEADERS"), "ha", "hb", "hc");

    private EchoClient() {
    }

    /**
     * Main method.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Http1Client client = WebClient.builder()
                .build();

        Http1ClientRequest request = client.get("http://localhost:8080/echo;param={param}/{name}");


        Http1ClientResponse response = request.pathParam("name", "param-placeholder")
                .pathParam("param", "path-param-placeholder")
                .queryParam("query-param", "single_value")
                .queryParam("query-params", "a", "b", "c")
                .header(HEADER)
                .header(HEADERS)
                .request();

        Headers headers = response.headers();
        for (HeaderValue header : headers) {
            System.out.println("Header: " + header.name() + "=" + header.value());
        }
        String entity = response.as(String.class);
    }
}
