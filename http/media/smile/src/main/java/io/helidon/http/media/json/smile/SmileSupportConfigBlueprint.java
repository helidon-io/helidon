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

package io.helidon.http.media.json.smile;

import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.HttpMediaType;
import io.helidon.http.media.MediaSupportConfig;
import io.helidon.http.media.spi.MediaSupportProvider;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.smile.SmileConfig;

/**
 * Configuration of Smile media support.
 */
@Api.Preview
@Prototype.Configured(value = SmileSupport.ID, root = false)
@Prototype.Provides(MediaSupportProvider.class)
@Prototype.Blueprint
interface SmileSupportConfigBlueprint extends MediaSupportConfig, Prototype.Factory<SmileSupport> {

    @Override
    @Option.Default(SmileSupport.ID)
    String name();

    /**
     * JSON binding instance to use for serialization and deserialization.
     *
     * @return JSON binding instance
     */
    @Option.DefaultMethod("create")
    JsonBinding jsonBinding();

    /**
     * Smile generator configuration.
     *
     * @return smile generator configuration
     */
    @Option.DefaultMethod("create")
    SmileConfig smileConfig();

    /**
     * Types accepted by this media support.
     * When server processes the response, it checks the {@code Accept} header, to choose the right
     * media support, if there are more supports available for the provided entity object.
     * <p>
     * Supported accepted types defaults to {@value io.helidon.common.media.type.MediaTypes#APPLICATION_X_JACKSON_SMILE_VALUE}.
     *
     * @return accepted media types
     */
    @Override
    @Option.Singular
    @Option.DefaultCode("@java.util.Set@.of(@io.helidon.common.media.type.MediaTypes@.APPLICATION_X_JACKSON_SMILE)")
    Set<MediaType> acceptedMediaTypes();

    /**
     * Content type to use if not configured (in response headers for server, and in request headers for client).
     *
     * @return content type to use, defaults
     * to {@value io.helidon.common.media.type.MediaTypes#APPLICATION_X_JACKSON_SMILE_VALUE}
     */
    @Override
    @Option.DefaultCode("@io.helidon.http.HttpMediaType@.create("
            + "@io.helidon.common.media.type.MediaTypes@.APPLICATION_X_JACKSON_SMILE)")
    HttpMediaType contentType();
}
