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

/**
 * Integrating with OCI Metrics.
 */
module io.helidon.integrations.oci.metrics {
    requires java.logging;

    requires transitive io.helidon.config;
    requires transitive io.helidon.common;
    requires io.helidon.common.http;
    requires io.helidon.webserver;
    requires static io.helidon.config.metadata;
    requires io.helidon.metrics.api;

    requires oci.java.sdk.monitoring;
    requires oci.java.sdk.common;

    requires transitive microprofile.metrics.api;

    exports io.helidon.integrations.oci.metrics;
 }
