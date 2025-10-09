/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.security;

class HttpSecurityInterceptorContext {
    private boolean shouldFinish;

    // tracing support
    private boolean traceSuccess = true;
    private String traceDescription;
    private Throwable traceThrowable;

    void clearTrace() {
        traceSuccess = true;
        traceDescription = null;
        traceThrowable = null;
    }

    boolean shouldFinish() {
        return shouldFinish;
    }

    void shouldFinish(boolean shouldFinish) {
        this.shouldFinish = shouldFinish;
    }

    boolean traceSuccess() {
        return traceSuccess;
    }

    void traceSuccess(boolean traceSuccess) {
        this.traceSuccess = traceSuccess;
    }

    String traceDescription() {
        return traceDescription;
    }

    void traceDescription(String traceDescription) {
        this.traceDescription = traceDescription;
    }

    Throwable traceThrowable() {
        return traceThrowable;
    }

    void traceThrowable(Throwable traceThrowable) {
        this.traceThrowable = traceThrowable;
    }
}
