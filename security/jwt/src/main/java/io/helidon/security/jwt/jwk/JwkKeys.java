/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.jwt.jwk;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReaderFactory;

import io.helidon.common.configurable.Resource;

/**
 * A representation of the JSON web keys document - a map of key ids to corresponding web keys.
 * The keys may be of different types and algorithms.
 *
 * Example (as used in unit test):
 * <pre>
 * JwkKeys keys = JwkKeys.builder()
 * .resource("jwk_data.json")
 * .build();
 *
 * Optional&lt;Jwk&gt; key = keys
 * .forKeyId("cc34c0a0-bd5a-4a3c-a50d-a2a7db7643df");
 * </pre>
 */
public final class JwkKeys {
    private static final Logger LOGGER = Logger.getLogger(JwkKeys.class.getName());
    private static final JsonReaderFactory JSON = Json.createReaderFactory(Collections.emptyMap());

    private final Map<String, Jwk> keyMap = new HashMap<>();
    private final List<Jwk> noKeyIdKeys = new LinkedList<>();

    private JwkKeys(Builder builder) {
        this.keyMap.putAll(builder.keyMap);
        this.noKeyIdKeys.addAll(builder.noKeyIdKeys);
    }

    /**
     * Create a new builder for this class.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get a JWK for defined key id if present.
     *
     * @param keyId keyId of the key to obtain from this keys
     * @return Jwk if present
     */
    public Optional<Jwk> forKeyId(String keyId) {
        return Optional.ofNullable(keyMap.get(keyId));
    }

    /**
     * List of keys in this instance.
     *
     * @return all keys configured
     */
    public List<Jwk> keys() {
        List<Jwk> result = new LinkedList<>();
        result.addAll(noKeyIdKeys);
        result.addAll(keyMap.values());
        return result;
    }

    /**
     * Builder of {@link JwkKeys}.
     */
    public static final class Builder implements io.helidon.common.Builder<JwkKeys> {
        private final List<Jwk> noKeyIdKeys = new LinkedList<>();
        private final Map<String, Jwk> keyMap = new HashMap<>();

        private Builder() {
        }

        /**
         * Build a new keys instance.
         *
         * @return JwkKeys created from this builder
         */
        @Override
        public JwkKeys build() {
            return new JwkKeys(this);
        }

        /**
         * Add a new JWK to this keys.
         *
         * @param key the JWK instance
         * @return updated builder instance
         */
        public Builder addKey(Jwk key) {
            Objects.requireNonNull(key, "Key must not be null");
            if (null == key.keyId()) {
                noKeyIdKeys.add(key);
            } else {
                keyMap.put(key.keyId(), key);
            }
            return this;
        }

        /**
         * Load keys from a resource (must point to JSON text content).
         *
         * @param resource the resource with JSON data (file, classpath, URI etc.)
         * @return updated builder instance
         * @throws NullPointerException in case the path is null
         */
        public Builder resource(Resource resource) {
            Objects.requireNonNull(resource, "Json resource must not be null");
            try (InputStream is = resource.stream()) {
                JsonObject jsonObject = JSON.createReader(is).readObject();
                addKeys(jsonObject);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to close input stream on resource: " + resource);
            }

            return this;
        }

        private void addKeys(JsonObject jsonObject) {
            JsonArray keyArray = jsonObject.getJsonArray("keys");
            keyArray.forEach(it -> {
                JsonObject aKey = (JsonObject) it;
                try {
                    addKey(Jwk.create(aKey));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                               "Could not process a key from JWK JSON, this key will not be available",
                               e);
                }
            });
        }
    }
}
