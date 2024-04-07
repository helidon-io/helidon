/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
 * Tests applicable to all tracing providers.
 */
module io.helidon.tracing.provider.tests {

    requires io.helidon.tracing;
    requires io.helidon.common.testing.junit5;

    requires org.junit.jupiter.api;
    requires hamcrest.all;

    provides io.helidon.tracing.SpanLifeCycleListener with io.helidon.tracing.providers.tests.AutoLoadedSpanLifeCycleListener;
}