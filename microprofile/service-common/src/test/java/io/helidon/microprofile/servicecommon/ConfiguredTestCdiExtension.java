/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.servicecommon;

import io.helidon.microprofile.server.ServerCdiExtension;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * Test MP extension that relies on the test SE service which itself reads a value from config, to make sure the config used
 * is runtime not build-time config.
 */
public class ConfiguredTestCdiExtension extends HelidonRestCdiExtension {
    /**
     * Common initialization for concrete implementations.
     */
    protected ConfiguredTestCdiExtension() {
        super(System.getLogger(ConfiguredTestCdiExtension.class.getName()),
              "test");
    }

    void registerService(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class)
                         Object event,
                         ServerCdiExtension server) {

        ConfiguredTestSupport testSupport = ConfiguredTestSupport.builder()
                .config(componentConfig())
                .build();
        testSupport.setup(server.serverRoutingBuilder(), super.routingBuilder(server));
    }
}
