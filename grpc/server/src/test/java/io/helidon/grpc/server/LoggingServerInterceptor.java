package io.helidon.grpc.server;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.function.Consumer;

import java.util.logging.Logger;

public class LoggingServerInterceptor
        implements ServerInterceptor
    {
    private final String message;

    public LoggingServerInterceptor(String message)
        {
        this.message = message;
        }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
            Metadata                       headers,
            ServerCallHandler<ReqT, RespT> next)
        {
        ServerCall.Listener<ReqT> original = next.startCall(call, headers);
        return new LoggingListener<>(original, call.getMethodDescriptor().getFullMethodName(), message);
        }

    /**
     * A logging {@link ServerCall.Listener}.
     *
     * @param <ReqT>  the request type
     */
    public static class LoggingListener<ReqT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>
        {
        private final String methodName;

        private final String logMessage;

        public LoggingListener(ServerCall.Listener<ReqT> delegate, String methodName, String logMessage)
            {
            super(delegate);
            this.methodName = methodName;
            this.logMessage = logMessage;
            }

        @Override
        public void onMessage(final ReqT message)
            {
            try
                {
                LOGGER.info("GRPC entering onMessage method=" + methodName
                        + " logMessage=" + logMessage);
                super.onMessage(message);
                }
            finally
                {
                LOGGER.info("GRPC leaving onMessage method=" + methodName
                        + " logMessage=" + logMessage);
                }
            }
        }

    public static LoggingServerInterceptor create(String sMessage)
        {
        return new LoggingServerInterceptor(sMessage);
        }

    /**
     * The {@link Logger} to use.
     */
    private static final Logger LOGGER = Logger.getLogger(LoggingServerInterceptor.class.getName());
    }




