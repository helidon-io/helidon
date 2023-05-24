/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.example.basic.implicit;

import io.helidon.config.Config;
import io.helidon.grpc.examples.common.EchoService;
import io.helidon.grpc.examples.common.GreetService;
import io.helidon.microprofile.grpc.server.spi.GrpcMpContext;
import io.helidon.microprofile.grpc.server.spi.GrpcMpExtension;

/**
 * An example of adding additional non-managed bean gRPC services.
 * <p>
 * This class is a {@link GrpcMpExtension} that will be called by
 * the  {@link io.helidon.microprofile.grpc.server.GrpcServerCdiExtension
 * gRPC MP Extension} prior to starting the gRPC server.
 * <p>
 * As an extension this class also needs to be specified in the
 * {@code META-INF/services/io.helidon.microprofile.grpc.server.spi.GrpcMpExtension}
 * file (or for Java 9+ modules in the {@code module-info.java} file).
 */
public class AdditionalServices
        implements GrpcMpExtension {
    /**
     * Add additional gRPC services as managed beans.
     * <p>
     * These are classes that are on the classpath but for whatever reason are not
     * annotated as managed beans (for example we do not own the source) but we want
     * them to be located and loaded by the server.
     *
     * @param context  the {@link GrpcMpContext} to use to add the extra services
     */
    @Override
    public void configure(GrpcMpContext context) {
        context.routing()
               .register(new GreetService(Config.empty()))
               .register(new EchoService());
    }
}
