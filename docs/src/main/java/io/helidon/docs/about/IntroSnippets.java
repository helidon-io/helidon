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
package io.helidon.docs.about;

import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.JsonObject;

@SuppressWarnings("ALL")
class IntroSnippets {

    // stub
    void doSomething(JsonObject jsonObject, ServerResponse response) {
    }

    /*
    void snippet_1(ServerRequest request, ServerResponse response) {
        // tag::snippet_1[]
        request.content().as(JsonObject.class)
                .thenAccept(jo -> doSomething(jo, response));
        // end::snippet_1[]
    }
    */

    void snippet_2(ServerRequest request, ServerResponse response) {
        // tag::snippet_2[]
        doSomething(request.content().as(JsonObject.class), response);
        // end::snippet_2[]
    }
}
