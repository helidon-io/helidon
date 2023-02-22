/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.pico.car.pico;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class Car implements Vehicle {

    private Engine engine;
    private Brand brand;

    @Inject
    public Car(
            Engine engine,
            Brand brand) {
        this.engine = engine;
        this.brand = brand;
    }

    @Override
    public String toString() {
        return "Car(engine=" + engine() + ",brand=" + brand() + ")";
    }

    @Override
    public Engine engine() {
        return engine;
    }

    public void engine(Engine engine) {
        this.engine = engine;
    }

    @Override
    public Brand brand() {
        return brand;
    }

    public void brand(Brand brand) {
        this.brand = brand;
    }

}
