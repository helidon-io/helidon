/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import io.helidon.service.registry.Service;

@Service.Singleton
@Service.RunLevel(Service.RunLevel.SERVER)
class WebServerStarterService {
    private final LoomServer server;

    WebServerStarterService(LoomServer server) {
        this.server = server;
    }

    @Service.PostConstruct
    void postConstruct() {
        if (server != null) {
            server.start();
        }
    }

    @Service.PreDestroy
    void preDestroy() {
        if (server != null) {
            server.stop();
        }
    }
}
