/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.observe.log;

import io.helidon.common.config.Config;
import io.helidon.nima.observe.spi.ObserveProvider;
import io.helidon.nima.webserver.http.HttpRouting;

/**
 * {@link java.util.ServiceLoader} provider implementation for logging observe provider.
 * <p>
 * Java Util Logging uses weak references to loggers (and does not support adding level configuration to LogManager at runtime),
 *  so changing a log level for a logger may be temporary (in case a garbage collector runs and the reference is not kept
 *  anywhere).
 * In Helidon, most loggers are referenced for the duration of the application, so this should not impact Helidon components.
 */
public class LogObserveProvider implements ObserveProvider {
    @Override
    public String configKey() {
        return "log";
    }

    @Override
    public String defaultEndpoint() {
        return "log";
    }

    @Override
    public void register(Config config, String componentPath, HttpRouting.Builder routing) {
        routing.register(componentPath, LogService.create(config));
    }
}
