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
 * Optional Logback log capture for OpenTelemetry.
 */
@SuppressWarnings("requires-automatic")
module io.helidon.telemetry.logging.logback {
    requires io.helidon.common;
    requires transitive io.helidon.telemetry.logging;
    requires io.helidon.logging.slf4j;
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;
    requires io.opentelemetry.instrumentation.logback_appender_1_0;

    exports io.helidon.telemetry.logging.logback;

    provides io.helidon.telemetry.logging.spi.LogBridgeProvider
            with io.helidon.telemetry.logging.logback.LogbackLogBridgeProvider;
}
