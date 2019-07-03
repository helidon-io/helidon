/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ExecutorService;

import javax.ws.rs.client.ClientBuilder;

import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.context.Contexts;
import io.helidon.microprofile.config.MpConfig;
import io.helidon.tracing.jersey.client.ClientTracingFilter;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.opentracing.ClientTracingRegistrarProvider;

/**
 * Microprofile client tracing registrar.
 */
public class MpTracingClientRegistrar implements ClientTracingRegistrarProvider {
    private static final ClientTracingFilter FILTER = new ClientTracingFilter();
    static final ThreadPoolSupplier EXECUTOR_SERVICE;

    static {
        MpConfig config = (MpConfig) ConfigProvider.getConfig();
        EXECUTOR_SERVICE = ThreadPoolSupplier.create(config.helidonConfig().get("tracing.executor-service"));
    }

    @Override
    public ClientBuilder configure(ClientBuilder clientBuilder) {
        return configure(clientBuilder, EXECUTOR_SERVICE.get());
    }

    @Override
    public ClientBuilder configure(ClientBuilder clientBuilder, ExecutorService executorService) {
        clientBuilder.register(FILTER);
        clientBuilder.executorService(Contexts.wrap(executorService));
        return clientBuilder;
    }
}
