package io.helidon.grpc.server;


import io.grpc.MethodDescriptor.MethodType;


/**
 * @author Aleksandar Seovic  2019.02.05
 */
public class EchoService
        extends AbstractGrpcService
    {
    protected void defineMethods()
        {
        addMethod("echo", MethodType.UNARY, this::echo);
        }

    @RpcMethod(type = MethodType.UNARY)
    void echo(String request, Response<String> response)
        {
        response.send(request);
        }
    }
