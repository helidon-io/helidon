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

package io.helidon.pico.config.testsubjects;

import java.util.Optional;

import io.helidon.pico.config.api.ConfiguredBy;

import jakarta.inject.Inject;

@ConfiguredBy(value = ClientAndServerConfig.class, overrideBean = true, defaultConfigBeanUsingDefaults = true)
public class ClientAndServer {

    private final Optional<Client> client;
    private final Optional<Server> server;

    @Inject
    public ClientAndServer(Optional<Client> client, Optional<Server> server) {
        this.client = client;
        this.server = server;
    }

    String serverName() {
        return server.map(Server::name).orElse(null);
    }

    String clientName() {
        return client.map(Client::name).orElse(null);
    }

    @Override
    public String toString() {
        return clientName() + ":" + serverName();
    }

}
