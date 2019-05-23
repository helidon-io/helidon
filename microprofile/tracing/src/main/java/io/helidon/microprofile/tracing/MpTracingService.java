/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.microprofile.tracing;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.microprofile.server.spi.MpService;
import io.helidon.microprofile.server.spi.MpServiceContext;
import io.helidon.tracing.TracerBuilder;

import io.opentracing.Tracer;

/**
 * Extension of microprofile to add support for tracing.
 */
public class MpTracingService implements MpService {
    @Override
    public void configure(MpServiceContext context) {
        // configure tracing from server configuration
        // "known" location is:
        Config tracingConfig = context.helidonConfig().get("tracing");

        // register as global tracer
        Tracer tracer = TracerBuilder.create(tracingConfig).buildAndRegister();

        // and configure webserver
        context.serverConfigBuilder()
                .tracer(tracer);

        context.applications()
                .forEach(app -> app.register(MpTracingFilter.class));

        // and finally register in current context - this should be the "application" context
        Contexts.context()
                .ifPresent(ctx -> ctx.register(tracer));
    }
}
