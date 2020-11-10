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
 * Helidon Log4j MDC module.
 */
module io.helidon.logging.log4j {
    requires io.helidon.common.context;
    requires org.apache.logging.log4j;
    requires io.helidon.logging.common;

    exports io.helidon.logging.log4j;

    provides io.helidon.logging.common.spi.MdcProvider with io.helidon.logging.log4j.Log4jMdcProvider;
    provides io.helidon.common.context.spi.DataPropagationProvider with io.helidon.logging.log4j.Log4jMdcPropagator;
}