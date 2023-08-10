/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.websocket;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Class MessageQueueResource.
 */
public class MessageQueueService implements HttpService {

    private final MessageQueue messageQueue = MessageQueue.instance();

    @Override
    public void routing(HttpRules routingRules) {
        routingRules.post("/board", this::handlePost);
    }

    private void handlePost(ServerRequest request, ServerResponse response) {
        messageQueue.push(request.content().as(String.class));
        response.status(204).send();
    }
}
