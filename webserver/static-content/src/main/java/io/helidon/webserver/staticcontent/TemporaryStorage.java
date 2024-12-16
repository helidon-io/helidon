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
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;

/**
 * Handling of temporary files.
 */
@RuntimeType.PrototypedBy(TemporaryStorageConfig.class)
public interface TemporaryStorage extends RuntimeType.Api<TemporaryStorageConfig> {
    /**
     * Create a new builder.
     *
     * @return a new fluent API builder
     */
    static TemporaryStorageConfig.Builder builder() {
        return TemporaryStorageConfig.builder();
    }

    /**
     * Create a new instance from its configuration.
     *
     * @param config configuration of temporary storage
     * @return a new configured instance
     */
    static TemporaryStorage create(TemporaryStorageConfig config) {
        return new TemporaryStorageImpl(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param consumer consumer of configuration of temporary storage
     * @return a new configured instance
     */
    static TemporaryStorage create(Consumer<TemporaryStorageConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    /**
     * Create a new instance with defaults.
     *
     * @return a new temporary storage (enabled)
     */
    static TemporaryStorage create() {
        return builder().build();
    }

    /**
     * Create a temporary file.
     *
     * @return a new temporary file, if enabled and successful
     */
    Optional<Path> createFile();
}
