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

package io.helidon.webserver.grpc;

import io.grpc.Context;

/**
 * A collection of gRPC {@link Context.Key} instances available for use in Helidon WebServer gRPC request handlers.
 * Other keys which are not specific to server handlers can be found in the {@link io.helidon.grpc.core.ContextKeys}
 * class.
 */
public final class ServerContextKeys {
    /**
     * The gRPC context key to use to obtain the Helidon {@link GrpcConnectionContext}
     * from the gRPC {@link Context}.
     */
    public static final Context.Key<GrpcConnectionContext> CONNECTION_CONTEXT =
        Context.key(GrpcConnectionContext.class.getCanonicalName());

    private ServerContextKeys() {
        // Utility class
    }
}
