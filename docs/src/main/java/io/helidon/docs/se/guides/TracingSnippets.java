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
package io.helidon.docs.se.guides;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.tracing.WebClientTracing;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.JsonObject;

@SuppressWarnings("ALL")
class TracingSnippets {

    // stub
    static void sendResponse(ServerResponse response, String str) {
    }

    class Snippet1 {

        // tag::snippet_1[]
        private void getDefaultMessageHandler(ServerRequest request,
                                              ServerResponse response) {
            var spanBuilder = Tracer.global().spanBuilder("secondchildSpan"); // <1>
            request.context().get(SpanContext.class).ifPresent(sc -> sc.asParent(spanBuilder)); // <2>
            var span = spanBuilder.start(); // <3>

            try (Scope scope = span.activate()) { // <4>
                sendResponse(response, "World");
                span.end(); // <5>
            } catch (Throwable t) {
                span.end(t);    // <6>
            }
        }
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        private void getDefaultMessageHandler(ServerRequest request,
                                              ServerResponse response) {

            var spanBuilder = Tracer.global().spanBuilder("getDefaultMessageHandler");
            request.context().get(SpanContext.class).ifPresent(spanBuilder::parent);
            Span span = spanBuilder.start();

            try (Scope scope = span.activate()) {
                sendResponse(response, "World");
                span.end();
            } catch (Throwable t) {
                span.end(t);
            }
        }
        // end::snippet_2[]
    }

    // tag::snippet_3[]
    private WebClient webClient;
    // end::snippet_3[]

    void snippet_4(WebClient webClient) {
        // tag::snippet_4[]
        webClient = WebClient.builder()
                .baseUri("http://localhost:8081")
                .addService(WebClientTracing.create())
                .build();
        // end::snippet_4[]
    }

    class Snippet5_6 {

        WebClient webClient;

        void snippet_5(HttpRules rules) {
            rules
            // tag::snippet_5[]
                    .get("/outbound", this::outboundMessageHandler);
            // end::snippet_5[]
        }

        // tag::snippet_6[]
        private void outboundMessageHandler(ServerRequest request,
                                            ServerResponse response) {
            var spanBuilder = Tracer.global().spanBuilder("outboundMessageHandler");
            request.context().get(SpanContext.class).ifPresent(spanBuilder::parent);
            var span = spanBuilder.start();

            try (Scope scope = span.activate()) {
                ClientResponseTyped<JsonObject> remoteResult = webClient.get()
                        .path("/greet")
                        .accept(MediaTypes.APPLICATION_JSON)
                        .request(JsonObject.class);

                response.status(remoteResult.status()).send(remoteResult.entity());
                span.end();
            } catch (Exception e) {
                response.status(Status.INTERNAL_SERVER_ERROR_500).send();
                span.end(e);
            }
        }
        // end::snippet_6[]
    }

}
