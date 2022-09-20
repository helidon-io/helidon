/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
 * Helidon data repository API.
 *
 * @see io.helidon.data
 */
module io.helidon.data {
    requires java.logging;
    requires java.sql;
    requires transitive io.helidon.common.reactive;
    requires com.fasterxml.jackson.annotation;

    exports io.helidon.data;
    exports io.helidon.data.annotation;
    exports io.helidon.data.annotation.event;
    exports io.helidon.data.event;
    exports io.helidon.data.event.listeners;
    exports io.helidon.data.model;
    exports io.helidon.data.repository;
}
