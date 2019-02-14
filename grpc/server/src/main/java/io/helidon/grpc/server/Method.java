package io.helidon.grpc.server;


import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;


/**
 * An interface that must be implemented by all gRPC methods.
 *
 * @param <ReqT>  the type of gRPC request class
 * @param <ResT>  the type of gRPC response class
 *
 * @author Aleksandar Seovic  2017.09.19
 */
@FunctionalInterface
public interface Method<ReqT, ResT>
    {
    /**
     * Execute a request and write one or more results into the response.
     *
     * @param request  the request to execute
     * @param response the response to write the result(s) to
     */
    void execute(ReqT request, Response<ResT> response);
    }
