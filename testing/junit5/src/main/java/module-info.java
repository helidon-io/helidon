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

/**
 * See {@link io.helidon.testing.junit5}.
 */
module io.helidon.testing.junit5 {
    requires org.junit.jupiter.api;
    requires io.helidon.service.registry;
    requires io.helidon.logging.common;
    requires transitive io.helidon.testing;
    requires transitive io.helidon.common.context;

    exports io.helidon.testing.junit5;
}