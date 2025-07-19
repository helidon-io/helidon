/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.transaction.data.mp.model;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tests Model: Pokemon type.
 */
@Entity
@Table(name = "TYPE")
public class Type {

    @Id
    private int id;

    private String name;

    public Type() {
        this(-1, null);
    }

    public Type(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        return id == ((Type) obj).id
                && Objects.equals(name, ((Type) obj).name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName());
        sb.append(" {id=");
        sb.append(id);
        sb.append(", name=");
        sb.append(name);
        sb.append("}");
        return sb.toString();
    }

    /**
     * Creates clone of provided {@link Type} instance.
     *
     * @param src source instance to be cloned
     * @return the clone
     */
    public static Type clone(Type src) {
        return new Type(src.getId(), src.getName());
    }

}
