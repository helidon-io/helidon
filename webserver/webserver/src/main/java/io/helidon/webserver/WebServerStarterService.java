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

import io.helidon.common.config.Configuration;
import io.helidon.service.registry.Service;

@SuppressWarnings("deprecation")
@Service.Singleton
@Service.RunLevel(Service.RunLevel.SERVER)
class WebServerStarterService {
    private static final System.Logger LOGGER = System.getLogger(WebServerStarterService.class.getName());

    private final LoomServer server;
    private final boolean ignoreIncubating;

    WebServerStarterService(LoomServer server, @Configuration.Value("declarative.ignore-incubating") boolean ignoreIncubating) {
        this.server = server;
        this.ignoreIncubating = ignoreIncubating;
    }

    @Service.PostConstruct
    void postConstruct() {
        server.start();
        if (!ignoreIncubating) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "Helidon WebServer is starting through Helidon Declarative. This is currently an incubating feature (and"
                               + " as such it may be changed including backward incompatible changes). This warning can be "
                               + "disabled by setting configuration option 'declarative.ignore-incubating' to true");
        }
    }

    @Service.PreDestroy
    void preDestroy() {
        if (server != null) {
            server.stop();
        }
    }
}
