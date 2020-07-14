/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.integration.jersey.SecurityFeature;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.providers.abac.AbacProvider;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.AuthorizationProvider;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * Extension to register bean {@link SecurityProducer}.
 */
public class SecurityCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(SecurityCdiExtension.class.getName());

    private final AtomicReference<Security> security = new AtomicReference<>();

    private Security.Builder securityBuilder = Security.builder();
    private Config config;

    private void registerBean(@Observes BeforeBeanDiscovery abd) {
        abd.addAnnotatedType(SecurityProducer.class, "helidon-security-producer")
                .add(ApplicationScoped.Literal.INSTANCE);
    }

    // priority is high, so we update builder from config as soon as possible
    // all additions by other extension will override configuration options
    private void configure(@Observes @RuntimeStart @Priority(PLATFORM_BEFORE) Config config) {
        this.config = config;
        securityBuilder.config(config.get("security"));
    }

    private void registerSecurity(@Observes @Initialized(ApplicationScoped.class) @Priority(PLATFORM_AFTER) Object adv,
                                  BeanManager bm) {

        if (securityBuilder.noProvider(AuthenticationProvider.class)) {
            LOGGER.info(
                    "Authentication provider is missing from security configuration, but security extension for microprofile "
                            + "is enabled (requires providers configuration at key security.providers). "
                            + "Security will not have any valid authentication provider");

            securityBuilder.addAuthenticationProvider(this::failingAtnProvider);
        }

        if (securityBuilder.noProvider(AuthorizationProvider.class)) {
            LOGGER.info(
                    "Authorization provider is missing from security configuration, but security extension for microprofile "
                            + "is enabled (requires providers configuration at key security.providers). "
                            + "ABAC provider is configured for authorization.");
            securityBuilder.addAuthorizationProvider(AbacProvider.create());
        }

        Security tmpSecurity = securityBuilder.build();
        // free it and make sure we fail if somebody wants to update security afterwards
        securityBuilder = null;

        if (!tmpSecurity.enabled()) {
            // security is disabled, we need to set up some basic stuff - injection, security context etc.
            LOGGER.info("Security is disabled.");
            tmpSecurity = Security.builder()
                    .enabled(false)
                    .build();
        }

        // we need an effectively final instance to use in lambda
        Security security = tmpSecurity;

        JaxRsCdiExtension jaxrs = bm.getExtension(JaxRsCdiExtension.class);
        ServerCdiExtension server = bm.getExtension(ServerCdiExtension.class);

        Contexts.context().ifPresent(ctx -> ctx.register(security));

        Config jerseyConfig = config.get("security.jersey");
        if (jerseyConfig.get("enabled").asBoolean().orElse(true)) {
            SecurityFeature feature = SecurityFeature.builder(security)
                    .config(jerseyConfig)
                    .build();

            jaxrs.applicationsToRun()
                    .forEach(app -> app.resourceConfig().register(feature));
        }

        Config webServerConfig = config.get("security.web-server");
        if (webServerConfig.exists() && webServerConfig.get("enabled").asBoolean().orElse(true)) {
            server.serverRoutingBuilder()
                    .register(WebSecurity.create(security, config.get("security")));
        }
        this.security.set(security);
    }

    private CompletionStage<AuthenticationResponse> failingAtnProvider(ProviderRequest request) {
        return CompletableFuture
                .completedFuture(AuthenticationResponse.failed("No provider configured"));
    }

    /**
     * Other extensions may update security builder.
     *
     * @return security builder
     */
    public Security.Builder securityBuilder() {
        if (null == securityBuilder) {
            throw new IllegalStateException("Security is already built, you cannot update the builder");
        }
        return securityBuilder;
    }

    /**
     * Access to security instance once it is created from {@link #securityBuilder()}.
     *
     * @return a security instance, or empty if not yet created
     */
    public Optional<Security> security() {
        return Optional.ofNullable(security.get());
    }
}
