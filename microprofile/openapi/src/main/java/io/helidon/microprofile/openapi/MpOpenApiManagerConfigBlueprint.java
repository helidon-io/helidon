/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi;

import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * {@link MpOpenApiManager} prototype.
 */
@Prototype.Blueprint
@Configured
interface MpOpenApiManagerConfigBlueprint {

    /**
     * If {@code true} and the {@code jakarta.ws.rs.core.Application} class returns a non-empty set, endpoints defined by
     * other resources are not included in the OpenAPI document.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    @ConfiguredOption(key = MpOpenApiManager.USE_JAXRS_SEMANTICS_KEY)
    boolean useJaxRsSemantics();

    /**
     * Specify the set of Jandex index path.
     *
     * @return list of Jandex index path
     */
    @ConfiguredOption(configured = false, value = "META-INF/jandex.idx")
    List<String> indexPaths();
}
