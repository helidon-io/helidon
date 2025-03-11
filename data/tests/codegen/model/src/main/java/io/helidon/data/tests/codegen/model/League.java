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
package io.helidon.data.tests.codegen.model;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tests Model: Pokemon league.
 */
@Entity
@Table(name = "LEAGUE")
public class League {

    @Id
    private int id;

    private String name;
    @ManyToOne
    @JoinColumn(name = "REGION_ID")
    private Region region;

    public League() {
        this(-1, null, null);
    }

    public League(int id, String name, Region region) {
        this.id = id;
        this.name = name;
        this.region = region;
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

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        return id == ((League) obj).id
                && Objects.equals(name, ((League) obj).name)
                && Objects.equals(region, ((League) obj).region);
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
        sb.append(", region=");
        sb.append(region.toString());
        sb.append("}");
        return sb.toString();
    }

}
