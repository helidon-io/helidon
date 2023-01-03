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

package org.openapitools.server.api;

import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import org.openapitools.server.model.Message;

public interface MessageService extends Service {

    /**
     * A service registers itself by updating the routing rules.
     *
     * @param rules the routing rules.
     */
    @Override
    default void update(Routing.Rules rules) {
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
    void getDefaultMessage(ServerRequest request, ServerResponse response);

    /**
     * GET /greet/{name} : Return a greeting message using the name that was provided..
     *
     * @param request  the server request
     * @param response the server response
     */
    void getMessage(ServerRequest request, ServerResponse response);

    /**
     * PUT /greet/greeting : Set the greeting to use in future messages..
     *
     * @param request  the server request
     * @param response the server response
     * @param message  Message for the user
     */
    void updateGreeting(ServerRequest request, ServerResponse response, Message message);

}
