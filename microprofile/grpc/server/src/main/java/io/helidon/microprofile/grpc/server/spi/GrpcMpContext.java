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

package io.helidon.microprofile.grpc.server.spi;

import java.util.concurrent.CompletionStage;

import javax.enterprise.inject.spi.BeanManager;

import io.helidon.config.Config;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;

/**
 * A context to allow a microprofile gRPC server extensions to configure additional
 * services or components for the gRPC server or use the CDI bean manager.
 */
public interface GrpcMpContext {

    /**
     * Obtain the Helidon configuration.
     *
     * @return  the Helidon configuration
     */
    Config config();

    /**
     * Obtain the {@link GrpcServerConfiguration}.
     *
     * @return the {@link GrpcServerConfiguration}
     */
    GrpcServerConfiguration.Builder grpcServerConfiguration();

    /**
     * Obtain the {@link GrpcRouting.Builder} to allow modifications
     * to be made to the routing before the server is configured.
     *
     * @return the {@link GrpcRouting.Builder}
     */
    GrpcRouting.Builder routing();

    /**
     * Obtain the {@link javax.enterprise.inject.spi.BeanManager}.
     *
     * @return the {@link javax.enterprise.inject.spi.BeanManager}
     */
    BeanManager beanManager();

    /**
     * Return a completion stage is completed when the gRPC server is started.
     *
     * @return a completion stage is completed when the gRPC server is started
     */
    CompletionStage<GrpcServer> whenStarted();

    /**
     * Return a completion stage is completed when the gRPC server is shut down.
     *
     * @return a completion stage is completed when the gRPC server is shut down
     */
    CompletionStage<GrpcServer> whenShutdown();
}
