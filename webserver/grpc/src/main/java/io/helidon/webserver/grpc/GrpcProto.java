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
package io.helidon.webserver.grpc;

import com.google.protobuf.Descriptors;

/**
 * gRPC service types that implement this interface can return a proto
 * file descriptor.
 *
 * @see io.helidon.webserver.grpc.GrpcService
 */
public interface GrpcProto {

    /**
     * Proto descriptor of this service.
     *
     * @return proto file descriptor
     */
    Descriptors.FileDescriptor proto();
}
