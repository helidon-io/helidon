/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

/**
 * Integration with Micronaut Data.
 */
@Features.Preview
@Features.Name("Micronaut Data")
@Features.Description("Micronaut Data integration")
@Features.Flavor(HelidonFlavor.MP)
@Features.Path({"CDI", "Micronaut", "Data"})
@SuppressWarnings({ "requires-automatic"})
module io.helidon.integrations.micronaut.data {

    requires io.micronaut.inject;
    requires jakarta.annotation;
    requires java.sql;

    requires static io.helidon.common.features.api;

    requires transitive jakarta.cdi;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.integrations.micronaut.cdi.data.MicronautDataCdiExtension;

    exports io.helidon.integrations.micronaut.cdi.data;

}
