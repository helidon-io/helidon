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

@SuppressWarnings("ALL")
class Upgrade3xSnippets {
    /*
    void snippet_1() {
        // tag::snippet_1[]
        WebServer.builder(Routing.builder()
                                  .register("/rest", new SomeRestService()) // <1>
                                  .register("/websocket", TyrusSupport.builder() // <2>
                                          .register(ServerEndpointConfig.Builder
                                                            .create(MessageBoardEndpoint.class, "/")
                                                            .encoders(encoders)
                                                            .build())
                                          .build()
                                  ))
                .port(8080)
                .build();
        // end::snippet_1[]
    }
    */

    /*
    void snippet_2() {
        // tag::snippet_2[]
        WebServer.builder()
                .routing(r -> r
                        .register("/rest", new SomeRestService()) // <1>
                )
                .addRouting(WebSocketRouting.builder() // <2>
                                    .endpoint("/websocket", ServerEndpointConfig.Builder
                                            .create(MessageBoardEndpoint.class, "/board")
                                            .encoders(encoders)
                                            .build())
                                    .build())
                .port(8080)
        // end::snippet_2[]
    }
    */

}
