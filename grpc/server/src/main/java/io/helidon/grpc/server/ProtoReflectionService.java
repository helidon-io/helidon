/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

/*
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2016 The gRPC Authors
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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;

import com.google.protobuf.Any;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import io.grpc.BindableService;
import io.grpc.InternalServer;
import io.grpc.Server;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;
import io.grpc.reflection.v1alpha.ErrorResponse;
import io.grpc.reflection.v1alpha.ExtensionNumberResponse;
import io.grpc.reflection.v1alpha.ExtensionRequest;
import io.grpc.reflection.v1alpha.FileDescriptorResponse;
import io.grpc.reflection.v1alpha.ListServiceResponse;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.reflection.v1alpha.ServiceResponse;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import static io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING;
import static io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING;
import static io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING;

/**
 * Provides a reflection service for Protobuf services (including the reflection service itself).
 *
 * <p>Separately tracks mutable and immutable services. Throws an exception if either group of
 * services contains multiple Protobuf files with declarations of the same service, method, type, or
 * extension.
 */
public final class ProtoReflectionService extends ServerReflectionGrpc.ServerReflectionImplBase {

    private final Object lock = new Object();

    private final Map<Server, ServerReflectionIndex> serverReflectionIndexes = new WeakHashMap<>();

    private ProtoReflectionService() {
    }

    /**
     * Creates a instance of {@link ProtoReflectionService}.
     * @return a service instance
     */
    public static BindableService newInstance() {
        return new ProtoReflectionService();
    }

    /**
     * Retrieves the index for services of the server that dispatches the current call. Computes
     * one if not exist. The index is updated if any changes to the server's mutable services are
     * detected. A change is any addition or removal in the set of file descriptors attached to the
     * mutable services or a change in the service names.
     */
    private ServerReflectionIndex getRefreshedIndex() {
        synchronized (lock) {
            Server server = InternalServer.SERVER_CONTEXT_KEY.get();
            ServerReflectionIndex index = serverReflectionIndexes.get(server);
            if (index == null) {
                index =
                        new ServerReflectionIndex(server.getImmutableServices(), server.getMutableServices());
                serverReflectionIndexes.put(server, index);
                return index;
            }

            Set<FileDescriptor> serverFileDescriptors = new HashSet<>();
            Set<String> serverServiceNames = new HashSet<>();
            List<ServerServiceDefinition> serverMutableServices = server.getMutableServices();
            for (ServerServiceDefinition mutableService : serverMutableServices) {
                io.grpc.ServiceDescriptor serviceDescriptor = mutableService.getServiceDescriptor();
                if (serviceDescriptor.getSchemaDescriptor() instanceof ProtoFileDescriptorSupplier) {
                    String serviceName = serviceDescriptor.getName();
                    FileDescriptor fileDescriptor =
                            ((ProtoFileDescriptorSupplier) serviceDescriptor.getSchemaDescriptor())
                                    .getFileDescriptor();
                    serverFileDescriptors.add(fileDescriptor);
                    serverServiceNames.add(serviceName);
                }
            }

            // Replace the index if the underlying mutable services have changed. Check both the file
            // descriptors and the service names, because one file descriptor can define multiple
            // services.
            FileDescriptorIndex mutableServicesIndex = index.getMutableServicesIndex();
            if (!mutableServicesIndex.getServiceFileDescriptors().equals(serverFileDescriptors)
                    || !mutableServicesIndex.getServiceNames().equals(serverServiceNames)) {
                index =
                        new ServerReflectionIndex(server.getImmutableServices(), serverMutableServices);
                serverReflectionIndexes.put(server, index);
            }

            return index;
        }
    }

    @Override
    public StreamObserver<ServerReflectionRequest> serverReflectionInfo(
            final StreamObserver<ServerReflectionResponse> responseObserver) {
        final ServerCallStreamObserver<ServerReflectionResponse> serverCallStreamObserver =
                (ServerCallStreamObserver<ServerReflectionResponse>) responseObserver;
        ProtoReflectionStreamObserver requestObserver =
                new ProtoReflectionStreamObserver(getRefreshedIndex(), serverCallStreamObserver);
        serverCallStreamObserver.setOnReadyHandler(requestObserver);
        serverCallStreamObserver.disableAutoRequest();
        serverCallStreamObserver.request(1);
        return requestObserver;
    }

