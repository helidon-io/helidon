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
package io.helidon.integrations.eureka;

import java.lang.System.Logger;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.webclient.http1.Http1Client;

import static java.lang.System.getLogger;

/**
 * Support for additional customization of the {@link EurekaRegistrationConfig} prototype.
 */
final class EurekaRegistrationConfigSupport {

    private EurekaRegistrationConfigSupport() {
        super();
    }

    static final class BuilderDecorator implements Prototype.BuilderDecorator<EurekaRegistrationConfig.BuilderBase<?, ?>> {

        private static final Logger LOGGER = getLogger(BuilderDecorator.class.getName());

        BuilderDecorator() {
            super();
        }

        @Override // Prototype.BuilderDecorator<EurekaRegistrationConfig.BuilderBase<?, ?>>
        public void decorate(EurekaRegistrationConfig.BuilderBase<?, ?> builder) {
            if (builder.clientBuilderSupplier().isEmpty()) {
                Config config = builder.config().orElseGet(Config::empty).get("client");
                if (config.isObject()) {
                    builder.clientBuilderSupplier(() -> Http1Client.builder().config(config));
                }
            }
        }

    }

}
