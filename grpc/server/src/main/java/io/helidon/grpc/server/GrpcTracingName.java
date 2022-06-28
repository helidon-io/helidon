package io.helidon.grpc.server;

import io.grpc.MethodDescriptor;

@FunctionalInterface
public interface GrpcTracingName {
    /**
     * Constructs a span's operation name from the gRPC method.
     *
     * @param method method to extract a name from
     * @return operation name
     */
    String name(MethodDescriptor<?, ?> method);
}
