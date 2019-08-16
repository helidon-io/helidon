/**
 * @author jk  2019.08.15
 */
module io.helidon.microprofile.grpc.client {
    exports io.helidon.microprofile.grpc.client;
    exports io.helidon.microprofile.grpc.client.model;

    requires transitive io.helidon.microprofile.grpc.core;

    requires java.logging;

    provides javax.enterprise.inject.spi.Extension
            with io.helidon.microprofile.grpc.client.GrpcClientCdiExtension;
}