/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.logging.log4j;

import io.helidon.logging.common.spi.LoggingProvider;

import org.apache.logging.log4j.LogManager;

/**
 * Log4j logging provider.
 */
public class Log4jProvider implements LoggingProvider {
    @Override
    public void initialization() {
        LogManager.getLogger(Log4jProvider.class)
                .info("Logging initialization.");
    }

    @Override
    public void runTime() {
        LogManager.getLogger(Log4jProvider.class)
                .info("Logging runtime.");
    }
}
