/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config.spi;

import java.util.Optional;

import io.helidon.config.ConfigException;

/**
 * An eager source that can read all data from the underlying origin as a configuration node.
 */
public interface NodeConfigSource extends ConfigSource {
    /**
     * Loads the underlying source data. This method is only called when the source {@link #exists()}.
     * <p>
     * The method can be invoked repeatedly, for example during retries.
     *
     * @return An instance of {@code T} as read from the underlying origin of the data (if it exists)
     * @throws io.helidon.config.ConfigException in case of errors loading from the underlying origin
     */
    Optional<ConfigContent.NodeContent> load() throws ConfigException;
}
