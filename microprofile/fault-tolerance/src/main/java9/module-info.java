/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
    requires javax.inject;
    requires javax.interceptor.api;

    requires io.helidon.common.context;
    requires io.helidon.common.configurable;
    requires io.helidon.microprofile.config;
    requires io.helidon.microprofile.server;

    requires cdi.api;
    requires hystrix.core;
    requires archaius.core;
    requires commons.configuration;
    requires failsafe;

    requires microprofile.config.api;
    requires microprofile.metrics.api;
    requires microprofile.fault.tolerance.api;

    provides io.helidon.microprofile.server.spi.MpService with io.helidon.microprofile.faulttolerance.FaultToleranceMpService;
    provides javax.enterprise.inject.spi.Extension with io.helidon.microprofile.faulttolerance.FaultToleranceExtension;
}
