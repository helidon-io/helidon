/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.spi;

import java.util.Map;

import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

/**
 * SPI for {@link WebServer} factory implementations.
 *
 * @see WebServer
 */
public interface WebServerFactory {

    /**
     * Creates new {@link WebServer} instance form provided configuration and routing.
     *
     * @param configuration a server configuration instance
     * @param routing       a default routing instance
     * @param namedRoutings the named routings of the configured additional server sockets. If there is no
     *                      named routing for a given named additional server socket configuration, a default
     *                      routing is used.
     * @return a new web server instance.
     */
    WebServer newWebServer(ServerConfiguration configuration, Routing routing, Map<String, Routing> namedRoutings);
}
