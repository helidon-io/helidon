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

package io.helidon.webserver.netty;

import java.util.Map;

import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.spi.WebServerFactory;

/**
 * Factory SPI implementation using Netty.io.
 */
public class Factory implements WebServerFactory {

    static {
        // By default, disable use of Unsafe
        if (!Boolean.valueOf(System.getProperty("io.helidon.webserver.yesUnsafe", "false"))) {
            System.setProperty("io.netty.noUnsafe", "true");
        }
    }

    @Override
    public WebServer newWebServer(ServerConfiguration configuration, Routing routing, Map<String, Routing> namedRoutings) {
        return new NettyWebServer(configuration, routing, namedRoutings);
    }
}
