package io.helidon.grpc.server;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import io.helidon.metrics.RegistryFactory;

import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Timer;

public class GrpcMetrics
        implements ServerInterceptor
    {
    private final static MetricRegistry vendorRegistry =
            RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.VENDOR);
    private final static MetricRegistry appRegistry =
            RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);

    private MetricType type;

    private GrpcMetrics(MetricType type)
        {
        this.type = type;
        }

    public static GrpcMetrics counted()
        {
        return new GrpcMetrics(MetricType.COUNTER);
        }

    public static GrpcMetrics metered()
        {
        return new GrpcMetrics(MetricType.METERED);
        }

    public static GrpcMetrics histogram()
        {
        return new GrpcMetrics(MetricType.HISTOGRAM);
        }

    public static GrpcMetrics timed()
        {
        return new GrpcMetrics(MetricType.TIMER);
        }

    public static GrpcMetrics vendorOnly()
        {
        return new GrpcMetrics(MetricType.INVALID);
        }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
            Metadata headers, ServerCallHandler<ReqT, RespT> next)
        {
        String name = call.getMethodDescriptor().getFullMethodName().replace('/', '.');

        ServerCall<ReqT, RespT> serverCall =
                type == MetricType.COUNTER ? new CountedServerCall<>(appRegistry.counter(name), call) :
                type == MetricType.TIMER ? new TimedServerCall<>(appRegistry.timer(name), call) :
                type == MetricType.METERED ? new MeteredServerCall<>(appRegistry.meter(name), call) :
                type == MetricType.HISTOGRAM ? new HistogramServerCall<>(appRegistry.histogram(name), call) :
                call;

        serverCall = new MeteredServerCall<>(vendorRegistry.meter("grpc.requests.meter"), serverCall);
        serverCall = new CountedServerCall<>(vendorRegistry.counter("grpc.requests.count"), serverCall);

        return next.startCall(serverCall, headers);
        }

    private abstract class MetricServerCall<ReqT, RespT, MetricT>
            extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>
        {
        /**
         * The metric to update.
         */
        protected final MetricT metric;

        /**
         * Create a {@link TimedServerCall}.
         *
         * @param delegate  the call to time
         */
        MetricServerCall(MetricT metric, ServerCall<ReqT, RespT> delegate)
            {
            super(delegate);

            this.metric = metric;
            }
        }

    private class TimedServerCall<ReqT, RespT>
            extends MetricServerCall<ReqT, RespT, Timer>
        {
        /**
         * The method start time.
         */
        private final long startNanos;

        /**
         * Create a {@link TimedServerCall}.
         *
         * @param delegate  the call to time
         */
        TimedServerCall(Timer timer, ServerCall<ReqT, RespT> delegate)
            {
            super(timer, delegate);

            this.startNanos = System.nanoTime();
            }

        @Override
        public void close(Status status, Metadata responseHeaders)
            {
            super.close(status, responseHeaders);

            long time = System.nanoTime() - startNanos;
            metric.update(time, TimeUnit.NANOSECONDS);
            }
        }

    private class CountedServerCall<ReqT, RespT>
            extends MetricServerCall<ReqT, RespT, Counter>
        {
        /**
         * Create a {@link CountedServerCall}.
         *
         * @param delegate  the call to time
         */
        CountedServerCall(Counter counter, ServerCall<ReqT, RespT> delegate)
            {
            super(counter, delegate);
            }

        @Override
        public void close(Status status, Metadata responseHeaders)
            {
            super.close(status, responseHeaders);

            metric.inc();
            }
        }

    private class MeteredServerCall<ReqT, RespT>
            extends MetricServerCall<ReqT, RespT, Meter>
        {
        /**
         * Create a {@link MeteredServerCall}.
         *
         * @param delegate  the call to time
         */
        MeteredServerCall(Meter meter, ServerCall<ReqT, RespT> delegate)
            {
            super(meter, delegate);
            }

        @Override
        public void close(Status status, Metadata responseHeaders)
            {
            super.close(status, responseHeaders);

            metric.mark();
            }
        }

    private class HistogramServerCall<ReqT, RespT>
            extends MetricServerCall<ReqT, RespT, Histogram>
        {
        /**
         * Create a {@link HistogramServerCall}.
         *
         * @param delegate  the call to time
         */
        public HistogramServerCall(Histogram histogram, ServerCall<ReqT, RespT> delegate)
            {
            super(histogram, delegate);
            }

        @Override
        public void close(Status status, Metadata responseHeaders)
            {
            super.close(status, responseHeaders);

            metric.update(1);
            }
        }
    }
