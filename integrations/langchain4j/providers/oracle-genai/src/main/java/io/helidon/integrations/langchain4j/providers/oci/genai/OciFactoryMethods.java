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

package io.helidon.integrations.langchain4j.providers.oci.genai;

import io.helidon.builder.api.Prototype;

import com.oracle.bmc.Region;
import com.oracle.bmc.generativeaiinference.model.ServingMode;

/**
 * Config mappers for special OCI SDK types.
 */
final class OciFactoryMethods {

    private OciFactoryMethods() {
    }

    @Prototype.FactoryMethod
    static Region createRegion(io.helidon.common.config.Config config) {
        return config.asString()
                .map(Region::fromRegionCodeOrId)
                .get();
    }

    @Prototype.FactoryMethod
    static ServingMode.ServingType createServingType(io.helidon.common.config.Config config) {
        return config.asString()
                .map(ServingMode.ServingType::create)
                .get();
    }
}
