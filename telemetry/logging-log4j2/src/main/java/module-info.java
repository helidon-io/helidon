/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
/**
 * Optional Log4j 2 log capture for OpenTelemetry.
 */
@SuppressWarnings("requires-automatic")
module io.helidon.telemetry.logging.log4j2 {
    requires io.helidon.common;
    requires transitive io.helidon.telemetry.logging;
    requires io.helidon.logging.log4j;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires io.opentelemetry.instrumentation.log4j_appender_2_17;
    requires java.desktop;

    exports io.helidon.telemetry.logging.log4j2;

    provides io.helidon.telemetry.logging.spi.LogBridgeProvider
            with io.helidon.telemetry.logging.log4j2.Log4jLogBridgeProvider;
}
