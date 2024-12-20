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
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Provides classes and interfaces that integrate <a
 * href="https://jcp.org/en/jsr/detail?id=907">JTA</a> version 1.2
 * into <a
 * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html">CDI</a>
 * version 2.0 using <a href="http://narayana.io/">Narayana</a> as the
 * underlying implementation.
 */
@Aot(description = "Experimental support, tested on limited use cases")
@Feature(value = "JTA",
        description = "Jakarta transaction API support for Helidon MP",
        in = HelidonFlavor.MP,
        path = "JTA"
)
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.integrations.jta.cdi {

    requires jakarta.annotation;
    requires transitive jakarta.cdi;
    requires jakarta.inject;
    requires transitive jakarta.transaction;
    requires java.rmi;
    requires java.sql;
    requires narayana.jta;
    requires io.helidon.common.resumable;

    requires static io.helidon.common.features.api;

    exports io.helidon.integrations.jta.cdi;
    exports io.helidon.integrations.jta.narayana to narayana.jta;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.integrations.jta.cdi.NarayanaExtension;

}
