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

package io.helidon.declarative.codegen.grpc.server;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.grpc.GrpcProtoDescriptors.MethodType;

record GrpcMethod(TypedElementInfo method,
                  String grpcName,
                  String uniqueName,
                  MethodType methodType,
                  Invocation invocation,
                  TypeName requestType,
                  TypeName responseType,
                  GrpcSecurityDefinition security) {
    enum Invocation {
        UNARY_RETURN,
        OBSERVER,
        SERVER_STREAMING_STREAM,
        CLIENT_STREAMING_STREAM,
        BIDI_STREAM,
        BIDI_OBSERVER
    }
}
