/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.grpc.examples.security.abac;

import java.time.DayOfWeek;
import java.time.LocalTime;

import io.helidon.common.LogConfig;
import io.helidon.grpc.examples.common.StringService;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.security.Security;
import io.helidon.security.SubjectType;
import io.helidon.security.abac.policy.PolicyValidator;
import io.helidon.security.abac.scope.ScopeValidator;
import io.helidon.security.abac.time.TimeValidator;
import io.helidon.security.integration.grpc.GrpcSecurity;
import io.helidon.security.providers.abac.AbacProvider;

/**
 * An example of a secure gRPC server that uses
 * ABAC security configured in the code below.
 * <p>
 * This server configures in code the same rules that
 * the {@link AbacServerFromConfig} class uses from
 * its configuration.
 */
public class AbacServer {

    private AbacServer() {
    }

    /**
     * Main entry point.
     *
     * @param args  the program arguments
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();

        Security security = Security.builder()
                .addProvider(AtnProvider.builder().build())   // add out custom provider
                .addProvider(AbacProvider.builder().build())  // add the ABAC provider
                .build();

        // Create the time validator that will be used by the ABAC security provider
        TimeValidator.TimeConfig validTimes = TimeValidator.TimeConfig.builder()
                .addBetween(LocalTime.of(8, 15), LocalTime.of(12, 0))
                .addBetween(LocalTime.of(12, 30), LocalTime.of(17, 30))
                .addDaysOfWeek(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                .build();

        // Create the policy validator that will be used by the ABAC security provider
        PolicyValidator.PolicyConfig validPolicy = PolicyValidator.PolicyConfig.builder()
                .statement("${env.time.year >= 2017}")
                .build();

        // Create the scope validator that will be used by the ABAC security provider
        ScopeValidator.ScopesConfig validScopes = ScopeValidator.ScopesConfig.create("calendar_read", "calendar_edit");

        // Create the Atn config that will be used by out custom security provider
        AtnProvider.AtnConfig atnConfig = AtnProvider.AtnConfig.builder()
                .addAuth(AtnProvider.Auth.builder("user")
                                 .type(SubjectType.USER)
                                 .roles("user_role")
                                 .scopes("calendar_read", "calendar_edit")
                                 .build())
                .addAuth(AtnProvider.Auth.builder("service")
                                 .type(SubjectType.SERVICE)
                                 .roles("service_role")
                                 .scopes("calendar_read", "calendar_edit")
                                 .build())
                .build();

        ServiceDescriptor stringService = ServiceDescriptor.builder(new StringService())
                .intercept("Upper", GrpcSecurity.secure()
                                                            .customObject(atnConfig)
                                                            .customObject(validScopes)
                                                            .customObject(validTimes)
                                                            .customObject(validPolicy))
                .build();

        GrpcRouting grpcRouting = GrpcRouting.builder()
                .intercept(GrpcSecurity.create(security).securityDefaults(GrpcSecurity.secure()))
                .register(stringService)
                .build();

        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder().build();
        GrpcServer grpcServer = GrpcServer.create(serverConfig, grpcRouting);

        grpcServer.start()
                .thenAccept(s -> {
                        System.out.println("gRPC server is UP! http://localhost:" + s.port());
                        s.whenShutdown().thenRun(() -> System.out.println("gRPC server is DOWN. Good bye!"));
                        })
                .exceptionally(t -> {
                        System.err.println("Startup failed: " + t.getMessage());
                        t.printStackTrace(System.err);
                        return null;
                        });
    }
}
