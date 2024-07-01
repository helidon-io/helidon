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

import io.helidon.common.features.api.Aot;
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;


@Feature(value = "Eclipse Store",
        description = "Eclipse Store Integration",
        in = HelidonFlavor.MP,
        path = {"EclipseStore", "CDI"}
)
@Aot(false)
@SuppressWarnings({"requires-automatic", "requires-transitive-automatic"})
module io.helidon.integrations.eclipsestore.cdi {
    requires jakarta.annotation;
    requires transitive cache.api;
    requires transitive jakarta.cdi;
    requires transitive jakarta.inject;

    requires io.helidon.integrations.eclipsestore.cache;
    exports io.helidon.integrations.eclipsestore.cdi;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.integrations.eclipsestore.cdi.EmbeddedStorageManagerExtension,
                    io.helidon.integrations.eclipsestore.cdi.CacheExtension;

}
