/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.tests.mixed.provider;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Entity used by the Jakarta Persistence repository fixture.
 */
@Entity
public class JakartaEntity {
    @Id
    private int id;

    /**
     * Creates an entity.
     */
    public JakartaEntity() {
    }

    /**
     * Returns the entity identifier.
     *
     * @return entity identifier
     */
    public int id() {
        return id;
    }

    /**
     * Updates the entity identifier.
     *
     * @param id entity identifier
     */
    public void id(int id) {
        this.id = id;
    }
}
