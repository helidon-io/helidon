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

/**
 * A source that is not capable of loading all keys at once.
 * Even though such a source can be used in Helidon Config, there are limitations to its use.
 *
 * The following methods may ignore data from a lazy source (may for cases when the node was not invoked directly):
 * <ul>
 *     <li>{@link io.helidon.config.Config#asMap()}</li>
 *     <li>{@link io.helidon.config.Config#asNodeList()}</li>
 *     <li>{@link io.helidon.config.Config#traverse()}</li>
 * </ul>
 */
public interface LazyConfigSource {
    /**
     * Provide a value for the node on the requested key.
     *
     * @param key config key to obtain
     * @return value of the node if available in the source
     */
    Optional<ConfigNode> node(String key);
}
