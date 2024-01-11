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

/**
 * Test Helidon inject annotations and types.
 */
module io.helidon.inject.tests.helidon.inject {
    requires io.helidon.inject.service;
    requires io.helidon.common;
    requires io.helidon.inject;

    exports io.helidon.inject.tests.helidon.inject;

    provides io.helidon.inject.service.ModuleComponent
            with io.helidon.inject.tests.helidon.inject.Injection__Module;
}