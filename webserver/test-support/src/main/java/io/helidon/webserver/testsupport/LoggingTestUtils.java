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

package io.helidon.webserver.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

/**
 * The LoggingTestUtils.
 */
public final class LoggingTestUtils {

    // checkstyle compliance
    private LoggingTestUtils() {
    }

    /**
     * Initialize JUL logging with {@code logging-test.properties} file that is
     * accessible on classpath.
     * <p>
     * The purpose of this method is to add a conventional way of an easy override of the standard JUL
     * defaults.
     */
    public static void initializeLogging() {
        if (System.getProperty("java.util.logging.config.file") == null) {
            try (InputStream stream = LoggingTestUtils.class.getResourceAsStream("/logging-test.properties")) {
                if (null != stream) {
                    LogManager.getLogManager().readConfiguration(stream);
                }
            } catch (IOException e) {
                // ignored for now
            }
        }
    }
}
