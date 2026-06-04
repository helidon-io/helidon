/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.openapi;

import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;

final class OpenApiFeatureConfigSupport {
    private OpenApiFeatureConfigSupport() {
    }

    @Prototype.RuntimeTypeFactoryMethod
    static OpenApiFeature create(OpenApiFeatureConfig config) {
        ServiceRegistry registry = config.runtimeServiceRegistry()
                .orElseGet(GlobalServiceRegistry::registry);
        Config sourceConfig = config.sourceRoot()
                .map(Config.class::cast)
                .orElseGet(Config::empty);
        return new OpenApiFeature(registry, sourceConfig, config);
    }

    static final class BuilderDecorator implements Prototype.BuilderDecorator<OpenApiFeatureConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(OpenApiFeatureConfig.BuilderBase<?, ?> builder) {
            builder.serviceRegistry().ifPresent(builder::runtimeServiceRegistry);
            builder.config()
                    .map(Config::root)
                    .ifPresent(builder::sourceRoot);
        }
    }

    static final class EnabledDecorator implements Prototype.OptionDecorator<OpenApiFeatureConfig.BuilderBase<?, ?>, Boolean> {
        @Override
        public void decorate(OpenApiFeatureConfig.BuilderBase<?, ?> builder, Boolean enabled) {
            if (!enabled) {
                OpenApiFeature.disableProviderDiscovery(builder);
            }
        }
    }
}
