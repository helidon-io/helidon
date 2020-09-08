/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

/**
 * Settlement in pokemon world.
 */
@MappedSuperclass
public class Settlement {

    @Id
    @GeneratedValue
    private int id;

    private String name;

    public Settlement() {
    }

    public Settlement(String name) {
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
        if (oth instanceof Settlement) {
            return id == ((Settlement)oth).id
                    && Objects.equals(name, ((Settlement)oth).name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hashCode = id;
        if (name != null) {
            hashCode = hashCode * 31 + name.hashCode();
        }
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Settlement: {id=");
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
