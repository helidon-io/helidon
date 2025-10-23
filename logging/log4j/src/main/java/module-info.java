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
 * Helidon Log4j MDC module.
 */
@Features.Name("Log4j")
@Features.Description("Log4j MDC support")
@Features.Flavor({HelidonFlavor.SE, HelidonFlavor.MP})
@Features.Path({"Logging", "Log4j"})
@Features.Aot(description = "Only programmatic configuration supported, does not work with Helidon loggers")
module io.helidon.logging.log4j {

    requires org.apache.logging.log4j;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.common.context;
    requires transitive io.helidon.logging.common;

    exports io.helidon.logging.log4j;

    provides io.helidon.logging.common.spi.LoggingProvider with io.helidon.logging.log4j.Log4jProvider;
    provides io.helidon.logging.common.spi.MdcProvider with io.helidon.logging.log4j.Log4jMdcProvider;
    provides io.helidon.common.context.spi.DataPropagationProvider with io.helidon.logging.log4j.Log4jMdcPropagator;

}
