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

package io.helidon.examples.inject.configdriven;

import io.helidon.examples.inject.basics.ToolBox;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;

/**
 * Config-driven example.
 */
public class Main {

    /**
     * Executes the example.
     *
     * @param args arguments
     */
    public static void main(String... args) {
        // the config driven registry will use GlobalConfig by default, which in turn uses application.yaml


        // this drives config-driven service activations (see the contents of the toolbox being output)
        Services services = InjectionServices.create().services();

        // this will trigger the PostConstruct method to display the contents of the toolbox
        services.get(ToolBox.class);
    }

}
