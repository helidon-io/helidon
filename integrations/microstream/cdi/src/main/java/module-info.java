/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Aot;
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

/**
 * Provides CDI support for Microstream integration.
 *
 * @provides jakarta.enterprise.inject.spi.Extension
 * @deprecated Microstream is renamed to Eclipse store and no longer updated
 */
@Deprecated(forRemoval = true, since = "4.2.1")
@Feature(value = "Microstream",
        description = "Microstream Integration",
        in = HelidonFlavor.MP,
        path = "Microstream"
)
@Aot(false)
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.integrations.microstream.cdi {

    requires io.helidon.integrations.microstream.cache;
    requires io.helidon.integrations.microstream;
    requires jakarta.annotation;
    //requires microstream.base;
    requires microstream.cache;
    //requires microstream.persistence;
    requires microstream.storage.embedded;
    //requires microstream.storage;

    requires static io.helidon.common.features.api;

    requires transitive cache.api;
    requires transitive jakarta.cdi;
    requires transitive jakarta.inject;

    exports io.helidon.integrations.microstream.cdi;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.integrations.microstream.cdi.EmbeddedStorageManagerExtension,
                    io.helidon.integrations.microstream.cdi.CacheExtension;
	
}
