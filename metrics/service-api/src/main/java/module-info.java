/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
 * API, SPI, and minimal implementation of metrics service.
 */
module io.helidon.metrics.serviceapi {

    requires java.logging;

    requires io.helidon.reactive.webserver;
    requires static io.helidon.config.metadata;
    requires io.helidon.reactive.servicecommon;
    requires io.helidon.metrics.api;

    exports io.helidon.metrics.serviceapi;
    exports io.helidon.metrics.serviceapi.spi;

    uses io.helidon.metrics.serviceapi.spi.MetricsSupportProvider;
}
