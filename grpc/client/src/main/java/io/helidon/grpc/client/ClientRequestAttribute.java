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
package io.helidon.grpc.client;

/**
 * An enum of possible gRPC client call attributes to attach to
 * call tracing spans.
 */
public enum ClientRequestAttribute {
    /**
     * Add the method type to the tracing span.
     */
    METHOD_TYPE,

    /**
     * Add the method name to the tracing span.
     */
    METHOD_NAME,

    /**
     * Add the call deadline to the tracing span.
     */
    DEADLINE,

    /**
     * Add the compressor type to the tracing span.
     */
    COMPRESSOR,

    /**
     * Add the security authority to the tracing span.
     */
    AUTHORITY,

    /**
     * Add the method call options to the tracing span.
     */
    ALL_CALL_OPTIONS,

    /**
     * Add the method call headers to the tracing span.
     */
    HEADERS
}
