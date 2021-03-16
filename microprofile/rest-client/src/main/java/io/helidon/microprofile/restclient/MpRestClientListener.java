/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.microprofile.restclient;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.context.Contexts;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.spi.RestClientListener;

/**
 * A client listener that wraps executor service with
 * {@link io.helidon.common.context.Contexts#wrap(java.util.concurrent.ExecutorService)}.
 */
public class MpRestClientListener implements RestClientListener {
    private static final Logger LOGGER = Logger.getLogger(MpRestClientListener.class.getName());

    @SuppressWarnings("unchecked")
    @Override
    public void onNewClient(Class<?> aClass, RestClientBuilder restClientBuilder) {
        // we know there is an executor service we need to wrap, so let us do it
        // the class RestClientBuilderImpl has a field
        // private Supplier<ExecutorService> executorService;
        // we replace the value with a wrapper
        try {
            Field execServiceField = restClientBuilder.getClass().getDeclaredField("executorService");
            execServiceField.setAccessible(true);
            Supplier<ExecutorService> existingSupplier = (Supplier<ExecutorService>) execServiceField.get(restClientBuilder);
            Supplier<ExecutorService> newSupplier = () -> Contexts.wrap(existingSupplier.get());
            execServiceField.set(restClientBuilder, newSupplier);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to replace executor service for a REST Client: " + aClass, e);
        }

        restClientBuilder.register(new HelidonInboundHeaderProvider());
    }
}
