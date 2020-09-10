/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server.test.types;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Abstract implementation of a {@link Vehicle}.
 */
public abstract class AbstractVehicle implements Vehicle {
    private String plate;
    private int numberOfWheels;
    private String model;
    private String make;
    private int getManufactureYear;
    private Collection<VehicleIncident> incidents;

    public AbstractVehicle(String plate, int numberOfWheels, String model, String make, int getManufactureYear) {
        this.plate = plate;
        this.numberOfWheels = numberOfWheels;
        this.model = model;
        this.make = make;
        this.getManufactureYear = getManufactureYear;
        this.incidents = new ArrayList<>();
    }

    @Override
    public Collection<VehicleIncident> getIncidents() {
        return incidents;
    }
    
    @Override
    public String getPlate() {
        return plate;
    }

    @Override
    public int getNumberOfWheels() {
        return numberOfWheels;
    }

    @Override
    public String getMake() {
        return make;
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public int getManufactureYear() {
        return getManufactureYear;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }

    public void setNumberOfWheels(int numberOfWheels) {
        this.numberOfWheels = numberOfWheels;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public void setGetManufactureYear(int getManufactureYear) {
        this.getManufactureYear = getManufactureYear;
    }
}
