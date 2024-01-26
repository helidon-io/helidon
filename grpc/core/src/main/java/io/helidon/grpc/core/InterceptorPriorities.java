/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.grpc.core;

/**
 * Constants that represent a priority ordering that interceptors registered with
 * a gRPC service or method will be applied.
 */
public class InterceptorPriorities {
    /**
     * Context priority.
     * <p>
     * Interceptors with this priority typically <b>only</b> perform tasks
     * such as adding state to the call {@link io.grpc.Context}.
     */
    public static final int CONTEXT = 1000;

    /**
     * Tracing priority.
     * <p>
     * Tracing and metrics interceptors are typically applied after any context
     * interceptors so that they can trace and gather metrics on the whole call
     * stack of remaining interceptors.
     */
    public static final int TRACING = CONTEXT + 1;

    /**
     * Security authentication priority.
     */
    public static final int AUTHENTICATION = 2000;

    /**
     * Security authorization priority.
     */
    public static final int AUTHORIZATION = 2000;

    /**
     * User-level priority.
     *
     * This value is also used as a default priority for application-supplied interceptors.
     */
    public static final int USER = 5000;

    /**
     * Cannot create instances.
     */
    private InterceptorPriorities() {
    }
}
