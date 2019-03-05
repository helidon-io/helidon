package io.helidon.grpc.server;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.helidon.config.Config;
import io.helidon.metrics.RegistryFactory;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;

public class GrpcMetrics
        implements ServerInterceptor
    {
    private final MetricRegistry registry = RegistryFactory.getRegistryFactory()
            .get().getRegistry(MetricRegistry.Type.APPLICATION);

    private static GrpcMetrics grpcMetrics;

    private GrpcMetrics(Builder builder)
        {
        }

    public static synchronized GrpcMetrics create()
        {
        if (grpcMetrics == null)
            {
            grpcMetrics = builder().build();
            }
        return grpcMetrics;
        }

    public static synchronized GrpcMetrics create(Config config)
        {
        if (grpcMetrics == null)
            {
            grpcMetrics = builder().config(config).build();
            }
        return grpcMetrics;
        }

    /**
     * Create a new builder to construct an instance.
     *
     * @return A new builder instance
     */
    public static Builder builder()
        {
        return new Builder();
        }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
            Metadata headers, ServerCallHandler<ReqT, RespT> next)
        {
        String methodName = call.getMethodDescriptor().getFullMethodName();
        MetricsServerCall<ReqT, RespT> serverCall = new MetricsServerCall<>(methodName, call);

        return next.startCall(serverCall, headers);
        }

    private class MetricsServerCall<ReqT, RespT>
            extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>
        {
        /**
         * The {@link Timer} to update.
         */
        private final String methodName;

        /**
         * The method start time.
         */
        private final long startNanos;

        /**
         * Create a {@link MetricsServerCall}.
         *
         * @param delegate  the call to time
         */
        public MetricsServerCall(String methodName, ServerCall<ReqT, RespT> delegate)
            {
            super(delegate);

            this.startNanos = System.nanoTime();
            this.methodName = methodName;
            }

        @Override
        public void close(Status status, Metadata responseHeaders)
            {
            super.close(status, responseHeaders);

            long time = System.nanoTime() - startNanos;
            Timer timer = registry.timer(methodName + "_timer");
            Counter counter = registry.counter(methodName + "_count");

            timer.update(time, TimeUnit.NANOSECONDS);
            counter.inc();
            }
        }

    /**
     * A fluent API builder to build instances of {@link GrpcMetrics}.
     */
    public static final class Builder implements io.helidon.common.Builder<GrpcMetrics>
        {
        private Config config = Config.empty();

        private Builder()
            {
            }

        @Override
        public GrpcMetrics build()
            {
            return new GrpcMetrics(this);
            }

        /**
         * Override default configuration.
         *
         * @param config configuration instance
         * @return updated builder instance
         * @see GrpcMetrics for details about configuration keys
         */
        public Builder config(Config config)
            {
            this.config = config;

            return this;
            }
        }
    }
