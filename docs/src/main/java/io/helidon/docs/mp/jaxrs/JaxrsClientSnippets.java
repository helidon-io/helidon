/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.mp.jaxrs;

import java.io.IOException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

@SuppressWarnings("ALL")
class JaxrsClientSnippets {

    // stub
    class GreetFilter implements ClientRequestFilter {

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
        }
    }

    // stub
    class GreetExceptionMapper implements ExceptionMapper<Exception> {

        @Override
        public Response toResponse(Exception exception) {
            return null;
        }
    }

    void snippet_1() {
        // tag::snippet_1[]
        Client client = ClientBuilder.newClient();
        Response res = client
                .target("http://localhost:8080/greet")
                .request("text/plain")
                .get();
        // end::snippet_1[]
    }

    void snippet_2() {
        // tag::snippet_2[]
        Client client = ClientBuilder.newClient();
        String res = client
                .target("http://localhost:8080/greet")
                .request("text/plain")
                .get(String.class);
        // end::snippet_2[]
    }

    void snippet_3() {
        // tag::snippet_3[]
        Client client = ClientBuilder.newClient();
        client.register(GreetFilter.class);
        String res = client
                .target("http://localhost:8080/greet")
                .register(GreetExceptionMapper.class)
                .request("text/plain")
                .get(String.class);
        // end::snippet_3[]
    }

    void snippet_4() {
        // tag::snippet_4[]
        Client client = ClientBuilder.newClient();
        Future<String> res = client
                .target("http://localhost:8080/greet")
                .request("text/plain")
                .async()        // now asynchronous
                .get(String.class);
        // end::snippet_4[]
    }

    void snippet_5() {
        // tag::snippet_5[]
        Client client = ClientBuilder.newClient();
        CompletionStage<String> res = client
                .target("http://localhost:8080/greet")
                .request("text/plain")
                .rx()           // now reactive
                .get(String.class);
        // end::snippet_5[]
    }

}
