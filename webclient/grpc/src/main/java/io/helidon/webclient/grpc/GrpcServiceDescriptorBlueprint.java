/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.webclient.grpc;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import io.grpc.CallCredentials;
import io.grpc.ClientInterceptor;

@Prototype.Blueprint
interface GrpcServiceDescriptorBlueprint {

    String serviceName();

    @Option.Singular
    Map<String, GrpcClientMethodDescriptor> methods();

    default GrpcClientMethodDescriptor method(String name) {
        GrpcClientMethodDescriptor descriptor = methods().get(name);
        if (descriptor == null) {
            throw new NoSuchElementException("There is no method " + name + " defined for service " + this);
        }
        return descriptor;
    }

    @Option.Singular
    List<ClientInterceptor> interceptors();

    Optional<CallCredentials> callCredentials();
}
