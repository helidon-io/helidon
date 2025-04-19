/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen;

/**
 * Helidon Declarative Run Levels.
 */
public final class RunLevels {
    /**
     * Services to run on startup (not used by Helidon services).
     */
    public static final double STARTUP = 10D;
    /**
     * Services that establish connectivity to database.
     */
    public static final double DATA_CONNECT = 20D;
    /**
     * Services that modify data or data structures.
     */
    public static final double DATA = 25D;
    /**
     * Messaging and similar (JMS, Kafka).
     */
    public static final double MESSAGING = 30D;
    /**
     * Helidon WebServer.
     */
    public static final double SERVER = 50D;
    /**
     * Service that need other services already running, such as scheduling.
     */
    public static final double SCHEDULING = 70D;

    private RunLevels() {
    }
}
