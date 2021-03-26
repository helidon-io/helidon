/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
 * Microprofile fault tolerance implementation.
 */
module io.helidon.microprofile.faulttolerance {
    requires java.logging;
    requires java.annotation;
    requires jakarta.inject.api;
    requires jakarta.interceptor.api;

    requires io.helidon.common.context;
    requires io.helidon.common.configurable;
    requires io.helidon.faulttolerance;
    requires io.helidon.microprofile.config;
    requires io.helidon.microprofile.server;
    requires io.helidon.microprofile.metrics;

    requires jakarta.enterprise.cdi.api;

    requires microprofile.config.api;
    requires microprofile.metrics.api;
    requires microprofile.fault.tolerance.api;

    requires jersey.weld2.se;

    exports io.helidon.microprofile.faulttolerance;

    // needed when running with modules - to make private methods accessible
    opens io.helidon.microprofile.faulttolerance to weld.core.impl, io.helidon.microprofile.cdi;

    provides javax.enterprise.inject.spi.Extension with io.helidon.microprofile.faulttolerance.FaultToleranceExtension;
}
