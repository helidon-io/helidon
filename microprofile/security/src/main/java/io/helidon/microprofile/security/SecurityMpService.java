/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.microprofile.server.spi.MpService;
import io.helidon.microprofile.server.spi.MpServiceContext;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Security;
import io.helidon.security.integration.jersey.SecurityFeature;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.providers.abac.AbacProvider;

/**
 * Security extension.
 * Simply put this module into your dependencies and it will get picked-up
 * by microprofile server.
 * You can configure security using application.yaml (or any other default
 * configuration for helidon config).
 */
public class SecurityMpService implements MpService {
    private static final Logger LOGGER = Logger.getLogger(SecurityMpService.class.getName());

    @Override
    public void configure(MpServiceContext context) {
        //this uses helidon config heavily
        Config config = context.helidonConfig();

        Security security;
        if (config.get("security.enabled").asBoolean().orElse(true)) {
            if (config.get("security.providers").exists()) {
                security = Security.create(config.get("security"));
            } else {
                LOGGER.info(
                        "Security extension for microprofile is enabled, yet security configuration is missing from config "
                                + "(requires providers configuration at key security.providers). Security will not have any "
                                + "valid "
                                + "provider.");
                security = Security.builder()
                        .addProvider(AbacProvider.create())
                        .addAuthenticationProvider(providerRequest -> CompletableFuture
                                .completedFuture(AuthenticationResponse.failed("No provider configured")))
                        .build();
            }
        } else {
            // security is disabled, we need to set up some basic stuff - injection, security context etc.
            LOGGER.info("Security is disabled.");
            security = Security.builder()
                    .enabled(false)
                    .build();
        }

        Contexts.context().ifPresent(ctx -> ctx.register(security));

        Config jerseyConfig = config.get("security.jersey");
        if (jerseyConfig.get("enabled").asBoolean().orElse(true)) {
            SecurityFeature feature = SecurityFeature.builder(security)
                    .config(jerseyConfig)
                    .build();

            context.applications().forEach(app -> {
                app.register(feature);
            });
        }

        Config webServerConfig = config.get("security.web-server");
        if (webServerConfig.exists() && webServerConfig.get("enabled").asBoolean().orElse(true)) {
            context.serverRoutingBuilder()
                    .register(WebSecurity.create(security, config.get("security")));
        }

        // for later use, e.g. for outbound security
        context.register(Security.class, security);
    }
}
