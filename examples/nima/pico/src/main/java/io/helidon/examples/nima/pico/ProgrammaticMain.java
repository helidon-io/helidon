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

import io.helidon.builder.config.ConfigBean;
import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.pico.Bootstrap;
import io.helidon.pico.DefaultBootstrap;
import io.helidon.pico.PicoServices;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Main class showing customization of routing programmatically.
 * This allows for combining manual routes with discovered ones.
 */
public final class ProgrammaticMain {
    private ProgrammaticMain() {
    }

    /**
     * Start the application.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
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
        picoServices.services().lookup(Server.class).get();
    }

    @ConfigBean(key = "sockets", wantDefaultConfigBean = true)
    interface RoutingConfig {
        String name();

        int port();
    }

    @Singleton
    @Weight(100)
    //    @ConfiguredBy(RoutingConfig.class) // on the config bean, define that
    static class RoutingSource implements Provider<HttpRouting.Builder> {
        @Override
        public HttpRouting.Builder get() {
            return HttpRouting.builder();
        }
    }

    @Singleton
    @Weight(110)
    static class RoutingUpdate implements Provider<HttpRouting.Builder> {
        private final HttpRouting.Builder builder;

        @Inject
        RoutingUpdate(HttpRouting.Builder builder) {
            this.builder = builder;
        }

        @Override
        public HttpRouting.Builder get() {
            return builder.get("/test", (req, res) -> res.send("Hello"));
        }
    }

    @Singleton
    static class Server {
        //        @Inject
        //        Server(ServerConfig config, HttpRouting.Builder routing, List<Provider<HttpFeature>> features) {
        //            List<String> sockets = config.socketNames();
        //
        //
        //
        //            features.stream()
        //                    .map(Provider::get)
        //                    .forEach(routing::addFeature);
        //
        //            System.out.println(routing);
        //        }
    }
}
