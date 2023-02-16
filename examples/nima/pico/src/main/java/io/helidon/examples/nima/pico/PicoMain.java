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

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.pico.Bootstrap;
import io.helidon.pico.DefaultBootstrap;
import io.helidon.pico.PicoServices;

/**
 * As simple as possible with a fixed port.
 */
public final class PicoMain {
    private PicoMain() {
    }

    /**
     * Start the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        // todo move to a Níma on Pico module
        LogConfig.configureRuntime();

        Optional<Bootstrap> existingBootstrap = PicoServices.globalBootstrap();
        if (existingBootstrap.isEmpty()) {
            Bootstrap bootstrap = DefaultBootstrap.builder()
                    .config(Config.create())
                    .build();
            PicoServices.globalBootstrap(bootstrap);
        }

        PicoServices picoServices = PicoServices.picoServices().get();
        // now everything is started from config driven

        //        Services services = picoServices.services();

        /*
        @ConfigBean("server")
        public interface WebServerConfig {
        }

        @ConfiguredBy(WebServerConfig.class)
        public class LoomWebServer {
            @Inject
            LoomWebServer(WebServerConfig config) {
            }

            @PostConstruct
            void start() {
            }
        }
         */

        // resolve configuration
        //        Config config = services.lookup(Config.class).get();

        //
        //        Nima.config(config);
        //
        //        List<ServiceProvider<Bootstrap>> bootstrappers = services.lookupAll(Bootstrap.class);
        //        for (ServiceProvider<Bootstrap> bootstrapper : bootstrappers) {
        //            bootstrapper.get();
        //        }
        //
        //        List<ServiceProvider<HttpFeature>> features = services.lookupAll(HttpFeature.class);
        //
        //        HelidonFeatures.flavor(HelidonFlavor.NIMA);
        //        HelidonFeatures.print(HelidonFlavor.NIMA, Version.VERSION, true);
        //
        //        WebServer.builder()
        //                .config(config.get("server"))
        //                .routing(it -> {
        //                    features.stream()
        //                            .map(ServiceProvider::get)
        //                            .forEach(it::addFeature);
        //                })
        //                .start();

    }
}
