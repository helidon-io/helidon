package io.helidon.grpc.server;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.ActiveSpanSource;
import io.opentracing.contrib.grpc.OperationNameConstructor;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.TimeUnit;

/**
 * @author jk  2018.06.12
 */
public class ClientTracingInterceptor
        implements ClientInterceptor
    {
    // ----- constructors ---------------------------------------------------

    /**
     * Private constructor called by {@link Builder}.
     *
     * @param tracer                    the Open Tracing {@link Tracer}
     * @param operationNameConstructor  the operation name constructor
     * @param streaming                 flag indicating whether to trace streaming calls
     * @param verbose                   flag to indicate verbose logging to spans
     * @param tracedAttributes          the set of request attributes to add to the span
     * @param activeSpanSource          the spurce of the active span
     */
    private ClientTracingInterceptor(Tracer                      tracer,
                                     OperationNameConstructor operationNameConstructor,
                                     boolean                     streaming,
                                     boolean                     verbose,
                                     Set<ClientRequestAttribute> tracedAttributes,
                                     ActiveSpanSource activeSpanSource)
        {
        f_tracer                   = tracer;
        f_operationNameConstructor = operationNameConstructor;
        f_fStreaming               = streaming;
        f_fVerbose                 = verbose;
        f_setTracedAttributes      = tracedAttributes;
        f_activeSpanSource         = activeSpanSource;
        }

    // ----- ClientTracingInterceptor methods -------------------------------

    /**
     * Use this interceptor to trace all requests made by this client channel.
     *
     * @param channel to be traced
     *
     * @return intercepted channel
     */
    public Channel intercept(Channel channel)
        {
        return ClientInterceptors.intercept(channel, this);
        }

    // ----- ClientInterceptor methods --------------------------------------

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next)
        {
        final String operationName = f_operationNameConstructor.constructOperationName(method);

        Span activeSpan = f_activeSpanSource.getActiveSpan();
        Span span       = createSpanFromParent(activeSpan, operationName);

        for (ClientRequestAttribute attr : f_setTracedAttributes)
            {
            switch (attr)
                {
                case ALL_CALL_OPTIONS:
                    span.setTag("grpc.call_options", callOptions.toString());
                    break;
                case AUTHORITY:
                    if (callOptions.getAuthority() == null)
                        {
                        span.setTag("grpc.authority", "null");
                        }
                    else
                        {
                        span.setTag("grpc.authority", callOptions.getAuthority());
                        }
                    break;
                case COMPRESSOR:
                    if (callOptions.getCompressor() == null)
                        {
                        span.setTag("grpc.compressor", "null");
                        }
                    else
                        {
                        span.setTag("grpc.compressor", callOptions.getCompressor());
                        }
                    break;
                case DEADLINE:
                    if (callOptions.getDeadline() == null)
                        {
                        span.setTag("grpc.deadline_millis", "null");
                        }
                    else
                        {
                        span.setTag("grpc.deadline_millis",
                                    callOptions.getDeadline().timeRemaining(TimeUnit.MILLISECONDS));
                        }
                    break;
                case METHOD_NAME:
                    span.setTag("grpc.method_name", method.getFullMethodName());
                    break;
                case METHOD_TYPE:
                    if (method.getType() == null)
                        {
                        span.setTag("grpc.method_type", "null");
                        }
                    else
                        {
                        span.setTag("grpc.method_type", method.getType().toString());
                        }
                    break;
                case HEADERS:
                    break;
                }
            }

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions))
            {
            @Override
            public void start(Listener<RespT> responseListener, final Metadata headers)
                {
                if (f_fVerbose)
                    {
                    span.log("Started call");
                    }

                if (f_setTracedAttributes.contains(ClientRequestAttribute.HEADERS))
                    {
                    span.setTag("grpc.headers", headers.toString());
                    }

                f_tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new TextMap()
                    {
                    @Override
                    public void put(String key, String value)
                        {
                        Metadata.Key<String> headerKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
                        headers.put(headerKey, value);
                        }

                    @Override
                    public Iterator<Map.Entry<String, String>> iterator()
                        {
                        throw new UnsupportedOperationException(
                                "TextMapInjectAdapter should only be used with Tracer.inject()");
                        }
                    });

                Listener<RespT> tracingResponseListener
                        = new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener)
                    {
                    @Override
                    public void onHeaders(Metadata headers)
                        {
                        if (f_fVerbose)
                            {
                            span.log(Collections.singletonMap("Response headers received", headers.toString()));
                            }
                        delegate().onHeaders(headers);
                        }

                    @Override
                    public void onMessage(RespT message)
                        {
                        if (f_fStreaming || f_fVerbose)
                            {
                            span.log("Response received");
                            }
                        delegate().onMessage(message);
                        }

                    @Override
                    public void onClose(Status status, Metadata trailers)
                        {
                        if (f_fVerbose)
                            {
                            if (status.getCode().value() == 0)
                                {
                                span.log("Call closed");
                                }
                            else
                                {
                                String desc = String.valueOf(status.getDescription());

                                span.log(Collections.singletonMap("Call failed", desc));
                                }
                            }
                        span.finish();
                        
                        delegate().onClose(status, trailers);
                        }
                    };

                delegate().start(tracingResponseListener, headers);
                }

            @Override
            public void cancel(String message, Throwable cause)
                {
                String errorMessage;

                errorMessage = message == null ? "Error" : message;

                if (cause == null)
                    {
                    span.log(errorMessage);
                    }
                else
                    {
                    span.log(Collections.singletonMap(errorMessage, cause.getMessage()));
                    }

                delegate().cancel(message, cause);
                }

            @Override
            public void halfClose()
                {
                if (f_fStreaming)
                    {
                    span.log("Finished sending messages");
                    }

                delegate().halfClose();
                }

            @Override
            public void sendMessage(ReqT message)
                {
                if (f_fStreaming || f_fVerbose)
                    {
                    span.log("Message sent");
                    }

                delegate().sendMessage(message);
                }
            };
        }

    // ----- helper methods -------------------------------------------------

    private Span createSpanFromParent(Span parentSpan, String operationName)
        {
        if (parentSpan == null)
            {
            return f_tracer.buildSpan(operationName).start();
            }
        else
            {
            return f_tracer.buildSpan(operationName).asChildOf(parentSpan).start();
            }
        }

    // ----- inner class: Builder -------------------------------------------

    public static Builder builder(Tracer tracer)
        {
        return new Builder(tracer);
        }

    /**
     * Builds the configuration of a ClientTracingInterceptor.
     */
    public static class Builder
        {
        /**
         * @param tracer to use for this intercepter
         *               Creates a Builder with default configuration
         */
        public Builder(Tracer tracer)
            {
            f_tracer = tracer;
            m_operationNameConstructor = OperationNameConstructor.DEFAULT;
            m_fStreaming = false;
            m_fVerbose = false;
            m_setTracedAttributes = new HashSet<>();
            m_activeSpanSource = ActiveSpanSource.GRPC_CONTEXT;
            }

        /**
         * @param operationNameConstructor to name all spans created by this intercepter
         *
         * @return this Builder with configured operation name
         */
        public ClientTracingInterceptor.Builder withOperationName(OperationNameConstructor operationNameConstructor)
            {
            m_operationNameConstructor = operationNameConstructor;
            return this;
            }

        /**
         * Logs streaming events to client spans.
         *
         * @return this Builder configured to log streaming events
         */
        public ClientTracingInterceptor.Builder withStreaming()
            {
            m_fStreaming = true;
            return this;
            }

        /**
         * @param tracedAttributes to set as tags on client spans
         *                         created by this intercepter
         *
         * @return this Builder configured to trace attributes
         */
        public ClientTracingInterceptor.Builder withTracedAttributes(ClientRequestAttribute... tracedAttributes)
            {
            m_setTracedAttributes = new HashSet<>(Arrays.asList(tracedAttributes));
            return this;
            }

        /**
         * Logs all request life-cycle events to client spans.
         *
         * @return this Builder configured to be verbose
         */
        public ClientTracingInterceptor.Builder withVerbosity()
            {
            m_fVerbose = true;
            return this;
            }

        /**
         * @param activeSpanSource that provides a method of getting the
         *                         active span before the client call
         *
         * @return this Builder configured to start client span as children
         * of the span returned by activeSpanSource.getActiveSpan()
         */
        public ClientTracingInterceptor.Builder withActiveSpanSource(ActiveSpanSource activeSpanSource)
            {
            m_activeSpanSource = activeSpanSource;
            return this;
            }

        /**
         * @return a ClientTracingInterceptor with this Builder's configuration
         */
        public ClientTracingInterceptor build()
            {
            return new ClientTracingInterceptor(f_tracer,
                                                m_operationNameConstructor,
                                                m_fStreaming,
                                                m_fVerbose,
                                                m_setTracedAttributes,
                                                m_activeSpanSource);
            }
        // ----- data members -----------------------------------------------
        
        private final Tracer f_tracer;

        private OperationNameConstructor m_operationNameConstructor;

        private boolean m_fStreaming;

        private boolean m_fVerbose;

        private Set<ClientRequestAttribute> m_setTracedAttributes;

        private ActiveSpanSource m_activeSpanSource;
        }

    // ----- data members ---------------------------------------------------

    private final Tracer f_tracer;

    private final OperationNameConstructor f_operationNameConstructor;

    private final boolean f_fStreaming;

    private final boolean f_fVerbose;

    private final Set<ClientRequestAttribute> f_setTracedAttributes;

    private final ActiveSpanSource f_activeSpanSource;
    }
