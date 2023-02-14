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

package io.helidon.examples.nima.pico;

import java.util.List;

import io.helidon.common.Version;
import io.helidon.common.features.HelidonFeatures;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.Nima;
import io.helidon.pico.Bootstrap;
import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;

/**
 * As simple as possible with a fixed port.
 */
public class PicoMain {
    /**
     * Start the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        // todo move to a Níma on Pico module
        LogConfig.configureRuntime();

        PicoServices picoServices = PicoServices.picoServices().get();
        Services services = picoServices.services();
        // resolve configuration
        Config config = services.lookup(Config.class).get();
        Nima.config(config);
        HelidonFeatures.flavor(HelidonFlavor.NIMA);
        HelidonFeatures.print(HelidonFlavor.NIMA, Version.VERSION, config.get("nima.features.print-details")
                .asBoolean()
                .orElse(false));

        List<ServiceProvider<Bootstrap>> bootstrappers = services.lookupAll(Bootstrap.class);
        for (ServiceProvider<Bootstrap> bootstrapper : bootstrappers) {
            bootstrapper.get();
        }
    }
}
