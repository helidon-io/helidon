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

import java.util.Locale;

import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;

class ExtendedTracerConfigBlueprintSupport {

    private ExtendedTracerConfigBlueprintSupport() {
    }

    @Prototype.ConfigFactoryMethod("samplerType")
    static SamplerType createSamplerType(Config config) {
        String samplerType = config.asString().get().toUpperCase(Locale.ROOT);
        samplerType = "CONST".equals(samplerType) ? SamplerType.CONSTANT.name() : samplerType;
        return SamplerType.valueOf(samplerType);
    }
}
