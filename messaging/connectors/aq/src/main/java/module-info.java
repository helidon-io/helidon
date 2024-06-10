/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
 * MicroProfile Reactive Messaging Oracle AQ connector.
 */
@Feature(value = "Oracle AQ Connector",
        description = "Reactive messaging connector for Oracle AQ",
        in = {HelidonFlavor.SE, HelidonFlavor.MP},
        path = {"Messaging", "OracleAQ"}
)
@Aot(false)
module io.helidon.messaging.connectors.aq {

    requires aqapi;
    requires io.helidon.common.configurable;
    requires io.helidon.messaging.jms.shim;
    requires javax.jms.api;
    requires jakarta.messaging;

    requires static io.helidon.common.features.api;
    requires static io.helidon.config.mp;
    requires static jakarta.cdi;
    requires static jakarta.inject;

    requires transitive io.helidon.messaging.connectors.jms;
    requires transitive io.helidon.messaging;
    requires transitive java.sql;

    exports io.helidon.messaging.connectors.aq;

}
