/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.inject.interceptors;

import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;

/**
 * Interceptors example.
 */
public class Main {

    /**
     * Executes the example.
     *
     * @param args arguments
     */
    public static void main(String... args) {
        // global service registry
        Services services = InjectionServices.create().services();

        // use the intercepted screwdriver - note that hashCode(), equals(), and toString() are not intercepted
        ScrewDriver screwDriver = services.get(ScrewDriver.class);
        System.out.println(screwDriver + " (1st turn): ");
        screwDriver.turn("left");

        // use the intercepted screwdriver turning tool - note that hashCode(), equals(), and toString() are not intercepted
        TurningTool turningTool = services.get(TurningTool.class);
        System.out.println(turningTool + " (2nd turn): ");
        turningTool.turn("left");
    }

}
