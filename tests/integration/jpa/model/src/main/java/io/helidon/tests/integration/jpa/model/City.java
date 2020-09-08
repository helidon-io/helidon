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

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;

/**
 * City in pokemon world
 */
@Entity
public class City extends Settlement {

    private String mayor;

    @OneToOne(cascade = CascadeType.ALL)
    @MapsId
    private Stadium stadium;

    public City() {
    }

    public City(String name, String mayor, Stadium stadium) {
        super(name);
        this.mayor = mayor;
        this.stadium = stadium;
    }

    public String getMayor() {
        return mayor;
    }

    public void setMayor(String mayor) {
        this.mayor = mayor;
    }

    public Stadium getStadium() {
        return stadium;
    }

    public void setStadium(Stadium stadium) {
        this.stadium = stadium;
    }

    @Override
    public boolean equals(Object oth) {
        if (this == oth) {
            return true;
        }
        if (oth == null) {
            return false;
        }
        if (super.equals(oth) && (oth instanceof City)) {
            return Objects.equals(mayor, ((City) oth).mayor)
                    && Objects.equals(stadium, ((City) oth).stadium);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        if (mayor != null) {
            hashCode = hashCode * 31 + mayor.hashCode();
        }
        if (stadium != null) {
            hashCode = hashCode * 31 + stadium.hashCode();
        }
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Town: {id=");
        sb.append(getId());
        sb.append(", name=");
        if (getName() != null) {
            sb.append('"').append(getName()).append('"');
        } else {
            sb.append("<null>");
        }
        sb.append(", mayor=");
        if (mayor != null) {
            sb.append('"').append(mayor).append('"');
        } else {
            sb.append("<null>");
        }
        sb.append(", stadium=");
        if (stadium != null) {
            sb.append('"').append(stadium.toString()).append('"');
        } else {
            sb.append("<null>");
        }
        sb.append('}');
        return sb.toString();
    }

}
