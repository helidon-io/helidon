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

final class ServerSpanLifecycle {
    static final String PROPERTY = ServerSpanLifecycle.class.getName();

    private boolean resourceMethodStarted;
    private boolean resourceMethodFinished;
    private boolean responseProcessingStarted;
    private boolean requestFinished;
    private boolean responseWritten;
    private boolean requestScopeClosed;
    private boolean responseScopeClosed;
    private boolean spanEnded;
    private Throwable failure;
    private Scope responseScope;

    synchronized void resourceMethodStarted() {
        resourceMethodStarted = true;
    }

    void resourceMethodFinished(Span span, Scope requestScope) {
        boolean closeRequestScope;
        boolean endSpan;
        synchronized (this) {
            resourceMethodFinished = true;
            closeRequestScope = claimRequestScope();
            endSpan = claimSpanEnd();
        }
        closeAndEnd(span, requestScope, closeRequestScope, endSpan);
    }

    void responseProcessingStarted(Span span) {
        synchronized (this) {
            responseProcessingStarted = true;
            if (responseScope != null || isCurrent(span)) {
                return;
            }
            responseScope = span.activate();
        }
    }

    synchronized void responseFailure(Throwable throwable) {
        if (responseProcessingStarted && throwable != null && failure == null) {
            failure = throwable;
        }
    }

    synchronized void writerFailure(Throwable throwable) {
        if (throwable != null && failure == null) {
            failure = throwable;
        }
    }

    void requestFinished(Span span, Scope requestScope, boolean responseWritten) {
        Scope scopeToClose;
        boolean closeRequestScope;
        boolean endSpan;
        synchronized (this) {
            if (!requestFinished) {
                requestFinished = true;
                this.responseWritten = responseWritten;
            }
            scopeToClose = claimResponseScope();
            closeRequestScope = !resourceMethodStarted && claimRequestScope();
            endSpan = claimSpanEnd();
        }

        try {
            if (scopeToClose != null) {
                scopeToClose.close();
            }
        } finally {
            closeAndEnd(span, requestScope, closeRequestScope, endSpan);
        }
    }

    synchronized boolean isFinished() {
        return spanEnded && requestScopeClosed && (responseScope == null || responseScopeClosed);
    }

    private synchronized Scope claimResponseScope() {
        if (responseScopeClosed || responseScope == null) {
            return null;
        }
        responseScopeClosed = true;
        return responseScope;
    }

    private boolean claimRequestScope() {
        if (requestScopeClosed) {
            return false;
        }
        requestScopeClosed = true;
        return true;
    }

    private boolean claimSpanEnd() {
        if (spanEnded || !requestFinished || (resourceMethodStarted && !resourceMethodFinished)) {
            return false;
        }
        spanEnded = true;
        return true;
    }

    private void closeAndEnd(Span span, Scope requestScope, boolean closeRequestScope, boolean endSpan) {
        try {
            if (closeRequestScope) {
                requestScope.close();
            }
        } finally {
            if (endSpan) {
                end(span);
            }
        }
    }

    private void end(Span span) {
        Throwable throwable;
        boolean written;
        synchronized (this) {
            throwable = failure;
            written = responseWritten;
        }
        if (!written || throwable != null) {
            span.status(Span.Status.ERROR);
        }
        if (throwable == null) {
            span.end();
        } else {
            span.end(throwable);
        }
    }

    private static boolean isCurrent(Span span) {
        return Span.current()
                .map(current -> current.context().spanId().equals(span.context().spanId()))
                .orElse(false);
    }
}
