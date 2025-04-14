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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.grpc.Status;
import io.grpc.reflection.v1.ErrorResponse;
import io.grpc.reflection.v1.FileDescriptorResponse;
import io.grpc.reflection.v1.ListServiceResponse;
import io.grpc.reflection.v1.ServerReflectionProto;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.reflection.v1.ServiceResponse;
import io.grpc.stub.StreamObserver;

/**
 * Grpc reflection service.
 */
class GrpcReflectionService implements GrpcService {
    private static final System.Logger LOGGER = System.getLogger(GrpcReflectionService.class.getName());

    /**
     * Caches FileDescriptorProto representations as byte strings to avoid serialization
     * on every reflection request.
     */
    private static final Map<String, ByteString> FILE_DESCRIPTOR_CACHE = new ConcurrentHashMap<>();

    private final String socket;

    GrpcReflectionService(String socket) {
        this.socket = socket;
    }

    @Override
    public Descriptors.FileDescriptor proto() {
        return ServerReflectionProto.getDescriptor();
    }

    @Override
    public String serviceName() {
        List<Descriptors.ServiceDescriptor> services = proto().getServices();
        return services.getFirst().getFullName();       // only one service
    }

    @Override
    public void update(Routing router) {
        router.bidi("ServerReflectionInfo", this::serverReflectionInfo);
    }

    private StreamObserver<ServerReflectionRequest> serverReflectionInfo(StreamObserver<ServerReflectionResponse> res) {
        return new StreamObserver<>() {
            @Override
            public void onNext(ServerReflectionRequest req) {
                res.onNext(processRequest(req));
            }

            @Override
            public void onError(Throwable t) {
                res.onError(t);
            }

            @Override
            public void onCompleted() {
                res.onCompleted();
            }
        };
    }

    private ServerReflectionResponse processRequest(ServerReflectionRequest req) {
        return switch (req.getMessageRequestCase().getNumber()) {
            case ServerReflectionRequest.LIST_SERVICES_FIELD_NUMBER -> listServices(req);
            case ServerReflectionRequest.FILE_CONTAINING_SYMBOL_FIELD_NUMBER -> findSymbol(req);
            default -> notImplemented("Not implemented");
        };
    }

    private ServerReflectionResponse listServices(ServerReflectionRequest req) {
        List<GrpcRouting> grpcRoutings = GrpcReflectionFeature.socketGrpcRoutings().get(socket);

        ListServiceResponse.Builder builder = ListServiceResponse.newBuilder();
        for (GrpcRouting grpcRouting : grpcRoutings) {
            for (GrpcRoute grpcRoute : grpcRouting.routes()) {
                builder.addService(ServiceResponse.newBuilder().setName(grpcRoute.serviceName()).build());
            }
        }
        return ServerReflectionResponse.newBuilder().setListServicesResponse(builder).build();
    }

    private ServerReflectionResponse findSymbol(ServerReflectionRequest req) {
        List<GrpcRouting> grpcRoutings = GrpcReflectionFeature.socketGrpcRoutings().get(socket);

        // looks for a service that matches the name in the reflection request
        for (GrpcRouting grpcRouting : grpcRoutings) {
            for (GrpcRoute grpcRoute : grpcRouting.routes()) {
                String serviceName = grpcRoute.serviceName();
                if (req.getFileContainingSymbol().equals(serviceName)) {
                    ByteString byteString = FILE_DESCRIPTOR_CACHE.get(serviceName);
                    if (byteString == null) {
                        Descriptors.FileDescriptor fileDesc = grpcRoute.proto();
                        if (fileDesc == null) {
                            LOGGER.log(Level.DEBUG, "Unable to access proto file for service " + serviceName());
                            continue;
                        }
                        DescriptorProtos.FileDescriptorProto proto = fileDesc.toProto();
                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            proto.writeTo(baos);
                            byteString = ByteString.copyFrom(baos.toByteArray());
                            FILE_DESCRIPTOR_CACHE.putIfAbsent(serviceName, byteString);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                    FileDescriptorResponse.Builder builder = FileDescriptorResponse.newBuilder();
                    builder.addFileDescriptorProto(byteString);
                    return ServerReflectionResponse.newBuilder().setFileDescriptorResponse(builder.build()).build();
                }
            }
        }
        return notFound("Unable to find proto file for " + req.getFileContainingSymbol());
    }

    private ServerReflectionResponse notImplemented(String message) {
        return ServerReflectionResponse.newBuilder().setErrorResponse(
                ErrorResponse.newBuilder().setErrorCode(Status.UNIMPLEMENTED.getCode().value())
                        .setErrorMessage(message).build()).build();
    }

    private ServerReflectionResponse notFound(String message) {
        return ServerReflectionResponse.newBuilder().setErrorResponse(
                ErrorResponse.newBuilder().setErrorCode(Status.NOT_FOUND.getCode().value())
                        .setErrorMessage(message).build()).build();
    }
}
