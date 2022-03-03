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

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.client.ClientBuilder;

/**
 * Listen to the Jersey's client builder being invoked and register Helidon executor provider.
 */
// Lowest priority gets invoked first and first registered provider wins,
// user's custom ClientBuilderListeners needs to be able to override this
@Priority(Priorities.USER + 100)
public class ClientBuilderListener implements org.glassfish.jersey.client.spi.ClientBuilderListener {

    private static final String USE_CONTEXT_AWARE_EXEC_PROVIDER = "io.helidon.jersey.client.useContextAwareExecutorProvider";
    private static Boolean useProvider;

    @Override
    public void onNewBuilder(ClientBuilder builder) {
        if (useContextAwareProvider()) {
            builder.register(ExecutorProvider.class);
            builder.register(ScheduledExecutorProvider.class);
        }
    }

    private boolean useContextAwareProvider() {
        if (useProvider == null) {
            useProvider = Boolean.parseBoolean(System.getProperty(USE_CONTEXT_AWARE_EXEC_PROVIDER, "true"));
        }
        return useProvider;
    }
}
