/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

/**
 * Integration with Micronaut Data.
 */
@Preview
@Feature(value = "Micronaut Data",
        description = "Micronaut Data integration",
        in = HelidonFlavor.MP,
        path = {"CDI", "Micronaut", "Data"}
)
module io.helidon.integrations.micronaut.data {
    requires static io.helidon.common.features.api;

    requires jakarta.annotation;
    requires java.sql;

    requires jakarta.cdi;
    requires jakarta.interceptor.api;

    requires io.micronaut.inject;

    provides jakarta.enterprise.inject.spi.Extension with io.helidon.integrations.micronaut.cdi.data.MicronautDataCdiExtension;

    exports io.helidon.integrations.micronaut.cdi.data;
}
