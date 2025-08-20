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

package io.helidon.http.media.jackson;

import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.http.media.spi.MediaSupportProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Configuration of the {@link JacksonSupport}.
 */
@Prototype.Blueprint(decorator = JacksonSupport.Decorator.class)
@Prototype.Configured(value = "jackson", root = false)
@Prototype.Provides(MediaSupportProvider.class)
interface JacksonSupportConfigBlueprint extends Prototype.Factory<JacksonSupport> {

    /**
     * Name of the support. Default value is {@code jackson}.
     *
     * @return name of the support
     */
    @Option.Default("jackson")
    @Option.Configured
    String name();

    /**
     * Jackson {@link com.fasterxml.jackson.databind.ObjectMapper} instance.
     *
     * @return jackson object mapper instance
     */
    ObjectMapper objectMapper();

    /**
     * Jackson configuration properties.
     * Properties are being ignored if specific {@link JacksonSupport} is set.
     * Only {@code boolean} configuration values are supported.
     *
     * @return jackson config properties
     */
    @Option.Singular("property")
    @Option.Configured
    Map<String, Boolean> properties();

}
