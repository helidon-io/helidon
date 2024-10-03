/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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
 * Provides CDI support for Microstream integration.
 *
 * @provides jakarta.enterprise.inject.spi.Extension
 */
module io.helidon.integrations.microstream.cdi {
    exports io.helidon.integrations.microstream.cdi;

    requires transitive cache.api;
    requires io.helidon.integrations.microstream;
    requires io.helidon.integrations.microstream.cache;
    requires transitive jakarta.cdi;
    requires transitive jakarta.inject;
    requires jakarta.interceptor.api;
    requires jakarta.annotation;
    requires microstream.cache;
    requires microstream.storage.embedded;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.integrations.microstream.cdi.EmbeddedStorageManagerExtension,
                    io.helidon.integrations.microstream.cdi.CacheExtension;
}