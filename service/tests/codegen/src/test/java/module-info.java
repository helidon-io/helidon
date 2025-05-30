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

module io.helidon.service.tests.codegen.test {
    exports io.helidon.service.tests.codegen;

    requires io.helidon.service.registry;
    requires io.helidon.service.codegen;
    requires io.helidon.config.metadata;

    requires hamcrest.all;
    requires org.junit.jupiter.api;
    requires io.helidon.service.metadata;

    opens io.helidon.service.tests.codegen to org.junit.platform.commons;
}