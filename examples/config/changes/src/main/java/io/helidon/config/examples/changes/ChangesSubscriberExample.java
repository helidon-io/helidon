/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Flow;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.PollingStrategies;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;
import static io.helidon.config.PollingStrategies.regular;
import static java.time.Duration.ofSeconds;

/**
 * Example shows how to listen on Config node changes using
 * {@link Flow.Subscriber}. Method {@link Flow.Subscriber#onNext(Object) onNext}
 * is invoked with new instance of Config, see {@link Config#changes()} for more
 * detail.
 * <p>
 * The feature is based on using {@link io.helidon.config.spi.PollingStrategy}
 * with selected config source(s) to check for changes.
 * <p>
 * <h2>A note about {@code Config.changes() }, {@code Flow.Publisher}, and
 * {@code Flow.Subscriber}</h2>
 * This example uses the {@link Config#changes() } API. That method is marked as
 * deprecated because it its return type is the Helidon-specific interface
 * {@link Flow.Publisher} which mimics the interface with the same name in Java
 * 9 and later. Similarly the {@link Flow.Publisher#subscribe} method accepts a
 * Helidon-specific {@link Flow.Subscriber}.
 * <p>
 * Once Helidon requires Java 9 or later (as opposed to Java 8), the
 * {@code Config.changes()} API might change to return the Java
 * {@code Flow.Subscriber} interface instead of the Helidon-specific one. By
 * marking the method as deprecated we encourage developers to be very careful
 * in how they use the method and, specifically, its return value and the
 * argument to {@code subscribe}. Developers should avoid propagating these
 * Helidon-specific types throughout their code to minimize the disruption
 * if and when the {@code Config.changes()} method evolves to return the Java,
 * not Helidon, {@code Flow.Publisher}.
 */
public class ChangesSubscriberExample {

    private static final Logger LOGGER = Logger.getLogger(ChangesSubscriberExample.class.getName());

    /**
     * Executes the example.
     */
    public void run() {
        Config config = Config
                .create(file("conf/dev.yaml")
                        .optional()
                        .pollingStrategy(PollingStrategies::watch),
                        file("conf/config.yaml")
                                .optional()
                                .pollingStrategy(regular(ofSeconds(2))),
                        classpath("default.yaml")
                                .pollingStrategy(regular(ofSeconds(10))));

        // first greeting
        greeting(config.get("app"));

        // subscribe using custom Flow.Subscriber - see class-level JavaDoc
        config.get("app").changes()
                .subscribe(new AppConfigSubscriber());
    }

    private void greeting(Config appConfig) {
        LOGGER.info("[ChangesSubscriber] " + appConfig.get("greeting").asString() + " " + appConfig.get("name").asString() + ".");
    }

    /**
     * Flow Subscriber on "app" config node.
     */
    private class AppConfigSubscriber implements Flow.Subscriber<Config> {

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Config config) {
            greeting(config);
        }

        @Override
        public void onError(Throwable throwable) {
            LOGGER.log(Level.WARNING,
                    throwable,
                    () -> "Config Changes support failed. " + throwable.getLocalizedMessage());
        }

        @Override
        public void onComplete() {
            LOGGER.info("Config Changes support finished. There will no other Config reload.");
        }

    }

}
