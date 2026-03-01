/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.HttpMediaType;
import io.helidon.http.media.MediaSupportConfig;
import io.helidon.http.media.spi.MediaSupportProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Configuration of the {@link JacksonSupport}.
 */
@Prototype.Blueprint(decorator = JacksonConfigSupport.Decorator.class)
@Prototype.Configured(value = JacksonSupport.ID, root = false)
@Prototype.Provides(MediaSupportProvider.class)
interface JacksonSupportConfigBlueprint extends MediaSupportConfig, Prototype.Factory<JacksonSupport> {

    @Option.Default(JacksonSupport.ID)
    @Override
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

    /**
     * Types accepted by this media support.
     * When server processes the response, it checks the {@code Accept} header, to choose the right
     * media support, if there are more supports available for the provided entity object.
     * <p>
     * Supported accepted types defaults to {@value io.helidon.common.media.type.MediaTypes#APPLICATION_JSON_VALUE},
     * and {@value io.helidon.common.media.type.MediaTypes#APPLICATION_JSON_PATCH_JSON_VALUE}.
     *
     * @return accepted media types
     */
    @Option.DefaultCode("@java.util.Set@.of(@io.helidon.common.media.type.MediaTypes@.APPLICATION_JSON, @io.helidon.common"
            + ".media.type.MediaTypes@.APPLICATION_JSON_PATCH_JSON)")
    @Override
    Set<MediaType> acceptedMediaTypes();

    /**
     * Content type to use if not configured (in response headers for server, and in request headers for client).
     *
     * @return content type to use, defaults to {@link io.helidon.http.HttpMediaTypes#JSON}
     */
    @Option.DefaultCode("@io.helidon.http.HttpMediaTypes@.JSON")
    @Override
    HttpMediaType contentType();
}
