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

import java.util.Optional;

/**
 * A secrets engine.
 * Supported built-in engines are available as constants.
 *
 * @param <T> type of the {@link io.helidon.integrations.vault.Secret} provided by the engine
 */
public interface Engine<T extends Secrets> {
    /**
     * Create a new versioned engine.
     *
     * @param secrets secrets class
     * @param type type of engine
     * @param defaultMount default path to mount this engine on
     * @param version version of engine
     * @param <T> type of secrets
     * @return a new engine
     */
    static <T extends Secrets> Engine<T> create(Class<T> secrets, String type, String defaultMount, String version) {
        return EngineImpl.create(secrets, type, defaultMount, version);
    }

    /**
     * Create an engine.
     *
     * @param secrets secrets class
     * @param type type of engine
     * @param defaultMount default path to mount this engine on
     * @param <T> type of secrets
     * @return a new engine
     */
    static <T extends Secrets> Engine<T> create(Class<T> secrets, String type, String defaultMount) {
        return EngineImpl.create(secrets, type, defaultMount);
    }

    /**
     * Type of the engine as used in the REST API.
     *
     * @return type of the engine, such as {@code kv}
     */
    String type();

    /**
     * Version of the engine if versioned.
     *
     * @return version of the engine (such as 1 or 2 for KV engines)
     */
    Optional<String> version();

    /**
     * Implementation class of the {@link io.helidon.integrations.vault.Secrets} of this engine.
     *
     * @return secrets class
     * @see io.helidon.integrations.vault.Vault#secrets(Engine)
     */
    Class<T> secretsType();

    /**
     * Default path to mount this engine on.
     *
     * @return default mount path
     */
    String defaultMount();
}
