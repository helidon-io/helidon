/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.security;

import io.helidon.config.Config;
import io.helidon.microprofile.server.spi.MpService;
import io.helidon.microprofile.server.spi.MpServiceContext;
import io.helidon.security.Security;
import io.helidon.security.jersey.SecurityFeature;
import io.helidon.security.webserver.WebSecurity;

/**
 * Security extension.
 * Simply put this module into your dependencies and it will get picked-up
 * by microprofile server.
 * You can configure security using application.yaml (or any other default
 * configuration for helidon config).
 */
public class SecurityMpService implements MpService {
    @Override
    public void configure(MpServiceContext context) {
        //this uses helidon config heavily
        Config config = context.getHelidonConfig();

        Security security = Security.fromConfig(config);

        Config jerseyConfig = config.get("security.jersey");
        if (jerseyConfig.get("enabled").asBoolean(true)) {
            SecurityFeature feature = SecurityFeature.builder(security)
                    .fromConfig(config)
                    .build();

            context.getApplications().forEach(app -> app.register(feature));
        }

        Config webServerConfig = config.get("security.web-server");
        if (webServerConfig.exists() && webServerConfig.get("enabled").asBoolean(true)) {
            context.getServerRoutingBuilder()
                    .register(WebSecurity.from(security, config));
        }

        // for later use, e.g. for outbound security
        context.register(Security.class, security);
    }
}
