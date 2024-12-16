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

import java.nio.file.Path;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * File system based static content handler configuration.
 */
@Prototype.Configured
@Prototype.Blueprint
@Prototype.CustomMethods(StaticContentConfigSupport.FileSystemMethods.class)
interface FileSystemHandlerConfigBlueprint extends BaseHandlerConfigBlueprint {
    /**
     * The directory (or a single file) that contains the root of the static content.
     *
     * @return location to serve the static content, such as {@code "/home/user/static-content"}.
     */
    @Option.Configured
    Path location();
}