    private static class ProtoReflectionStreamObserver
            implements Runnable, StreamObserver<ServerReflectionRequest> {
        private final ServerReflectionIndex serverReflectionIndex;
        private final ServerCallStreamObserver<ServerReflectionResponse> serverCallStreamObserver;

        private boolean closeAfterSend = false;
        private ServerReflectionRequest request;

        ProtoReflectionStreamObserver(
                ServerReflectionIndex serverReflectionIndex,
                ServerCallStreamObserver<ServerReflectionResponse> serverCallStreamObserver) {
            this.serverReflectionIndex = serverReflectionIndex;
            this.serverCallStreamObserver = Objects.requireNonNull(serverCallStreamObserver, "observer");
        }

        @Override
        public void run() {
            if (request != null) {
                handleReflectionRequest();
            }
        }

        @Override
        public void onNext(ServerReflectionRequest request) {
            this.request = Objects.requireNonNull(request);
            handleReflectionRequest();
        }

        private void handleReflectionRequest() {
            if (serverCallStreamObserver.isReady()) {
                switch (request.getMessageRequestCase()) {
                case FILE_BY_FILENAME:
                    getFileByName(request);
                    break;
                case FILE_CONTAINING_SYMBOL:
                    getFileContainingSymbol(request);
                    break;
                case FILE_CONTAINING_EXTENSION:
                    getFileByExtension(request);
                    break;
                case ALL_EXTENSION_NUMBERS_OF_TYPE:
                    getAllExtensions(request);
                    break;
                case LIST_SERVICES:
                    listServices(request);
                    break;
                default:
                    sendErrorResponse(
                            request,
                            Status.Code.UNIMPLEMENTED,
                            "not implemented " + request.getMessageRequestCase());
                }
                request = null;
                if (closeAfterSend) {
                    serverCallStreamObserver.onCompleted();
                } else {
                    serverCallStreamObserver.request(1);
                }
            }
        }

        @Override
        public void onCompleted() {
            if (request != null) {
                closeAfterSend = true;
            } else {
                serverCallStreamObserver.onCompleted();
            }
        }

        @Override
        public void onError(Throwable cause) {
            serverCallStreamObserver.onError(cause);
        }

        private void getFileByName(ServerReflectionRequest request) {
            String name = request.getFileByFilename();
            FileDescriptor fd = serverReflectionIndex.getFileDescriptorByName(name);
            if (fd != null) {
                serverCallStreamObserver.onNext(createServerReflectionResponse(request, fd));
            } else {
                sendErrorResponse(request, Status.Code.NOT_FOUND, "File not found.");
            }
        }

        private void getFileContainingSymbol(ServerReflectionRequest request) {
            String symbol = request.getFileContainingSymbol();
            FileDescriptor fd = serverReflectionIndex.getFileDescriptorBySymbol(symbol);
            if (fd != null) {
                serverCallStreamObserver.onNext(createServerReflectionResponse(request, fd));
            } else {
                getFileContainingSymbolFromServer(request);
            }
        }

        private void getFileContainingSymbolFromServer(ServerReflectionRequest request) {
            Server server = InternalServer.SERVER_CONTEXT_KEY.get();
            String symbol = request.getFileContainingSymbol();

            try {
                ServerServiceDefinition service = server.getServices().stream()
                        .filter(s -> s.getServiceDescriptor().getName().equals(symbol))
                        .findFirst().orElse(null);

                if (service != null) {
                    sendServiceDetails(request, service, null);
                } else {
                    sendErrorResponse(request, Status.Code.NOT_FOUND, "Symbol not found.");
                }

            } catch (Throwable e) {
                sendErrorResponse(request, Status.Code.NOT_FOUND, "Symbol not found.");
            }
        }

        private void sendServiceDetails(ServerReflectionRequest request,
                                        ServerServiceDefinition service,
                                        ServerMethodDefinition method)
                throws Descriptors.DescriptorValidationException {
            String name = service.getServiceDescriptor().getName();
            var serviceDesc = DescriptorProtos.ServiceDescriptorProto.newBuilder()
                    .setName(name.substring(name.lastIndexOf('.') + 1));
            Collection<ServerMethodDefinition<?, ?>> methods = method == null
                    ? service.getMethods()
                    : Set.of(method);
            for (ServerMethodDefinition<?, ?> m : methods) {
                io.grpc.MethodDescriptor<?, ?> md = m.getMethodDescriptor();
                serviceDesc.addMethod(DescriptorProtos.MethodDescriptorProto.newBuilder()
                                              .setName(md.getBareMethodName())
                                              .setServerStreaming(EnumSet.of(SERVER_STREAMING, BIDI_STREAMING)
                                                                          .contains(md.getType()))
                                              .setClientStreaming(EnumSet.of(CLIENT_STREAMING, BIDI_STREAMING)
                                                                          .contains(md.getType()))
                                              .setInputType(Any.getDescriptor().getFullName())
                                              .setOutputType(Any.getDescriptor().getFullName())
                                              .build());
            }

            var fileDesc = DescriptorProtos.FileDescriptorProto.newBuilder()
                    .setPackage(name.substring(0, name.lastIndexOf('.')))
                    .addDependency(Any.getDescriptor().getFile().getFullName())
                    .addService(serviceDesc).build();

            serverCallStreamObserver.onNext(createServerReflectionResponse(request,
                   FileDescriptor.buildFrom(fileDesc,
                                            new FileDescriptor[] {Any.getDescriptor().getFile()},
                                            true)));
        }

        private void getFileByExtension(ServerReflectionRequest request) {
            ExtensionRequest extensionRequest = request.getFileContainingExtension();
            String type = extensionRequest.getContainingType();
            int extension = extensionRequest.getExtensionNumber();
            FileDescriptor fd =
                    serverReflectionIndex.getFileDescriptorByExtensionAndNumber(type, extension);
            if (fd != null) {
                serverCallStreamObserver.onNext(createServerReflectionResponse(request, fd));
            } else {
                sendErrorResponse(request, Status.Code.NOT_FOUND, "Extension not found.");
            }
        }

        private void getAllExtensions(ServerReflectionRequest request) {
            String type = request.getAllExtensionNumbersOfType();
            Set<Integer> extensions = serverReflectionIndex.getExtensionNumbersOfType(type);
            if (extensions != null) {
                ExtensionNumberResponse.Builder builder =
                        ExtensionNumberResponse.newBuilder()
                                .setBaseTypeName(type)
                                .addAllExtensionNumber(extensions);
                serverCallStreamObserver.onNext(
                        ServerReflectionResponse.newBuilder()
                                .setValidHost(request.getHost())
                                .setOriginalRequest(request)
                                .setAllExtensionNumbersResponse(builder)
                                .build());
            } else {
                sendErrorResponse(request, Status.Code.NOT_FOUND, "Type not found.");
            }
        }

        private void listServices(ServerReflectionRequest request) {
            ListServiceResponse.Builder builder = ListServiceResponse.newBuilder();
            for (String serviceName : serverReflectionIndex.getServiceNames()) {
                builder.addService(ServiceResponse.newBuilder().setName(serviceName));
            }
            Server server = InternalServer.SERVER_CONTEXT_KEY.get();
            for (ServerServiceDefinition def : server.getServices()) {
                if (def.getServiceDescriptor().getSchemaDescriptor() == null) {
                    builder.addService(ServiceResponse.newBuilder().setName(def.getServiceDescriptor().getName()));
                }
            }
            serverCallStreamObserver.onNext(
                    ServerReflectionResponse.newBuilder()
                            .setValidHost(request.getHost())
                            .setOriginalRequest(request)
                            .setListServicesResponse(builder)
                            .build());
        }

        private void sendErrorResponse(
                ServerReflectionRequest request, Status.Code code, String message) {
            ServerReflectionResponse response =
                    ServerReflectionResponse.newBuilder()
                            .setValidHost(request.getHost())
                            .setOriginalRequest(request)
                            .setErrorResponse(
                                    ErrorResponse.newBuilder()
                                            .setErrorCode(code.value())
                                            .setErrorMessage(message))
                            .build();
            serverCallStreamObserver.onNext(response);
        }

        private ServerReflectionResponse createServerReflectionResponse(
                ServerReflectionRequest request, FileDescriptor fd) {
            FileDescriptorResponse.Builder fdRBuilder = FileDescriptorResponse.newBuilder();

            Set<String> seenFiles = new HashSet<>();
            Queue<FileDescriptor> frontier = new ArrayDeque<>();
            seenFiles.add(fd.getName());
            frontier.add(fd);
            while (!frontier.isEmpty()) {
                FileDescriptor nextFd = frontier.remove();
                fdRBuilder.addFileDescriptorProto(nextFd.toProto().toByteString());
                for (FileDescriptor dependencyFd : nextFd.getDependencies()) {
                    if (!seenFiles.contains(dependencyFd.getName())) {
                        seenFiles.add(dependencyFd.getName());
                        frontier.add(dependencyFd);
                    }
                }
            }
            return ServerReflectionResponse.newBuilder()
                    .setValidHost(request.getHost())
                    .setOriginalRequest(request)
                    .setFileDescriptorResponse(fdRBuilder)
                    .build();
        }
    }

