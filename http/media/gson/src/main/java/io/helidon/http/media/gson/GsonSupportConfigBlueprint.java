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

package io.helidon.http.media.gson;

import java.util.List;
import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.http.media.spi.MediaSupportProvider;

import com.google.gson.Gson;
import com.google.gson.TypeAdapterFactory;

/**
 * Configuration of the {@link GsonSupport}.
 */
@Prototype.Blueprint(decorator = GsonSupport.Decorator.class)
@Prototype.Configured(value = "gson", root = false)
@Prototype.Provides(MediaSupportProvider.class)
interface GsonSupportConfigBlueprint extends Prototype.Factory<GsonSupport> {

    /**
     * Name of the support. Default value is {@code gson}.
     *
     * @return name of the support
     */
    @Option.Default("gson")
    @Option.Configured
    String name();

    /**
     * {@link Gson} instance.
     *
     * @return gson instance
     */
    Gson gson();

    /**
     * Gson configuration properties.
     * Properties are being ignored if specific {@link Gson} is set.
     * Only {@code boolean} configuration values are supported.
     *
     * @return gson config properties
     */
    @Option.Singular("property")
    @Option.Configured
    Map<String, Boolean> properties();

    /**
     * Additional type adapter factories.
     *
     * @return type adapter factories
     */
    @Option.Singular("typeAdapterFactory")
    List<TypeAdapterFactory> typeAdapterFactories();

}
