package io.helidon.webserver.jersey;

import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import io.opentracing.SpanContext;

class RequestScopedInstances {
    private static final ThreadLocal<ServerRequest> SERVER_REQUEST = new InheritableThreadLocal<>();
    private static final ThreadLocal<ServerResponse> SERVER_RESPONSE = new InheritableThreadLocal<>();
    private static final ThreadLocal<SpanContext> SPAN_CONTEXT = new InheritableThreadLocal<>();

    static void set(ServerRequest req) {
        SERVER_REQUEST.set(req);
    }

    static void set(ServerResponse res) {
        SERVER_RESPONSE.set(res);
    }

    static void set(SpanContext context) {
        SPAN_CONTEXT.set(context);
    }

    static ServerRequest request() {
        return SERVER_REQUEST.get();
    }

    static ServerResponse response() {
        return SERVER_RESPONSE.get();
    }

    static SpanContext spanContext() {
        return SPAN_CONTEXT.get();
    }
}
