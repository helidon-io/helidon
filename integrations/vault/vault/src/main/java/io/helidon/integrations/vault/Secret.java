/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault;

import java.util.Map;
import java.util.Optional;

/**
 * Common methods for secrets.
 * The methods {@link #value(String)} and {@link #values()} may have different semantic meaning
 * depending on secrets engine used, please check documentation of subclasses.
 */
public interface Secret {
    /**
     * Path of this secret.
     *
     * @return path in the vault, relative to mount point
     */
    String path();

    /**
     * Value of a key within a secret.
     *
     * @param key key of the secret's value
     * @return value if the key exists
     */
    Optional<String> value(String key);

    /**
     * A map of secret values (key/value pairs).
     *
     * @return a map of all values available
     */
    Map<String, String> values();
}
