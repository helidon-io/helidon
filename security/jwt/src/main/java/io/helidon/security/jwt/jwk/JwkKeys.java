/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

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
public class JwkKeys {
    private static final Logger LOGGER = Logger.getLogger(JwkKeys.class.getName());

    private final Map<String, Jwk> keyMap;

    private JwkKeys(Map<String, Jwk> keyMap) {
        this.keyMap = keyMap;
    }

    /**
     * Create a new builder for {@link JwkKeys}.
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
     * Builder of {@link JwkKeys}.
     */
    public static class Builder implements io.helidon.common.Builder<JwkKeys> {
        private Map<String, Jwk> keyMap = new HashMap<>();

        /**
         * Build a new keys instance.
         *
         * @return JwkKeys created from this builder
         */
        @Override
        public JwkKeys build() {
            return new JwkKeys(new HashMap<>(keyMap));
        }

        /**
         * Add a new JWK to this keys.
         *
         * @param key the JWK instance
         * @return updated builder instance
         */
        public Builder addKey(Jwk key) {
            Objects.requireNonNull(key, "Key must not be null");
            Objects.requireNonNull(key.getKeyId(), "Key id must not be null for key: " + key);

            keyMap.put(key.getKeyId(), key);
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
            try (InputStream is = resource.getStream()) {
                JsonObject jsonObject = Json.createReader(is).readObject();
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
                addKey(Jwk.fromJson(aKey));
            });
        }
    }
}
