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

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.helidon.common.http.Http;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import org.openapitools.server.model.Message;

public class MessageServiceImpl implements MessageService {

    private final AtomicReference<Message> defaultMessage = new AtomicReference<>();

    private static final int HTTP_CODE_NOT_IMPLEMENTED = 501;
    private static final Logger LOGGER = Logger.getLogger(MessageService.class.getName());
    private static final ObjectMapper MAPPER = JsonProvider.objectMapper();

    public MessageServiceImpl() {
        Message message = new Message();
        message.setMessage("World");
        message.setGreeting("Hello");
        defaultMessage.set(message);
    }

    public void getDefaultMessage(ServerRequest request, ServerResponse response) {
        response.send(defaultMessage.get());
    }

    public void getMessage(ServerRequest request, ServerResponse response) {
        String name = request.path().param("name");
        Message result = new Message();
        result.setMessage(name);
        result.setGreeting(defaultMessage.get().getGreeting());
        response.send(result);
    }

    public void updateGreeting(ServerRequest request, ServerResponse response, Message message) {
        if (message.getGreeting() == null) {
            Message jsonError = new Message();
            jsonError.setMessage("No greeting provided");
            response.status(Http.Status.BAD_REQUEST_400)
                    .send(jsonError);
            return;
        }
        defaultMessage.set(message);
        response.status(Http.Status.NO_CONTENT_204).send();
    }

}
