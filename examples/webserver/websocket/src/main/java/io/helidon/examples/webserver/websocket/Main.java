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

import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.staticcontent.StaticContentService;
import io.helidon.webserver.websocket.WsRouting;

/**
 * Application demonstrates combination of websocket and REST.
 */
public class Main {

    private Main() {
    }

    static void setup(WebServerConfig.Builder server) {
        StaticContentService staticContent = StaticContentService.builder("/WEB")
                                                         .welcomeFileName("index.html")
                                                         .build();
        MessageQueueService messageQueueService = new MessageQueueService();
        server.routing(routing -> routing
                       .register("/web", staticContent)
                       .register("/rest", messageQueueService))
               .addRouting(WsRouting.builder()
                                    .endpoint("/websocket/board", new MessageBoardEndpoint()));
    }

    /**
     * A java main class.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        WebServerConfig.Builder builder = WebServer.builder().port(8080);
        setup(builder);
        WebServer server = builder.build().start();
        System.out.println("WEB server is up! http://localhost:" + server.port() + "/web");
    }
}
