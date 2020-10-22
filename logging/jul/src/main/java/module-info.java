/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 * Helidon Java Util Logging MDC support module.
 */
module io.helidon.logging.jul {
    requires java.logging;

    requires io.helidon.common;
    requires io.helidon.logging;
    requires io.helidon.common.context;

    exports  io.helidon.logging.jul;

    provides io.helidon.common.context.spi.DataPropagationProvider with io.helidon.logging.jul.JulMdcPropagator;
    provides io.helidon.logging.spi.MdcProvider with io.helidon.logging.jul.JulMdcProvider;
}