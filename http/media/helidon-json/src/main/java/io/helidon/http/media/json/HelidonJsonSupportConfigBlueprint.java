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

package io.helidon.http.media.json;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.http.media.spi.MediaSupportProvider;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.binding.JsonBindingConfig;

/**
 * Configuration blueprint for Helidon JSON media support.
 * <p>
 * This interface defines the configuration options for the Helidon JSON media support,
 * which provides JSON serialization and deserialization capabilities for HTTP requests
 * and responses.
 */
@Prototype.Configured(value = HelidonJsonSupport.HELIDON_JSON_TYPE, root = false)
@Prototype.Provides(MediaSupportProvider.class)
@Prototype.Blueprint(decorator = HelidonJsonSupport.Decorator.class)
interface HelidonJsonSupportConfigBlueprint extends Prototype.Factory<HelidonJsonSupport> {

    /**
     * Name of the support. Default value is {@code helidon-json}.
     *
     * @return name of the support
     */
    @Option.Default(HelidonJsonSupport.HELIDON_JSON_TYPE)
    @Option.Configured
    String name();

    /**
     * Configuration of the Helidon JSON Binding component.
     *
     * @return json binding configuration
     */
    @Option.DefaultMethod("create")
    @Option.Configured(merge = true)
    JsonBindingConfig jsonBindingConfig();

    /**
     * JSON binding instance to use for serialization and deserialization.
     *
     * @return JSON binding instance
     */
    JsonBinding jsonBinding();

}
