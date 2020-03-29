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

/**
 * Integration library for {@link io.helidon.grpc.server.GrpcServer}.
 * <p>
 * The main security methods are duplicate - first as static methods on {@link io.helidon.security.integration.grpc.GrpcSecurity} and
 * then as instance methods on {@link io.helidon.security.integration.grpc.GrpcSecurityHandler} that is returned by the static methods
 * above. This is to provide a single starting point for security integration ({@link io.helidon.security.integration.grpc.GrpcSecurity})
 * and fluent API to build the "gate" to each gRPC service that is protected.
 *
 * @see io.helidon.security.integration.grpc.GrpcSecurity#create(io.helidon.security.Security)
 * @see io.helidon.security.integration.grpc.GrpcSecurity#create(io.helidon.config.Config)
 * @see io.helidon.security.integration.grpc.GrpcSecurity#create(io.helidon.security.Security, io.helidon.config.Config)
 */
package io.helidon.security.integration.grpc;
