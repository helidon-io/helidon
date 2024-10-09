/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
 * Shared unit tests and support for metrics implementations.
 */
module io.helidon.metrics.provider.tests {

    requires io.helidon.common.config;
    requires io.helidon.metrics.api;
    requires io.helidon.common.testing.junit5;
    requires org.junit.jupiter.api;
    requires hamcrest.all;
    requires io.helidon.config;
    requires org.junit.jupiter.params;
    requires micrometer.core;
    requires io.helidon.testing.junit5;

    exports io.helidon.metrics.provider.tests;
}