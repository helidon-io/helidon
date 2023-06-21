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

package io.helidon.examples.nima.faulttolerance;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.logging.common.LogConfig;
import io.helidon.pico.api.Bootstrap;
import io.helidon.pico.api.PicoServices;

/**
 * Main class of the example, starts Helidon with injection support.
 */
public final class FtMain {
    private FtMain() {
    }

    /**
     * Start the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        // TODO move to a helidon-inject-runtime module (or similar) to set everything up
        LogConfig.configureRuntime();

        Config config = Config.builder()
                .addSource(ConfigSources.classpath("application.yaml"))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        ConfigService.config(config);

        Bootstrap bootstrap = Bootstrap.builder()
                .config(config)
                .build();

        PicoServices.globalBootstrap(bootstrap);

        PicoServices picoServices = PicoServices.picoServices().get();
        // this line is needed!
        picoServices.services();
    }
}
