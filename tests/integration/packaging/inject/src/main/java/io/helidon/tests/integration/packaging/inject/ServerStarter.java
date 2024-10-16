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

package io.helidon.tests.integration.packaging.inject;

import java.util.List;
import java.util.function.Supplier;

import io.helidon.common.config.Config;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.Service;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpFeature;

/**
 * This will eventually be part of Helidon WebServer
 */
@Injection.Singleton
@Injection.RunLevel(Injection.RunLevel.SERVER)
class ServerStarter {
    private final Supplier<Config> config;
    private final Supplier<List<HttpFeature>> services;

    private volatile WebServer server;

    @Injection.Inject
    ServerStarter(Supplier<Config> config, Supplier<List<HttpFeature>> services) {
        this.config = config;
        this.services = services;
    }

    @Service.PostConstruct
    void start() {
        System.out.println(Thread.currentThread());
        server = WebServer.builder()
                .config(config.get().get("server"))
                .routing(it -> it.update(routing -> services.get().forEach(routing::addFeature)))
                .build()
                .start();
    }

    @Service.PreDestroy()
    void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
