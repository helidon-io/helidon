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

final class GrpcServerTypes {
    static final TypeName GRPC_SERVICE = TypeName.create("io.helidon.grpc.api.Grpc.GrpcService");
    static final TypeName GRPC_METHOD = TypeName.create("io.helidon.grpc.api.Grpc.GrpcMethod");
    static final TypeName GRPC_PROTO = TypeName.create("io.helidon.grpc.api.Grpc.Proto");
    static final TypeName GRPC_PROTO_DESCRIPTOR = TypeName.create("io.helidon.grpc.api.Grpc.ProtoDescriptor");
    static final TypeName RPC_SERVER_ENDPOINT = TypeName.create("io.helidon.webserver.grpc.RpcServer.Endpoint");
    static final TypeName RPC_SERVER_LISTENER = TypeName.create("io.helidon.webserver.grpc.RpcServer.Listener");
    static final TypeName SERVICE_PER_REQUEST = TypeName.create("io.helidon.service.registry.Service.PerRequest");
    static final TypeName GRPC_ENTRY_POINTS = TypeName.create("io.helidon.webserver.grpc.GrpcEntryPoint.EntryPoints");
    static final TypeName GRPC_ROUTE_REGISTRATION = TypeName.create("io.helidon.webserver.grpc.GrpcRouteRegistration");
    static final TypeName GRPC_SECURITY = TypeName.create("io.helidon.webserver.grpc.security.GrpcSecurity");
    static final TypeName GRPC_SERVICE_DESCRIPTOR = TypeName.create("io.helidon.webserver.grpc.GrpcServiceDescriptor");
    static final TypeName GRPC_STREAMS = TypeName.create("io.helidon.webserver.grpc.GrpcStreams");
    static final TypeName SECURITY_LEVEL = TypeName.create("io.helidon.security.SecurityLevel");
    static final TypeName AUDIT_SEVERITY = TypeName.create("io.helidon.security.AuditEvent.AuditSeverity");
    static final TypeName SECURITY_AUTHENTICATED = TypeName.create("io.helidon.security.annotations.Authenticated");
    static final TypeName SECURITY_AUTHORIZED = TypeName.create("io.helidon.security.annotations.Authorized");
    static final TypeName SECURITY_AUDITED = TypeName.create("io.helidon.security.annotations.Audited");
    static final TypeName SECURITY_DENY_ALL = TypeName.create("jakarta.annotation.security.DenyAll");
    static final TypeName SECURITY_PERMIT_ALL = TypeName.create("jakarta.annotation.security.PermitAll");
    static final TypeName SECURITY_ROLE_PERMIT_ALL =
            TypeName.create("io.helidon.security.abac.role.RoleValidator.PermitAll");
    static final TypeName SECURITY_ABAC_ANNOTATION =
            TypeName.create("io.helidon.security.providers.abac.AbacAnnotation");
    static final TypeName SECURITY_ROLES = TypeName.create("io.helidon.security.abac.role.RoleValidator.Roles");
    static final TypeName SECURITY_ROLES_CONTAINER =
            TypeName.create("io.helidon.security.abac.role.RoleValidator.RolesContainer");
    static final TypeName SECURITY_ROLES_ALLOWED = TypeName.create("jakarta.annotation.security.RolesAllowed");
    static final TypeName PROTO_FILE_DESCRIPTOR = TypeName.create("com.google.protobuf.Descriptors.FileDescriptor");
    static final TypeName PROTO_MESSAGE_DESCRIPTOR = TypeName.create("com.google.protobuf.Descriptors.Descriptor");
    static final TypeName STREAM_OBSERVER = TypeName.create("io.grpc.stub.StreamObserver");

    private GrpcServerTypes() {
    }
}
