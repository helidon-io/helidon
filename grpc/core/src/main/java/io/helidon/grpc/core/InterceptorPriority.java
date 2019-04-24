/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 * An enum that sets a priority ordering that interceptors registered with
 * a gRPC service ot method will be applied.
 */
public enum InterceptorPriority {
    /**
     * Context ordered interceptors will be applied first.
     * <p>
     * Interceptors with this priority typically only perform tasks
     * such as adding state to the call {@link io.grpc.Context}.
     */
    Context,

    /**
     * Security ordered interceptors will be applied before other non-context
     * interceptors.
     */
    Security,

    /**
     * Interceptors with this priority will be applied first.
     */
    First,

    /**
     * Interceptors with this priority will be applied after
     * first priority interceptors and before last priority.
     */
    Normal,

    /**
     * Interceptors with this priority will be applied last.
     */
    Last;
}
