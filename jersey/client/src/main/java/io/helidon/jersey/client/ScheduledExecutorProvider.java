/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.jersey.client;

import java.util.concurrent.ScheduledExecutorService;

import io.helidon.common.context.Contexts;

import org.glassfish.jersey.client.ClientBackgroundScheduler;
import org.glassfish.jersey.spi.ScheduledThreadPoolExecutorProvider;

/**
 * Wraps default executor to enable Helidon context propagation for Jersey async calls.
 */
@ClientBackgroundScheduler
public class ScheduledExecutorProvider extends ScheduledThreadPoolExecutorProvider {

    /**
     * Creates a new instance.
     */
    public ScheduledExecutorProvider() {
        super("helidon-client-background-scheduler");
    }

    @Override
    protected int getCorePoolSize() {
        return 1;
    }

    @Override
    public ScheduledExecutorService getExecutorService() {
        return Contexts.wrap(super.getExecutorService());
    }
}
