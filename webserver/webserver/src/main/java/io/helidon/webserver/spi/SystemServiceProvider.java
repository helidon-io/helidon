/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.config.Config;
import io.helidon.webserver.SystemService;

/**
 * A Java service loader provider that exposes a {@link SystemService} to WebServer.
 */
public interface SystemServiceProvider {
    /**
     * Create a system service from configuration.
     *
     * @param config configuration located on the configuration node as defined by {@link #name()}
     * @return a new system service instance
     */
    SystemService create(Config config);

    /**
     * Name of this system service, also used to locate configuration node in server configuration.
     * @return name of this service to be used as a key in configuration
     */
    String name();

}
