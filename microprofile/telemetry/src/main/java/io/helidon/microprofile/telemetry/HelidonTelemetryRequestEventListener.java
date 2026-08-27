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

import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

/**
 * Completes response-write-inclusive server spans after Jersey finishes processing a request.
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
        ServerSpanLifecycle lifecycle = (ServerSpanLifecycle) event.getContainerRequest()
                .getProperty(ServerSpanLifecycle.PROPERTY);
        if (lifecycle == null) {
            return;
        }

        switch (event.getType()) {
        case ON_EXCEPTION:
            lifecycle.failure(event.getException());
            break;
        case FINISHED:
            Span span = (Span) event.getContainerRequest().getProperty(HelidonTelemetryContainerFilter.SPAN);
            Scope scope = (Scope) event.getContainerRequest().getProperty(HelidonTelemetryContainerFilter.SPAN_SCOPE);
            if (span != null && scope != null) {
                lifecycle.complete(span, scope, event.isResponseWritten());
            }
            break;
        default:
            break;
        }
    }
}
