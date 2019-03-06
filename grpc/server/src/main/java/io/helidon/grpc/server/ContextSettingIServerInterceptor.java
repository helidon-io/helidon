package io.helidon.grpc.server;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import java.util.Map;

/**
 * @author jk  2019.03.06
 */
public class ContextSettingIServerInterceptor
        implements ServerInterceptor
    {
    private final Map<Context.Key<?>, Object> contextMap;

    public ContextSettingIServerInterceptor(Map<Context.Key<?>, Object> contextMap)
        {
        this.contextMap = contextMap;
        }

    @Override
    @SuppressWarnings("unchecked")
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next)
        {
        if (contextMap.isEmpty())
            {
            return next.startCall(call, headers);
            }
        else
            {
            Context context = Context.current();

            for (Map.Entry<Context.Key<?>, Object> entry : contextMap.entrySet())
                {
                Context.Key<Object> key = (Context.Key<Object>) entry.getKey();
                context = context.withValue(key, entry.getValue());
                }

            return Contexts.interceptCall(context, call, headers, next);
            }
        }
    }
