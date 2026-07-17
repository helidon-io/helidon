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

package io.helidon.declarative.tests.grpc;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingReply;
import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingRequest;
import io.helidon.grpc.api.Grpc;
import io.helidon.metrics.api.Metrics;
import io.helidon.security.abac.role.RoleValidator;
import io.helidon.security.abac.scope.ScopeValidator;
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.annotations.Audited;
import io.helidon.security.annotations.Authorized;
import io.helidon.service.registry.Service;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracing;
import io.helidon.webserver.grpc.RpcServer;

import com.google.protobuf.Descriptors;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

@RpcServer.Endpoint
@Grpc.GrpcService(ClientConfigGreetingClients.SERVICE_NAME)
@Service.Singleton
class GreetingEndpoint implements SecuredGreetingContract {
    @Grpc.Proto
    Descriptors.FileDescriptor proto() {
        return DeclarativeGrpcProto.getDescriptor();
    }

    @Grpc.Unary("Greet")
    @Metrics.Counted(tags = @Metrics.Tag(key = "transport", value = "grpc"))
    @Metrics.Timed(value = "grpc-greet", absoluteName = true)
    @Tracing.Traced(value = "grpc-greet-method",
                    kind = Span.Kind.SERVER,
                    tags = @Tracing.Tag(key = "transport", value = "grpc"))
    GreetingReply greet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Grpc.Unary("InterceptedGreet")
    @InterceptedGreeting
    GreetingReply interceptedGreet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Grpc.Unary("ValidatedGreet")
    GreetingReply validatedGreet(@ValidGreetingRequest GreetingRequest request) {
        return reply(request.getName());
    }

    @Grpc.Unary("SecureGreet")
    @Authenticated
    GreetingReply secureGreet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Grpc.Unary("AuthorizedGreet")
    @Authenticated
    @Authorized
    GreetingReply authorizedGreet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Grpc.Unary("AdminGreet")
    @RolesAllowed("admin")
    GreetingReply adminGreet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Grpc.Unary("PermitAllGreet")
    @PermitAll
    GreetingReply permitAllGreet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Override
    public GreetingReply denyAllGreet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Override
    public GreetingReply roleValidatorGreet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Override
    public GreetingReply scopeGreet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Grpc.Unary("AuditedGreet")
    @Audited("declarativeGrpc")
    GreetingReply auditedGreet(GreetingRequest request) {
        return reply(request.getName());
    }

    @Grpc.ServerStreaming("Split")
    Stream<GreetingReply> split(GreetingRequest request) {
        return Stream.of(request.getName().split(","))
                .map(String::strip)
                .map(this::reply);
    }

    @Grpc.ClientStreaming("Join")
    GreetingReply join(Stream<GreetingRequest> requests) {
        return reply(requests.map(GreetingRequest::getName)
                             .map(String::strip)
                             .collect(Collectors.joining(", ")));
    }

    @Grpc.Bidirectional("Chat")
    Stream<GreetingReply> chat(Stream<GreetingRequest> requests) {
        return requests.map(GreetingRequest::getName)
                .map(this::reply);
    }

    private GreetingReply reply(String name) {
        return GreetingReply.newBuilder()
                .setMessage("Hello " + name)
                .build();
    }
}

interface SecuredGreetingContract {
    @Grpc.Unary("DenyAllGreet")
    @DenyAll
    GreetingReply denyAllGreet(GreetingRequest request);

    @Grpc.Unary("RoleValidatorGreet")
    @RoleValidator.Roles("admin")
    GreetingReply roleValidatorGreet(GreetingRequest request);

    @Grpc.Unary("ScopeGreet")
    @ScopeValidator.Scope("admin")
    GreetingReply scopeGreet(GreetingRequest request);
}
