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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;

import io.opentelemetry.context.Context;

import static io.helidon.microprofile.telemetry.HelidonTelemetryConstants.HTTP_STATUS_CODE;

final class ServerSpanLifecycle {
    static final String PROPERTY = ServerSpanLifecycle.class.getName();

    private static final int NO_RESPONSE_STATUS = -1;
    private static final int REQUEST_SCOPE_CLOSED = 1;
    private static final int RESOURCE_METHOD_STARTED = 1 << 1;
    private static final int RESOURCE_METHOD_FINISHED = 1 << 2;
    private static final int RESPONSE_PROCESSING_STARTED = 1 << 3;
    private static final int REQUEST_FINISHED = 1 << 4;
    private static final int RESPONSE_WRITTEN = 1 << 5;
    private static final int SPAN_ENDED = 1 << 6;
    private static final VarHandle STATE;
    private static final VarHandle FAILURE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            STATE = lookup.findVarHandle(ServerSpanLifecycle.class, "state", int.class);
            FAILURE = lookup.findVarHandle(ServerSpanLifecycle.class, "failure", Throwable.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Span span;
    private final Scope requestScope;
    private final Thread requestScopeOwner;
    private final Context requestContext;
    private volatile int state;
    private volatile Throwable failure;
    private volatile int responseStatus = NO_RESPONSE_STATUS;

    ServerSpanLifecycle(Span span, Scope requestScope) {
        this.span = span;
        this.requestScope = requestScope;
        requestScopeOwner = Thread.currentThread();
        requestContext = Context.current();
    }

    Span span() {
        return span;
    }

    Scope activate() {
        return span.activate();
    }

    void closeRequestScopeIfCurrent() {
        if (Thread.currentThread() != requestScopeOwner || Context.current() != requestContext) {
            return;
        }

        int previous = (int) STATE.getAndBitwiseOr(this, REQUEST_SCOPE_CLOSED);
        if ((previous & REQUEST_SCOPE_CLOSED) == 0) {
            requestScope.close();
        }
    }

    boolean resourceMethodStarted() {
        int previous = (int) STATE.getAndBitwiseOr(this, RESOURCE_METHOD_STARTED);
        return (previous & RESOURCE_METHOD_STARTED) == 0;
    }

    boolean resourceMethodFinished() {
        STATE.getAndBitwiseOr(this, RESOURCE_METHOD_FINISHED);
        return tryEnd();
    }

    void responseProcessingStarted() {
        STATE.getAndBitwiseOr(this, RESPONSE_PROCESSING_STARTED);
    }

    void responseFailure(Throwable throwable) {
        if (((int) STATE.getVolatile(this) & RESPONSE_PROCESSING_STARTED) != 0 && throwable != null) {
            FAILURE.compareAndSet(this, null, throwable);
        }
    }

    void writerFailure(Throwable throwable) {
        if (throwable != null) {
            FAILURE.compareAndSet(this, null, throwable);
        }
    }

    void responseStatus(int status) {
        responseStatus = status;
    }

    boolean requestFinished(boolean responseWritten) {
        int completion = REQUEST_FINISHED | (responseWritten ? RESPONSE_WRITTEN : 0);
        while (true) {
            int current = (int) STATE.getVolatile(this);
            if ((current & REQUEST_FINISHED) != 0 || STATE.compareAndSet(this, current, current | completion)) {
                break;
            }
        }
        return tryEnd();
    }

    private boolean tryEnd() {
        while (true) {
            int current = (int) STATE.getVolatile(this);
            if ((current & SPAN_ENDED) != 0) {
                return true;
            }
            if ((current & REQUEST_FINISHED) == 0
                    || ((current & RESOURCE_METHOD_STARTED) != 0 && (current & RESOURCE_METHOD_FINISHED) == 0)) {
                return false;
            }
            if (STATE.compareAndSet(this, current, current | SPAN_ENDED)) {
                end(current);
                return true;
            }
        }
    }

    private void end(int completionState) {
        Throwable throwable = (Throwable) FAILURE.getVolatile(this);
        int finalResponseStatus = responseStatus;
        if (finalResponseStatus != NO_RESPONSE_STATUS) {
            span.tag(HTTP_STATUS_CODE, finalResponseStatus);
        }
        if ((completionState & RESPONSE_WRITTEN) == 0
                || throwable != null
                || (finalResponseStatus >= 500 && finalResponseStatus < 600)) {
            span.status(Span.Status.ERROR);
        }
        if (throwable == null) {
            span.end();
        } else {
            span.end(throwable);
        }
    }
}
