/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.logging.common.spi;

/**
 * Used by Helidon to correctly initialize logging at build time (such as when building GraalVM native image)
 * and at runtime (to configure loggers).
 *
 * @see io.helidon.logging.common.LogConfig
 */
public interface LoggingProvider {
    /**
     * This is executed at static initialization, such as when building
     * GraalVM native image or when starting the application.
     */
    void initialization();

    /**
     * Runtime configuration, called when the application starts in GraalVM native image (not called when running on hotspot).
     */
    void runTime();
}
