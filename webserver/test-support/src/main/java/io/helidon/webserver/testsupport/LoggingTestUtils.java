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

package io.helidon.webserver.testsupport;

import io.helidon.common.LogConfig;

/**
 * The LoggingTestUtils.
 *
 * @deprecated This class is no longer needed, please use {@link io.helidon.common.LogConfig#configureRuntime()} instead.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
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
     *
     * @deprecated please use {@link io.helidon.common.LogConfig#configureRuntime()} instead
     */
    @Deprecated
    public static void initializeLogging() {
        LogConfig.configureRuntime();
    }
}
