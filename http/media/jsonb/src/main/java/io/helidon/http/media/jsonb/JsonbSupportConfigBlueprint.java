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

package io.helidon.http.media.jsonb;

import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.http.media.spi.MediaSupportProvider;

import jakarta.json.bind.Jsonb;

/**
 * Configuration of the {@link JsonbSupport}.
 */
@Prototype.Blueprint(decorator = JsonbSupport.Decorator.class)
@Prototype.Configured(value = "jsonb", root = false)
@Prototype.Provides(MediaSupportProvider.class)
interface JsonbSupportConfigBlueprint extends Prototype.Factory<JsonbSupport> {

    /**
     * Name of the support. Default value is {@code jsonb}.
     *
     * @return name of the support
     */
    @Option.Default("jsonb")
    @Option.Configured
    String name();

    /**
     * Jsonb instance.
     *
     * @return jsonb instance
     */
    Jsonb jsonb();

    /**
     * Jsonb {@link String} configuration properties.
     * Properties are being ignored if specific {@link Jsonb} is set.
     *
     * @return jsonb config properties
     */
    @Option.Configured("properties")
    @Option.Access("")
    Map<String, String> stringProperties();

    /**
     * Jsonb {@code boolean} configuration properties.
     * Properties are being ignored if specific {@link Jsonb} is set.
     *
     * @return jsonb config properties
     */
    @Option.Configured
    @Option.Access("")
    Map<String, Boolean> booleanProperties();

    /**
     * Jsonb {@link Class} configuration properties.
     * Properties are being ignored if specific {@link Jsonb} is set.
     *
     * @return jsonb config properties
     */
    @Option.Configured
    @Option.Access("")
    @Option.TraverseConfig
    Map<String, Class<?>> classProperties();

    /**
     * Jsonb configuration properties.
     * Properties are being ignored if specific {@link Jsonb} is set.
     *
     * @return jsonb config properties
     */
    @Option.Singular("property")
    Map<String, Object> properties();

}
