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
 * Helidon Data Repository with Jakarta Persistence Runtime.
 */
module io.helidon.data.jakarta.persistence {

    requires transitive jakarta.persistence;
    requires transitive io.helidon.service.registry;

    requires io.helidon.data;
    requires io.helidon.data.sql.common;
    requires io.helidon.transaction;
    requires java.naming;

    exports io.helidon.data.jakarta.persistence;
    exports io.helidon.data.jakarta.persistence.spi;

    provides io.helidon.data.spi.ProviderConfigProvider
            with io.helidon.data.jakarta.persistence.DataJpaConfigProvider;

    // Temporary code for Jakarta Persistence 3.1 compliant initialization
    uses jakarta.persistence.spi.PersistenceProvider;
    uses io.helidon.data.jakarta.persistence.spi.JakartaPersistenceExtensionProvider;

}
