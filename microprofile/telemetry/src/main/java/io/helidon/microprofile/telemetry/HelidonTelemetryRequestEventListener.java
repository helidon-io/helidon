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

import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

/**
 * Maintains response-write-inclusive server spans across Jersey request-processing phases and ends them at FINISHED.
 */
final class HelidonTelemetryRequestEventListener implements ApplicationEventListener {

    @Override
    public void onEvent(ApplicationEvent event) {
    }

    @Override
    public RequestEventListener onRequest(RequestEvent requestEvent) {
        return HelidonTelemetryRequestEventListener::onRequestEvent;
    }

    private static void onRequestEvent(RequestEvent event) {
        ContainerRequest request = event.getContainerRequest();
        ServerSpanLifecycle lifecycle = (ServerSpanLifecycle) request.getProperty(ServerSpanLifecycle.PROPERTY);
        if (lifecycle == null) {
            return;
        }

        Span span = (Span) request.getProperty(HelidonTelemetryContainerFilter.SPAN);
        Scope scope = (Scope) request.getProperty(HelidonTelemetryContainerFilter.SPAN_SCOPE);
        switch (event.getType()) {
        case RESOURCE_METHOD_START:
            lifecycle.resourceMethodStarted();
            break;
        case RESOURCE_METHOD_FINISHED:
            if (span != null && scope != null) {
                try {
                    lifecycle.resourceMethodFinished(span, scope);
                } finally {
                    cleanupIfFinished(request, lifecycle);
                }
            }
            break;
        case RESP_FILTERS_START:
            if (span != null) {
                lifecycle.responseProcessingStarted(span);
            }
            break;
        case ON_EXCEPTION:
            lifecycle.responseFailure(event.getException());
            break;
        case FINISHED:
            if (span != null && scope != null) {
                try {
                    lifecycle.requestFinished(span, scope, event.isResponseWritten());
                } finally {
                    cleanupIfFinished(request, lifecycle);
                }
            }
            break;
        default:
            break;
        }
    }

    static void cleanupIfFinished(ContainerRequest request, ServerSpanLifecycle lifecycle) {
        if (lifecycle.isFinished()) {
            request.removeProperty(HelidonTelemetryContainerFilter.SPAN);
            request.removeProperty(HelidonTelemetryContainerFilter.SPAN_SCOPE);
            request.removeProperty(ServerSpanLifecycle.PROPERTY);
        }
    }
}
