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

package io.helidon.examples.pico.car.dagger2;

public class Main {

    public static void main(String[] args) {
        final long memStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        final long start = System.currentTimeMillis();

        if (args.length > 0) {
            VehiclesModule.BRAND_NAME = args[0];
        }
        VehiclesComponent component = DaggerVehiclesComponent.create();
        System.out.println("found a car component: " + component);
        Car car = component.buildCar();
        System.out.println("found a car: " + car);
        car.engine().start();
        car.engine().stop();

        final long finish = System.currentTimeMillis();
        final long memFinish = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.println("Dagger2 Main memory consumption = " + (memFinish - memStart) + " bytes");
        System.out.println("Dagger2 Main elapsed time = " + (finish - start) + " ms");
    }

}
