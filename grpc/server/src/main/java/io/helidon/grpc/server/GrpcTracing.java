package io.helidon.grpc.server;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.OpenTracingContextKey;
import io.opentracing.contrib.grpc.OperationNameConstructor;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GrpcTracing
        implements ServerInterceptor
    {
    // ----- constructors ---------------------------------------------------

    /**
     * Private constructor called by the {@link Builder}.
     *
     * @param tracer                    the Open Tracing {@link Tracer}
     * @param operationNameConstructor  the operation name constructor
     * @param streaming                 flag indicating whether to log streaming
     * @param verbose                   flag indicating verbose logging
     * @param tracedAttributes          the set of attributes to log in spans
     */
    private GrpcTracing(Tracer                      tracer,
            OperationNameConstructor operationNameConstructor,
            boolean                     streaming,
            boolean                     verbose,
            Set<ServerRequestAttribute> tracedAttributes)
        {
        f_tracer                   = tracer;
        f_operationNameConstructor = operationNameConstructor;
        f_streaming                = streaming;
        f_verbose                  = verbose;
        f_tracedAttributes         = tracedAttributes;
        }

    // ----- ServerTracingInterceptor methods -------------------------------

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT>        call,
            Metadata                       headers,
            ServerCallHandler<ReqT, RespT> next)
        {
        Map<String, String> headerMap = new HashMap<>();

        for (String key : headers.keys())
            {
            if (!key.endsWith(Metadata.BINARY_HEADER_SUFFIX))
                {
                String value = headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
                headerMap.put(key, value);
                }
            }

        final String operationName = f_operationNameConstructor.constructOperationName(call.getMethodDescriptor());
        final Span span          = getSpanFromHeaders(headerMap, operationName);

        for (ServerRequestAttribute attr : f_tracedAttributes)
            {
            switch (attr)
                {
                case METHOD_TYPE:
                    span.setTag("grpc.method_type", call.getMethodDescriptor().getType().toString());
                    break;
                case METHOD_NAME:
                    span.setTag("grpc.method_name", call.getMethodDescriptor().getFullMethodName());
                    break;
                case CALL_ATTRIBUTES:
                    span.setTag("grpc.call_attributes", call.getAttributes().toString());
                    break;
                case HEADERS:
                    // copy the headers and make sure that the AUTHORIZATION header
                    // is removed as we do not want auth details to appear in tracing logs
                    Metadata metadata = new Metadata();

                    metadata.merge(headers);
                  //  metadata.removeAll(AuthServerInterceptor.AUTHORIZATION);

                    span.setTag("grpc.headers", metadata.toString());
                    break;
                }
            }

        Context ctxWithSpan         = Context.current().withValue(OpenTracingContextKey.getKey(), span);
        ServerCall.Listener<ReqT> listenerWithContext = Contexts.interceptCall(ctxWithSpan, call, headers, next);


        return new TracingListener<>(listenerWithContext, span);
        }

    // ----- helper methods -------------------------------------------------

    private Span getSpanFromHeaders(Map<String, String> headers, String operationName)
        {
        Span span;

        try
            {
            SpanContext parentSpanCtx = f_tracer.extract(Format.Builtin.HTTP_HEADERS,
                    new TextMapExtractAdapter(headers));
            if (parentSpanCtx == null)
                {
                span = f_tracer.buildSpan(operationName)
                        .start();
                }
            else
                {
                span = f_tracer.buildSpan(operationName)
                        .asChildOf(parentSpanCtx)
                        .start();
                }
            }
        catch (IllegalArgumentException iae)
            {
            span = f_tracer.buildSpan(operationName)
                    .withTag("Error", "Extract failed and an IllegalArgumentException was thrown")
                    .start();
            }

        return span;
        }

    // ----- inner class: Builder -------------------------------------------

    /**
     * Builds the configuration of a ServerTracingInterceptor.
     */
    public static class Builder
        {
        // ----- constructors -----------------------------------------------

        /**
         * @param tracer to use for this interceptor
         *               Creates a Builder with default configuration
         */
        public Builder(Tracer tracer)
            {
            f_tracer                   = tracer;
            m_operationNameConstructor = OperationNameConstructor.DEFAULT;
            m_streaming                = false;
            m_verbose                  = false;
            m_tracedAttributes         = Collections.emptySet();
            }

        // ----- Builder methods --------------------------------------------

        /**
         * @param operationNameConstructor for all spans created by this interceptor
         *
         * @return this Builder with configured operation name
         */
        public Builder withOperationName(OperationNameConstructor operationNameConstructor)
            {
            m_operationNameConstructor = operationNameConstructor;
            return this;
            }

        /**
         * @param attributes to set as tags on server spans
         *                   created by this interceptor
         *
         * @return this Builder configured to trace request attributes
         */
        public Builder withTracedAttributes(ServerRequestAttribute... attributes)
            {
            m_tracedAttributes = new HashSet<>(Arrays.asList(attributes));
            return this;
            }

        /**
         * Logs streaming events to server spans.
         *
         * @return this Builder configured to log streaming events
         */
        public Builder withStreaming()
            {
            m_streaming = true;
            return this;
            }

        /**
         * Logs all request life-cycle events to server spans.
         *
         * @return this Builder configured to be verbose
         */
        public Builder withVerbosity()
            {
            m_verbose = true;
            return this;
            }

        /**
         * @return a ServerTracingInterceptor with this Builder's configuration
         */
        public GrpcTracing build()
            {
            return new GrpcTracing(f_tracer,
                    m_operationNameConstructor,
                    m_streaming,
                    m_verbose,
                    m_tracedAttributes);
            }

        // ----- data members -----------------------------------------------

        /**
         * The Open Tracing {@link Tracer}.
         */
        private final Tracer f_tracer;

        /**
         * A flag indicating whether to log streaming.
         */
        private OperationNameConstructor m_operationNameConstructor;

        /**
         * A flag indicating verbose logging.
         */
        private boolean m_streaming;

        /**
         * A flag indicating verbose logging.
         */
        private boolean m_verbose;

        /**
         * The set of attributes to log in spans.
         */
        private Set<ServerRequestAttribute> m_tracedAttributes;
        }

    private class TracingListener<ReqT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>
        {

        // Constructor
        public TracingListener(ServerCall.Listener<ReqT> delegate, Span span)
            {
            super(delegate);
            this.span = span;
            }

        @Override
        public void onMessage(ReqT message)
            {
            if (f_streaming || f_verbose)
                {
                span.log(Collections.singletonMap("Message received", message));
                }

            delegate().onMessage(message);
            }

        @Override
        public void onHalfClose()
            {
            if (f_streaming)
                {
                span.log("Client finished sending messages");
                }

            delegate().onHalfClose();
            }

        @Override
        public void onCancel()
            {
            span.log("Call cancelled");

            try
                {
                delegate().onCancel();
                }
            finally
                {
                span.finish();
                }
            }

        @Override
        public void onComplete()
            {
            if (f_verbose)
                {
                span.log("Call completed");
                }

            try
                {
                delegate().onComplete();
                }
            finally
                {
                span.finish();
                }
            }

        final Span span;
        }

    // ----- data members ---------------------------------------------------

    /**
     * The Open Tracing {@link Tracer}.
     */
    private final Tracer f_tracer;

    /**
     * A flag indicating whether to log streaming.
     */
    private final OperationNameConstructor f_operationNameConstructor;

    /**
     */
    private final boolean f_streaming;

    /**
     * A flag indicating verbose logging.
     */
    private final boolean f_verbose;

    /**
     * The set of attributes to log in spans.
     */
    private final Set<ServerRequestAttribute> f_tracedAttributes;
    }
