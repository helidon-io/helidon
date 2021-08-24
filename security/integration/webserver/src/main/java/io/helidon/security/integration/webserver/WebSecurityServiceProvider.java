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

package io.helidon.security.integration.webserver;

import java.util.List;

import javax.annotation.Priority;

import io.helidon.config.Config;
import io.helidon.webserver.Service;
import io.helidon.webserver.spi.WebServerServiceProvider;

/**
 * Provider for web server security.
 */
@Priority(100)
public class WebSecurityServiceProvider implements WebServerServiceProvider {

    @Override
    public String configKey() {
        return "security";
    }

    @Override
    public Iterable<Service> create(Config config) {
        return List.of(WebSecurity.create(config));
    }
}
