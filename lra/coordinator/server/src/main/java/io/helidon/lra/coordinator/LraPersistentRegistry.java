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

package io.helidon.lra.coordinator;

import io.helidon.common.reactive.Multi;

/**
 * Persistable lra registry.
 */
interface LraPersistentRegistry {

    /**
     * Load persisted Lras.
     */
    void load();

    /**
     * Persist Lras.
     */
    void save();

    /**
     * Get Lra by id.
     *
     * @param lraId to look for
     * @return lra if exist
     */
    Lra get(String lraId);

    /**
     * Add new Lra.
     *
     * @param lraId id of new lra
     * @param lra   Lra
     */
    void put(String lraId, Lra lra);

    /**
     * Remove lra by id.
     *
     * @param lraId of the Lra to be removed
     */
    void remove(String lraId);

    /**
     * Stream of all Lras.
     *
     * @return stream of all the Lras
     */
    Multi<Lra> stream();

}
