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

package io.helidon.tracing;

import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;

class ExtendedTracerConfigBlueprintSupport {
    private static final System.Logger LOGGER = System.getLogger(ExtendedTracerConfigBlueprintSupport.class.getName());

    private ExtendedTracerConfigBlueprintSupport() {
    }

    @Prototype.ConfigFactoryMethod("samplerType")
    static SamplerType createSamplerType(Config config) {
        try {
            return config.as(SamplerType.class).get();
        } catch (ConfigMappingException e) {
            if (config.isLeaf() && "const".equalsIgnoreCase(config.asString().get())) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "Sampler type \"const\" is deprecated; use \"constant\" instead");
                return SamplerType.CONSTANT;
            }
            throw e;
        }
    }
}
