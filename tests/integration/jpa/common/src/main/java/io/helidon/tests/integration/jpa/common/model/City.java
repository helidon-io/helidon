/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.common.model;

import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;

/**
 * City in {@code Pokemon} world
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof City city)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return Objects.equals(mayor, city.mayor) && Objects.equals(stadium, city.stadium);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mayor, stadium);
    }

    @Override
    public String toString() {
        return "City{" +
               "mayor='" + mayor + '\'' +
               ", stadium=" + stadium +
               '}';
    }
}
