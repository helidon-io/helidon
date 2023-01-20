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

package io.helidon.pico.tools;

import java.util.Optional;

import io.helidon.builder.Builder;

/**
 * Applies only to the output paths that various {@code creators} will use (e.g., {@link ActivatorCreator}).
 */
@Builder
public interface CodeGenPaths {

    /**
     * Identifies where the meta-inf services should be written.
     *
     * @return where should meta-inf services be written
     */
    String metaInfServicesPath();

    /**
     * Identifies where is the source directory resides.
     *
     * @return the source directory
     */
    String sourcePath();

    /**
     * Identifies where the generated sources should be written.
     *
     * @return where should the generated sources be written
     */
    String generatedSourcesPath();

    /**
     * Identifies where the classes directory resides.
     *
     * @return the classes directory
     */
    String outputPath();

    /**
     * Identifies where the module-info can be found.
     *
     * @return the module-info location
     */
    Optional<String> moduleInfoPath();

}
