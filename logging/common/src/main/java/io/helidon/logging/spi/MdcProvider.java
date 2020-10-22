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
package io.helidon.logging.spi;

/**
 * Provider which is used to propagate values passed from {@link io.helidon.logging.HelidonMdc} to the
 * corresponding logging framework MDC storage.
 */
public interface MdcProvider {

    /**
     * Set value to the specific logging framework MDC storage.
     *
     * @param key entry key
     * @param value entry value
     */
    void put(String key, Object value);

    /**
     * Remove value bound to the key from the specific logging framework MDC storage.
     *
     * @param key entry to remove
     */
    void remove(String key);

    /**
     * Clear all of the MDC values from the specific logging framework MDC storage.
     */
    void clear();

}
