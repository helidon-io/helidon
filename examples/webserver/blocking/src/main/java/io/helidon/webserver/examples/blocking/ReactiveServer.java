/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.blocking;

import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

class ReactiveServer {
    private volatile WebServer webServer;

    // returns port
    int start(Config config, WebClient webClient) {
        webServer = WebServer.builder()
                .routing(Routing.builder()
                                 .register(new ReactiveService(webClient)))
                .config(config.get("servers.reactive"))
                .addMediaSupport(JsonpSupport.create())
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);

        return webServer.port();
    }

    void stop() {
        webServer.shutdown()
                .await(10, TimeUnit.SECONDS);
    }
}
