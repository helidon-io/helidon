/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.examples.changes;

import java.util.function.Function;
import java.util.logging.Logger;

import io.helidon.config.Config;
import static io.helidon.config.ConfigSources.directory;
import static io.helidon.config.PollingStrategies.regular;

import static java.time.Duration.ofSeconds;

/**
 * Example shows how to listen on Config node changes using simplified API, {@link Config#onChange(Function)}.
 * The Function is invoked with new instance of Config.
 * <p>
 * The feature is based on using {@link io.helidon.config.spi.PollingStrategy} with
 * selected config source(s) to check for changes.
 */
public class OnChangeExample {

    private static final Logger LOGGER = Logger.getLogger(OnChangeExample.class.getName());

    /**
     * Executes the example.
     */
    public void run() {
        Config secrets = Config
                .withSources(directory("conf/secrets")
                                     .pollingStrategy(regular(ofSeconds(5))))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        logSecrets(secrets);

        // subscribe using simple onChange function
        secrets.onChange(OnChangeExample::logSecrets);
    }

    private static boolean logSecrets(Config secrets) {
        LOGGER.info("Loaded secrets are u: " + secrets.get("username").asString()
                            + ", p: " + secrets.get("password").asString());
        return true; // -> continue listening on config changes
    }

}
