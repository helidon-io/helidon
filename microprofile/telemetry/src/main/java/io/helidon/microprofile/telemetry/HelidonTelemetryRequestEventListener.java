/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.microprofile.telemetry;

import java.util.Objects;

import io.helidon.tracing.Scope;

import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

/**
 * Maintains response-write-inclusive server spans across Jersey request-processing phases. FINISHED makes a span eligible
 * to end; if an asynchronous resource method is still executing, the span ends when that method returns.
 */
final class HelidonTelemetryRequestEventListener implements ApplicationEventListener {

    @Override
    public void onEvent(ApplicationEvent event) {
        Objects.requireNonNull(event);
    }

    @Override
    public RequestEventListener onRequest(RequestEvent requestEvent) {
        Objects.requireNonNull(requestEvent);
        return new RequestListener();
    }

    private static void close(Scope scope) {
        if (scope != null) {
            scope.close();
        }
    }

    private static final class RequestListener implements RequestEventListener {
        private volatile ServerSpanLifecycle lifecycle;
        private Scope resourceScope;
        private Scope exceptionMapperScope;
        private volatile boolean initialized;

        @Override
        public void onEvent(RequestEvent event) {
            initialize(event);
            ServerSpanLifecycle currentLifecycle = lifecycle;
            if (currentLifecycle == null) {
                return;
            }

            switch (event.getType()) {
            case REQUEST_FILTERED -> currentLifecycle.closeRequestScopeIfCurrent();
            case RESOURCE_METHOD_START -> {
                if (currentLifecycle.resourceMethodStarted()) {
                    resourceScope = currentLifecycle.activate();
                }
            }
            case RESOURCE_METHOD_FINISHED -> {
                close(resourceScope);
                resourceScope = null;
                releaseIfFinished(currentLifecycle, currentLifecycle.resourceMethodFinished());
            }
            case RESP_FILTERS_START -> currentLifecycle.responseProcessingStarted();
            case EXCEPTION_MAPPER_FOUND -> {
                exceptionMapperScope = currentLifecycle.activate();
            }
            case EXCEPTION_MAPPING_FINISHED -> {
                close(exceptionMapperScope);
                exceptionMapperScope = null;
            }
            case ON_EXCEPTION -> currentLifecycle.responseFailure(event.getException());
            case FINISHED -> {
                currentLifecycle.closeRequestScopeIfCurrent();
                if (event.getContainerResponse() != null) {
                    currentLifecycle.responseStatus(event.getContainerResponse().getStatus());
                }
                releaseIfFinished(currentLifecycle, currentLifecycle.requestFinished(event.isResponseWritten()));
            }
            case START, MATCHING_START, LOCATOR_MATCHED, SUBRESOURCE_LOCATED, REQUEST_MATCHED, RESP_FILTERS_FINISHED -> {
            }
            default -> {
            }
            }
        }

        private void releaseIfFinished(ServerSpanLifecycle currentLifecycle, boolean finished) {
            if (finished && lifecycle == currentLifecycle) {
                lifecycle = null;
            }
        }

        private static boolean initializes(RequestEvent.Type type) {
            return switch (type) {
            case REQUEST_FILTERED, RESOURCE_METHOD_START, RESOURCE_METHOD_FINISHED, RESP_FILTERS_START,
                    RESP_FILTERS_FINISHED, ON_EXCEPTION, EXCEPTION_MAPPER_FOUND, EXCEPTION_MAPPING_FINISHED, FINISHED -> true;
            case START, MATCHING_START, LOCATOR_MATCHED, SUBRESOURCE_LOCATED, REQUEST_MATCHED -> false;
            };
        }

        private void initialize(RequestEvent event) {
            if (initialized || !initializes(event.getType())) {
                return;
            }
            lifecycle = (ServerSpanLifecycle) event.getContainerRequest().getProperty(ServerSpanLifecycle.PROPERTY);
            initialized = true;
        }
    }
}
