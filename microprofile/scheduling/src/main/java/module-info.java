/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import io.helidon.microprofile.scheduling.SchedulingCdiExtension;

/**
 * CDI Scheduling implementation.
 */
module io.helidon.microprofile.scheduling {
    requires java.logging;

    requires static jakarta.enterprise.cdi.api;
    requires static jakarta.inject.api;
    requires static jakarta.activation;
    requires jakarta.interceptor.api;
    requires io.helidon.config;
    requires io.helidon.config.mp;
    //requires com.cronutils.builder;
    requires io.helidon.microprofile.config;
    requires io.helidon.microprofile.server;
    requires cron.utils;

    exports io.helidon.microprofile.scheduling;
    opens io.helidon.microprofile.scheduling;

    provides javax.enterprise.inject.spi.Extension with SchedulingCdiExtension;
}
