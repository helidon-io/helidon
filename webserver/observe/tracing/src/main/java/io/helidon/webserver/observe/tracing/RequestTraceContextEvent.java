/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.webserver.observe.tracing;

import io.helidon.tracing.SpanContext;

import jdk.jfr.Category;
import jdk.jfr.Contextual;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;

@Name("io.helidon.RequestTraceContext")
@Label("HTTP Request Trace Context")
@Category({"Helidon", "HTTP Server"})
@Description("Existing tracing context during synchronous downstream request processing on this thread")
@Enabled(true)
@Threshold("0 ms")
@StackTrace(false)
final class RequestTraceContextEvent extends Event {
    private static final EventType TYPE = EventType.getEventType(RequestTraceContextEvent.class);
    private static final int MAX_TRACE_ID_LENGTH = 128;

    @Label("Trace ID")
    @Description("Literal ID of the existing tracing context, omitted if absent or longer than 128 characters")
    @Contextual
    private String traceId;

    @Label("Origin")
    @Description("serverSpan for a started server span, or inboundContext when the server span is disabled")
    private String origin;

    static RequestTraceContextEvent begin(SpanContext context, String origin) {
        if (!TYPE.isEnabled() || context == null) {
            return null;
        }
        String traceId = context.traceId();
        if (traceId == null || traceId.length() > MAX_TRACE_ID_LENGTH || traceId.isBlank() || "no-op".equals(traceId)) {
            return null;
        }
        var event = new RequestTraceContextEvent();
        event.traceId = traceId;
        event.origin = origin;
        event.begin();
        return event;
    }
}