    /**
     * Indexes the server's services and allows lookups of file descriptors by filename, symbol, type,
     * and extension number.
     *
     * <p>Internally, this stores separate indices for the immutable and mutable services. When
     * queried, the immutable service index is checked for a matching value. Only if there is no match
     * in the immutable service index are the mutable services checked.
     */
    private static final class ServerReflectionIndex {
        private final FileDescriptorIndex immutableServicesIndex;
        private final FileDescriptorIndex mutableServicesIndex;

        ServerReflectionIndex(
                List<ServerServiceDefinition> immutableServices,
                List<ServerServiceDefinition> mutableServices) {
            immutableServicesIndex = new FileDescriptorIndex(immutableServices);
            mutableServicesIndex = new FileDescriptorIndex(mutableServices);
        }

        private FileDescriptorIndex getMutableServicesIndex() {
            return mutableServicesIndex;
        }

        private Set<String> getServiceNames() {
            Set<String> immutableServiceNames = immutableServicesIndex.getServiceNames();
            Set<String> mutableServiceNames = mutableServicesIndex.getServiceNames();
            Set<String> serviceNames =
                    new HashSet<>(immutableServiceNames.size() + mutableServiceNames.size());
            serviceNames.addAll(immutableServiceNames);
            serviceNames.addAll(mutableServiceNames);
            return serviceNames;
        }

