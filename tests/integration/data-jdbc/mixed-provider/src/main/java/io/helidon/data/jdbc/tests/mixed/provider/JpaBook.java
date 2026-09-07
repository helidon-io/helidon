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
package io.helidon.data.jdbc.tests.mixed.provider;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity read through the Jakarta Persistence repository.
 */
@Entity
@Table(name = "JPA_BOOK")
public class JpaBook {
    @Id
    private int id;
    private String title;

    /**
     * Creates an empty entity for Jakarta Persistence.
     */
    public JpaBook() {
    }

    /**
     * Returns the identifier.
     *
     * @return identifier
     */
    public int getId() {
        return id;
    }

    /**
     * Updates the identifier.
     *
     * @param id identifier
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Returns the title.
     *
     * @return title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Updates the title.
     *
     * @param title title
     */
    public void setTitle(String title) {
        this.title = title;
    }
}
