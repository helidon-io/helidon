/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.model;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * Type entity.
 */
@Entity
public class Type implements Serializable {

    @Id
    private int id;
    
    private String name;

    public Type() {
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
    public boolean equals(Object oth) {
        if (this == oth) {
            return true;
        }
        if (oth == null) {
            return false;
        }
        if (oth instanceof Type) {
            return id == ((Type)oth).id && Objects.equals(name, ((Type)oth).name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * id + (name != null ? name.hashCode() : 0);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Type: {id=");
        sb.append(id);
        sb.append(", name=");
        if (name != null) {
            sb.append('"').append(name).append('"');
        } else {
            sb.append("<null>");
        }
        sb.append('}');
        return sb.toString();
    }

}
