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
package io.helidon.metrics.api;

/**
 * Behavior of a tag for further identifying meters.
 */
public interface Tag extends Wrapper {

    /**
     * Creates a new tag using the specified key and value.
     *
     * @param key the tag's key
     * @param value the tag's value
     * @return new {@code Tag} representing the key and value
     */
    static Tag create(String key, String value) {
        return MetricsFactory.getInstance().tagCreate(key, value);
    }

    /**
     * Returns the tag's key.
     *
     * @return the tag's key
     */
    String key();

    /**
     * Returns the tag's value.
     *
     * @return the tag's value
     */
    String value();
}