        private FileDescriptor getFileDescriptorByName(String name) {
            FileDescriptor fd = immutableServicesIndex.getFileDescriptorByName(name);
            if (fd == null) {
                fd = mutableServicesIndex.getFileDescriptorByName(name);
            }
            return fd;
        }

        private FileDescriptor getFileDescriptorBySymbol(String symbol) {
            FileDescriptor fd = immutableServicesIndex.getFileDescriptorBySymbol(symbol);
            if (fd == null) {
                fd = mutableServicesIndex.getFileDescriptorBySymbol(symbol);
            }
            return fd;
        }

        private FileDescriptor getFileDescriptorByExtensionAndNumber(String type, int extension) {
            FileDescriptor fd =
                    immutableServicesIndex.getFileDescriptorByExtensionAndNumber(type, extension);
            if (fd == null) {
                fd = mutableServicesIndex.getFileDescriptorByExtensionAndNumber(type, extension);
            }
            return fd;
        }

        private Set<Integer> getExtensionNumbersOfType(String type) {
            Set<Integer> extensionNumbers = immutableServicesIndex.getExtensionNumbersOfType(type);
            if (extensionNumbers == null) {
                extensionNumbers = mutableServicesIndex.getExtensionNumbersOfType(type);
            }
            return extensionNumbers;
        }
    }

    /**
     * Provides a set of methods for answering reflection queries for the file descriptors underlying
     * a set of services. Used by {@link ServerReflectionIndex} to separately index immutable and
     * mutable services.
     */
    private static final class FileDescriptorIndex {
        private final Set<String> serviceNames = new HashSet<>();
        private final Set<FileDescriptor> serviceFileDescriptors = new HashSet<>();
        private final Map<String, FileDescriptor> fileDescriptorsByName =
                new HashMap<>();
        private final Map<String, FileDescriptor> fileDescriptorsBySymbol =
                new HashMap<>();
        private final Map<String, Map<Integer, FileDescriptor>> fileDescriptorsByExtensionAndNumber =
                new HashMap<>();

