/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config.examples.overrides;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.OverrideSources;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;
import static io.helidon.config.PollingStrategies.regular;

/**
 * Overrides example.
 * <p>
 * Shows the Overrides feature where values from config sources might be overridden by override source.
 * <p>
 * In this example, {@code application.yaml} is meant to be default application configuration distributed with an app, containing
 * a wildcard configuration nodes representing the defaults for every environment and pod as well as a default definition of
 * these values. The source {@code conf/priority-config.yaml} is a higher priority configuration source which can be in a real
 * app dynamically changed (i.e. {@code Kubernetes ConfigMap} mapped as the file) and contains the current {@code env} and {@code
 * pod} values ({@code prod} and {@code abcdef} in this example) and higher priority default configuration. So far the current
 * configuration looks like this:
 * <pre>
 * prod:
 *   abcdef:
 *     logging:
 *     level: ERROR
 *     app:
 *       greeting:  Ahoy
 *       page-size: 42
 *       basic-range:
 *         - -20
 *         -  20
 * </pre>
 * But the override source just overrides values for environment: {@code prod} and pod: {@code abcdef} (it is the first
 * overriding rule found) and value for key {@code prod.abcdef.logging.level = FINEST}. For completeness, we would say that the
 * other pods in {@code prod} environment has overridden config value {@code prod.*.logging.level} to {@code WARNING} and all
 * pods
 * {@code test.*.logging.level} to {@code FINE}.
 */
public final class Main {

    private Main() {
    }

    /**
     * Executes the example.
     *
     * @param args arguments
     * @throws InterruptedException when a sleeper awakes
     */
    public static void main(String... args) throws InterruptedException {
        Config config = Config
                .builder()
                // specify config sources
                .sources(file("conf/priority-config.yaml").pollingStrategy(regular(Duration.ofSeconds(1))),
                         classpath("application.yaml"))
                // specify overrides source
                .overrides(OverrideSources.file("conf/overrides.properties")
                                   .pollingStrategy(regular(Duration.ofSeconds(1))))
                .build();

        // Resolve current runtime context
        String env = config.get("env").asString().get();
        String pod = config.get("pod").asString().get();

        // get logging config for the current runtime
        Config loggingConfig = config
                .get(env)
                .get(pod)
                .get("logging");

        // initialize logging from config
        initLogging(loggingConfig);

        // react on changes of logging configuration
        loggingConfig.onChange(Main::initLogging);

        TimeUnit.MINUTES.sleep(1);
    }

    /**
     * Initialize logging from config.
     */
    private static void initLogging(Config loggingConfig) {
        String level = loggingConfig.get("level").asString().orElse("WARNING");
        //e.g. initialize logging using configured level...

        System.out.println("Set logging level to " + level + ".");
    }

}
