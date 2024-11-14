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
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration of temporary storage for classpath based handlers.
 */
@Prototype.Configured
@Prototype.Blueprint
interface TemporaryStorageConfigBlueprint extends Prototype.Factory<TemporaryStorage> {
    /**
     * Default prefix.
     */
    String DEFAULT_FILE_PREFIX = "helidon-ws";
    /**
     * Default suffix.
     */
    String DEFAULT_FILE_SUFFIX = ".je";

    /**
     * Whether the temporary storage is enabled, defaults to {@code true}.
     * If disabled, nothing is stored in temporary directory (may have performance impact, as for example a file may be
     * extracted from a zip file on each request).
     *
     * @return whether the temporary storage is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Location of the temporary storage, defaults to temporary storage configured for the JVM.
     *
     * @return directory of temporary storage
     */
    @Option.Configured
    Optional<Path> directory();

    /**
     * Prefix of the files in temporary storage.
     *
     * @return file prefix
     */
    @Option.Configured
    @Option.Default(DEFAULT_FILE_PREFIX)
    String filePrefix();

    /**
     * Suffix of the files in temporary storage.
     *
     * @return file suffix
     */
    @Option.Configured
    @Option.Default(DEFAULT_FILE_SUFFIX)
    String fileSuffix();

    /**
     * Whether temporary files should be deleted on JVM exit.
     * This is enabled by default, yet it may be useful for debugging purposes to keep the files in place.
     *
     * @return whether to delete temporary files on JVM exit
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean deleteOnExit();
}
