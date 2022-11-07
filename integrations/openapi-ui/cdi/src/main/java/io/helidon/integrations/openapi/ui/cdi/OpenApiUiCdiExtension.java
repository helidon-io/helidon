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
package io.helidon.integrations.openapi.ui.cdi;

import io.helidon.config.Config;
import io.helidon.config.mp.MpConfig;
import io.helidon.integrations.openapi.ui.OpenApiUiSupport;
import io.helidon.microprofile.openapi.OpenApiCdiExtension;
import io.helidon.microprofile.server.RoutingBuilders;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Extension;
import org.eclipse.microprofile.config.ConfigProvider;


/**
 * CDI extension for initializing OpenAPI U/I in Helidon MP applications.
 */
public class OpenApiUiCdiExtension implements Extension {

    private static final System.Logger LOGGER = System.getLogger(OpenApiUiCdiExtension.class.getName());

    // Must run after the OpenApiCdiExtension's observer of the same event.
    private void registerService(@Observes
                                 @Priority(OpenApiCdiExtension.STARTUP_OBSERVER_PRIORITY + 1)
                                 @Initialized(ApplicationScoped.class)
                                 Object event,
                                 OpenApiCdiExtension openApiCdiExtension) {

        Config openApiUiConfig = MpConfig.toHelidonConfig(ConfigProvider.getConfig())
                .get(OpenApiUiSupport.Builder.OPENAPI_UI_CONFIG_KEY);

        OpenApiUiSupport uiSupport = OpenApiUiSupport.create(openApiCdiExtension.service(), openApiUiConfig);

        RoutingBuilders routingBuilders = RoutingBuilders.create(openApiUiConfig);

        uiSupport.configureEndpoint(routingBuilders.defaultRoutingBuilder(), routingBuilders.routingBuilder());
    }
}
