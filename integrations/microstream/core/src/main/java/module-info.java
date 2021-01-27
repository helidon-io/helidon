/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 * Provides support for Microstream core features integration.
 */
module io.helidon.integrations.microstream {
    exports io.helidon.integrations.microstream.core;

    requires transitive io.helidon.config;
    requires transitive microstream.afs;
    requires transitive microstream.base;
    requires transitive microstream.configuration;
    requires transitive microstream.persistence;
    requires transitive microstream.storage;
    requires transitive microstream.storage.embedded;
    requires transitive microstream.storage.embedded.configuration;

}