        FileDescriptorIndex(List<ServerServiceDefinition> services) {
            Queue<FileDescriptor> fileDescriptorsToProcess = new ArrayDeque<>();
            Set<String> seenFiles = new HashSet<>();
            for (ServerServiceDefinition service : services) {
                io.grpc.ServiceDescriptor serviceDescriptor = service.getServiceDescriptor();
                if (serviceDescriptor.getSchemaDescriptor() instanceof ProtoFileDescriptorSupplier) {
                    FileDescriptor fileDescriptor =
                            ((ProtoFileDescriptorSupplier) serviceDescriptor.getSchemaDescriptor())
                                    .getFileDescriptor();
                    String serviceName = serviceDescriptor.getName();
                    serviceFileDescriptors.add(fileDescriptor);
                    serviceNames.add(serviceName);
                    if (!seenFiles.contains(fileDescriptor.getName())) {
                        seenFiles.add(fileDescriptor.getName());
                        fileDescriptorsToProcess.add(fileDescriptor);
                    }
                }
            }

            while (!fileDescriptorsToProcess.isEmpty()) {
                FileDescriptor currentFd = fileDescriptorsToProcess.remove();
                processFileDescriptor(currentFd);
                for (FileDescriptor dependencyFd : currentFd.getDependencies()) {
                    if (!seenFiles.contains(dependencyFd.getName())) {
                        seenFiles.add(dependencyFd.getName());
                        fileDescriptorsToProcess.add(dependencyFd);
                    }
                }
            }
        }

        /**
         * Returns the file descriptors for the indexed services, but not their dependencies. This is
         * used to check if the server's mutable services have changed.
         */
        private Set<FileDescriptor> getServiceFileDescriptors() {
            return Collections.unmodifiableSet(serviceFileDescriptors);
        }

        private Set<String> getServiceNames() {
            return Collections.unmodifiableSet(serviceNames);
        }

        private FileDescriptor getFileDescriptorByName(String name) {
            return fileDescriptorsByName.get(name);
        }

        private FileDescriptor getFileDescriptorBySymbol(String symbol) {
            return fileDescriptorsBySymbol.get(symbol);
        }

        private FileDescriptor getFileDescriptorByExtensionAndNumber(String type, int number) {
            if (fileDescriptorsByExtensionAndNumber.containsKey(type)) {
                return fileDescriptorsByExtensionAndNumber.get(type).get(number);
            }
            return null;
        }

        private Set<Integer> getExtensionNumbersOfType(String type) {
            if (fileDescriptorsByExtensionAndNumber.containsKey(type)) {
                return Collections.unmodifiableSet(fileDescriptorsByExtensionAndNumber.get(type).keySet());
            }
            return null;
        }

        private void processFileDescriptor(FileDescriptor fd) {
            String fdName = fd.getName();
            fileDescriptorsByName.put(fdName, fd);
            for (ServiceDescriptor service : fd.getServices()) {
                processService(service, fd);
            }
            for (Descriptor type : fd.getMessageTypes()) {
                processType(type, fd);
            }
            for (FieldDescriptor extension : fd.getExtensions()) {
                processExtension(extension, fd);
            }
        }

        private void processService(ServiceDescriptor service, FileDescriptor fd) {
            String serviceName = service.getFullName();
            fileDescriptorsBySymbol.put(serviceName, fd);
            for (MethodDescriptor method : service.getMethods()) {
                String methodName = method.getFullName();
                fileDescriptorsBySymbol.put(methodName, fd);
            }
        }

        private void processType(Descriptor type, FileDescriptor fd) {
            String typeName = type.getFullName();
            fileDescriptorsBySymbol.put(typeName, fd);
            for (FieldDescriptor extension : type.getExtensions()) {
                processExtension(extension, fd);
            }
            for (Descriptor nestedType : type.getNestedTypes()) {
                processType(nestedType, fd);
            }
        }

        private void processExtension(FieldDescriptor extension, FileDescriptor fd) {
            String extensionName = extension.getContainingType().getFullName();
            int extensionNumber = extension.getNumber();
            if (!fileDescriptorsByExtensionAndNumber.containsKey(extensionName)) {
                fileDescriptorsByExtensionAndNumber.put(
                        extensionName, new HashMap<Integer, FileDescriptor>());
            }
            fileDescriptorsByExtensionAndNumber.get(extensionName).put(extensionNumber, fd);
        }
    }
}
