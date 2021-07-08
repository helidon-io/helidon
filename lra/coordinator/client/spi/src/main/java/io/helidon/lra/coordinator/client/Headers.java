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
 *  
 */

package io.helidon.lra.coordinator.client;

import java.util.List;

/**
 * Abstraction over the structure used for sending LRA id by coordinatior.
 */
public interface Headers {

    /**
     * Returns a list of the values for provided key.
     *
     * @param name key
     * @return list of the values
     */
    List<String> get(String name);

    /**
     * Replace any existing values of the given key by single provided value.
     *
     * @param name  key
     * @param value value
     */
    void putSingle(String name, String value);
}
