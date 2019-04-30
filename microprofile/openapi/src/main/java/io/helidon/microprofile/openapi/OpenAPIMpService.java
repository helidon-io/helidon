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
 *
 */
package io.helidon.microprofile.openapi;


import io.helidon.microprofile.server.spi.MpService;
import io.helidon.microprofile.server.spi.MpServiceContext;
import io.helidon.openapi.OpenAPISupport;

import io.smallrye.openapi.api.OpenApiConfigImpl;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Sets up OpenAPI support in the Helidon MP server.
 */
public class OpenAPIMpService implements MpService {

    @Override
    public void configure(MpServiceContext context) {

        OpenAPISupport openAPISupport = new MPOpenAPIBuilder()
                .openAPIConfig(new OpenApiConfigImpl(ConfigProvider.getConfig()))
                .build();

        openAPISupport.configureEndpoint(context.serverRoutingBuilder());
    }
}
