/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.staticcontent;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Classpath based static content handler configuration.
 */
@Prototype.Configured
@Prototype.Blueprint
@Prototype.CustomMethods(StaticContentConfigSupport.ClasspathMethods.class)
interface ClasspathHandlerConfigBlueprint extends BaseHandlerConfigBlueprint {
    /**
     * The location on classpath that contains the root of the static content.
     * This should never be the root (i.e. {@code /}), as that would allow serving of all class files.
     *
     * @return location on classpath to serve the static content, such as {@code "/web"}.
     */
    @Option.Configured
    String location();

    /**
     * Customization of temporary storage configuration.
     *
     * @return temporary storage config
     */
    @Option.Configured
    Optional<TemporaryStorage> temporaryStorage();

    /**
     * Class loader to use to lookup the static content resources from classpath.
     *
     * @return class loader to use
     */
    Optional<ClassLoader> classLoader();
}
