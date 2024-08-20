/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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
 *
 * @see org.eclipse.microprofile.faulttolerance
 */
module io.helidon.microprofile.faulttolerance {
    requires java.logging;
    requires jakarta.annotation;
    requires jakarta.inject;
    requires jakarta.interceptor;

    requires io.helidon.common.context;
    requires io.helidon.common.configurable;
    requires io.helidon.faulttolerance;
    requires io.helidon.microprofile.config;
    requires io.helidon.microprofile.server;
    requires io.helidon.microprofile.metrics;
    requires io.helidon.config.mp;

    requires jakarta.cdi;

    requires microprofile.config.api;
    requires microprofile.metrics.api;
    requires microprofile.fault.tolerance.api;

    requires jersey.weld2.se;
    requires weld.api;
    requires weld.spi;

    exports io.helidon.microprofile.faulttolerance;

    // needed when running with modules - to make private methods accessible
    opens io.helidon.microprofile.faulttolerance to weld.core.impl, io.helidon.microprofile.cdi;

    provides jakarta.enterprise.inject.spi.Extension with io.helidon.microprofile.faulttolerance.FaultToleranceExtension;
}
