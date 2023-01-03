/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package org.openapitools.client.api;

import io.helidon.common.http.Http;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import org.openapitools.client.ApiClient;
import org.openapitools.client.model.Message;

public class MessageService implements Service {

    private final MessageApi api;

    public MessageService() {
        ApiClient apiClient = ApiClient.builder().build();
        api = MessageApiImpl.create(apiClient);
    }

    /**
     * A service registers itself by updating the routing rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/greet", this::getDefaultMessage);
        rules.get("/greet/{name}", this::getMessage);
        rules.put("/greet/greeting", Handler.create(Message.class, this::updateGreeting));
    }

    /**
     * GET /greet : Return a worldly greeting message..
     *
     * @param request  the server request
     * @param response the server response
     */
    public void getDefaultMessage(ServerRequest request, ServerResponse response) {
        api.getDefaultMessage()
           .webClientResponse()
           .flatMapSingle(serverResponse -> serverResponse.content().as(Message.class))
           .thenAccept(response::send);
    }

    /**
     * GET /greet/{name} : Return a greeting message using the name that was provided..
     *
     * @param request  the server request
     * @param response the server response
     */
    public void getMessage(ServerRequest request, ServerResponse response) {
        String name = request.path().param("name");
        api.getMessage(name)
           .webClientResponse()
           .flatMapSingle(serverResponse -> serverResponse.content().as(Message.class))
           .thenAccept(response::send);
    }

    /**
     * PUT /greet/greeting : Set the greeting to use in future messages..
     *
     * @param request  the server request
     * @param response the server response
     * @param message  Message for the user
     */
    public void updateGreeting(ServerRequest request, ServerResponse response, Message message) {
        api.updateGreeting(message)
           .webClientResponse()
           .thenAccept(content -> response.status(Http.Status.NO_CONTENT_204).send());
    }
}
