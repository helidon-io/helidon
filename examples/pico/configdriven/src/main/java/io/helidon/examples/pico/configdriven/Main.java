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

package io.helidon.examples.pico.configdriven;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.examples.pico.basics.ToolBox;
import io.helidon.pico.api.BootstrapDefault;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.Services;

public class Main extends io.helidon.examples.pico.basics.Main {

    /**
     * Executes the example.
     *
     * @param args arguments
     */
    public static void main(String... args) {
        // we need to first initialize Pico - informing Pico where to find the application's Config
        Config config = Config.builder()
                .addSource(ConfigSources.classpath("application.yaml"))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        BootstrapDefault bootstrap = BootstrapDefault.builder()
                .config(config)
                .build();
        PicoServices.globalBootstrap(bootstrap);

        // the rest is handled normally as before
        io.helidon.examples.pico.basics.Main.main(args);

        // activate all of the drills
        Services services = PicoServices.realizedServices();
        List<ServiceProvider<Drill>> allDrillProviders = services.lookupAll(Drill.class);
        allDrillProviders.forEach(it -> System.out.println(it.get()));

        System.out.println("Ending");
        services.lookupFirst(ToolBox.class).get().printToolBoxContents();
    }

}
