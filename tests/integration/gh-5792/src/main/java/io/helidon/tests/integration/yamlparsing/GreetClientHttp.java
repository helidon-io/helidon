/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.yamlparsing;

import io.helidon.common.http.Http;
import io.helidon.nima.webclient.api.WebClient;

/**
 * Executable class that invokes HTTP/1 requests against the server.
 */
public class GreetClientHttp {
    private GreetClientHttp() {
    }

    /**
     * Main method.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        WebClient client = WebClient.builder()
                .baseUri("http://localhost:8080/greet")
                .build();

        String response = client.method(Http.Method.GET)
                .requestEntity(String.class);

        System.out.println(response);

        response = client.get("Nima")
                .requestEntity(String.class);

        System.out.println(response);
    }
}
