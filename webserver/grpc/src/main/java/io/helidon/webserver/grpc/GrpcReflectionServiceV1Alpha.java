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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.grpc.Status;
import io.grpc.reflection.v1alpha.ErrorResponse;
import io.grpc.reflection.v1alpha.FileDescriptorResponse;
import io.grpc.reflection.v1alpha.ListServiceResponse;
import io.grpc.reflection.v1alpha.ServerReflectionProto;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.reflection.v1alpha.ServiceResponse;
import io.grpc.stub.StreamObserver;

/**
 * Grpc reflection service version v1alpha. Some tools such as Postman still do not
 * support version v1. Once more tools support the new version we can remove support
 * for version v1alpha. Note the code in this class is almost identical to
 * {@link io.helidon.webserver.grpc.GrpcReflectionService} except for the code-generated
 * protobuf types.
 *
 * @see io.helidon.webserver.grpc.GrpcReflectionService
 */
class GrpcReflectionServiceV1Alpha implements GrpcService {

    /**
     * Caches FileDescriptorProto representations as byte strings to avoid serialization
     * on every reflection request.
     */
    private static final Map<String, ByteString> FILE_DESCRIPTOR_CACHE = new ConcurrentHashMap<>();

    private final String socket;

    GrpcReflectionServiceV1Alpha(String socket) {
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
            case ServerReflectionRequest.LIST_SERVICES_FIELD_NUMBER -> listServices();
            case ServerReflectionRequest.FILE_BY_FILENAME_FIELD_NUMBER -> findFile(req);
            case ServerReflectionRequest.FILE_CONTAINING_SYMBOL_FIELD_NUMBER -> findSymbol(req);
            case ServerReflectionRequest.FILE_CONTAINING_EXTENSION_FIELD_NUMBER -> findExtensionField(req);
            default -> notImplemented();
        };
    }

    private ServerReflectionResponse listServices() {
        List<GrpcRouting> grpcRoutings = GrpcReflectionFeature.socketGrpcRoutings().get(socket);

        ListServiceResponse.Builder builder = ListServiceResponse.newBuilder();
        for (GrpcRouting grpcRouting : grpcRoutings) {
            for (GrpcRoute grpcRoute : grpcRouting.routes()) {
                builder.addService(ServiceResponse.newBuilder().setName(grpcRoute.serviceName()).build());
            }
        }
        return ServerReflectionResponse.newBuilder().setListServicesResponse(builder).build();
    }

    private ServerReflectionResponse findFile(ServerReflectionRequest req) {
        String fileName = req.getFileByFilename();
        String cachedFileNameKey = "/" + fileName;      // not a legal identifier
        ByteString byteString = FILE_DESCRIPTOR_CACHE.get(cachedFileNameKey);
        if (byteString != null) {
            return fileDescResponse(byteString);
        }

        List<GrpcRouting> grpcRoutings = GrpcReflectionFeature.socketGrpcRoutings().get(socket);
        for (GrpcRouting grpcRouting : grpcRoutings) {
            for (GrpcRoute grpcRoute : grpcRouting.routes()) {
                Descriptors.FileDescriptor fileDesc = grpcRoute.proto();
                if (fileDesc.getFile().getFullName().equals(fileName)) {
                    return symbolFound(fileDesc, cachedFileNameKey);
                }
            }
        }
        return notFound("Unable to find file name " + fileName);
    }

    private ServerReflectionResponse findSymbol(ServerReflectionRequest req) {
        String symbol = req.getFileContainingSymbol();
        ByteString byteString = FILE_DESCRIPTOR_CACHE.get(symbol);
        if (byteString != null) {
            return fileDescResponse(byteString);
        }

        List<GrpcRouting> grpcRoutings = GrpcReflectionFeature.socketGrpcRoutings().get(socket);
        for (GrpcRouting grpcRouting : grpcRoutings) {
            for (GrpcRoute grpcRoute : grpcRouting.routes()) {
                Descriptors.FileDescriptor fileDesc = grpcRoute.proto();

                // scan through services and methods
                List<Descriptors.ServiceDescriptor> services = fileDesc.getServices();
                for (Descriptors.ServiceDescriptor service : services) {
                    if (service.getFullName().equals(symbol)) {
                        return symbolFound(fileDesc, symbol);
                    }
                    List<Descriptors.MethodDescriptor> methods = service.getMethods();
                    for (Descriptors.MethodDescriptor method : methods) {
                        if (method.getFullName().equals(symbol)) {
                            return symbolFound(fileDesc, symbol);
                        }
                    }
                }

                // scan through message types
                List<Descriptors.Descriptor> types = fileDesc.getMessageTypes();
                for (Descriptors.Descriptor type : types) {
                    if (type.getFullName().equals(symbol)) {
                        return symbolFound(fileDesc, symbol);
                    }
                }
            }
        }
        return notFound("Unable to find proto file for " + symbol);
    }

    private ServerReflectionResponse findExtensionField(ServerReflectionRequest req) {
        String type = req.getFileContainingExtension().getContainingType();
        int number = req.getFileContainingExtension().getExtensionNumber();
        String cachedFileNameKey = number + type;      // not a legal identifier
        ByteString byteString = FILE_DESCRIPTOR_CACHE.get(cachedFileNameKey);
        if (byteString != null) {
            return fileDescResponse(byteString);
        }

        List<GrpcRouting> grpcRoutings = GrpcReflectionFeature.socketGrpcRoutings().get(socket);
        for (GrpcRouting grpcRouting : grpcRoutings) {
            for (GrpcRoute grpcRoute : grpcRouting.routes()) {
                Descriptors.FileDescriptor fileDesc = grpcRoute.proto();
                List<Descriptors.FieldDescriptor> extensions = fileDesc.getExtensions();
                for (Descriptors.FieldDescriptor extension : extensions) {
                    if (extension.getContainingType().getFullName().equals(type)
                            && extension.toProto().getNumber() == number) {
                        return symbolFound(fileDesc, cachedFileNameKey);
                    }
                }
            }
        }
        return notFound("Unable to find proto file for " + type);
    }

    private ServerReflectionResponse symbolFound(Descriptors.FileDescriptor fileDesc, String symbol) {
        ByteString byteString;
        DescriptorProtos.FileDescriptorProto proto = fileDesc.toProto();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            proto.writeTo(baos);
            byteString = ByteString.copyFrom(baos.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ByteString cachedValue = FILE_DESCRIPTOR_CACHE.putIfAbsent(symbol, byteString);
        return fileDescResponse(cachedValue != null ? cachedValue : byteString);
    }

    private ServerReflectionResponse fileDescResponse(ByteString byteString) {
        FileDescriptorResponse.Builder builder = FileDescriptorResponse.newBuilder();
        builder.addFileDescriptorProto(byteString);
        return ServerReflectionResponse.newBuilder().setFileDescriptorResponse(builder.build()).build();
    }

    private ServerReflectionResponse notImplemented() {
        return ServerReflectionResponse.newBuilder().setErrorResponse(
                ErrorResponse.newBuilder().setErrorCode(Status.UNIMPLEMENTED.getCode().value())
                        .setErrorMessage("Reflection request not implemented").build()).build();
    }

    private ServerReflectionResponse notFound(String message) {
        return ServerReflectionResponse.newBuilder().setErrorResponse(
                ErrorResponse.newBuilder().setErrorCode(Status.NOT_FOUND.getCode().value())
                        .setErrorMessage(message).build()).build();
    }
}
