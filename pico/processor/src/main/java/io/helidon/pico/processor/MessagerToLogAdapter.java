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

package io.helidon.pico.processor;

import io.helidon.pico.tools.Msgr;

class MessagerToLogAdapter implements Msgr {

    private final System.Logger logger;

    MessagerToLogAdapter(
            System.Logger logger) {
        this.logger = logger;
    }

    @Override
    public void debug(
            String message) {
        logger.log(System.Logger.Level.DEBUG, message);
    }

    @Override
    public void debug(
            String message,
            Throwable t) {
        logger.log(System.Logger.Level.DEBUG, message, t);
    }

    @Override
    public void log(String message) {
        logger.log(System.Logger.Level.INFO, message);
    }

    @Override
    public void warn(
            String message) {
        logger.log(System.Logger.Level.WARNING, message);
    }

    @Override
    public void warn(
            String message,
            Throwable t) {
        logger.log(System.Logger.Level.WARNING, message, t);
    }

    @Override
    public void error(
            String message,
            Throwable t) {
        logger.log(System.Logger.Level.ERROR, message, t);
    }

}
