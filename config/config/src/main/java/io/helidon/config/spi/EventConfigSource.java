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

import java.util.function.BiConsumer;

/**
 * A source that supports notifications when changed.
 */
public interface EventConfigSource {
    /**
     * Register a change listener.
     *
     * @param changedNode the key and node of the configuration that changed. This may be the whole config tree, or a specific
     *                    node depending on how fine grained the detection mechanism is. To notify of a whole node being changed,
     *                    use empty string as a key
     */
    void onChange(BiConsumer<String, ConfigNode> changedNode);
}
