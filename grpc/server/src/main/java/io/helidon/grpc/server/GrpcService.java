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

package io.helidon.grpc.server;

/**
 * A Helidon gRPC service.
 */
public interface GrpcService {

    /**
     * Update service configuration.
     *
     * @param rules configuration to update
     */
    void update(ServiceDescriptor.Rules rules);

    /**
     * Obtain the name of this service.
     * <p>
     * The default implementation returns the implementation class's {@link Class#getSimpleName()}.
     *
     * @return  the name of this service
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
