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
 * Helidon Data Common Tests.
 */
module io.helidon.data.tests.common {

    requires org.junit.jupiter.api;
    requires hamcrest.all;

    requires io.helidon.transaction;
    requires io.helidon.data;
    requires io.helidon.data.tests.model;
    requires io.helidon.data.tests.repository;
    requires io.helidon.data.tests.application;
    requires io.helidon.data.jakarta.persistence;
    requires io.helidon.service.registry;

    exports io.helidon.data.tests.common;

}
