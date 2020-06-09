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

package io.helidon.microprofile.oidc;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;

import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.security.providers.oidc.OidcSupport;

/**
 * Microprofile extension that brings support for Open ID Connect.
 */
public final class OidcCdiExtension implements Extension {

    private Config config;

    private void configure(@Observes @RuntimeStart Config config) {
        this.config = config;
    }

    private void registerOidcSupport(@Observes @Initialized(ApplicationScoped.class) Object adv, BeanManager bm) {
        ServerCdiExtension server = bm.getExtension(ServerCdiExtension.class);

        server.serverRoutingBuilder().register(OidcSupport.create(config));
    }
}
