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

import io.helidon.common.features.api.Aot;
import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Provides classes and interfaces for working with <a
 * href="https://www.eclipse.org/eclipselink/#jpa"
 * target="_parent">Eclipselink</a> in CDI.
 *
 * @see io.helidon.integrations.cdi.eclipselink.CDISEPlatform
 */
@Features.Name("EclipseLink")
@Features.Description("EclipseLink support for Helidon MP")
@Features.Flavor(HelidonFlavor.MP)
@Features.Path({"JPA", "EclipseLink"})
@Features.Aot(false)
@SuppressWarnings("deprecation")
module io.helidon.integrations.cdi.eclipselink {

    requires io.helidon.integrations.jdbc;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.transaction;
    requires java.management;
    requires java.sql;

    requires static io.helidon.common.features.api;

    requires transitive org.eclipse.persistence.core;
    requires transitive org.eclipse.persistence.jpa;

    exports io.helidon.integrations.cdi.eclipselink;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.integrations.cdi.eclipselink.CDISEPlatformExtension;

}
