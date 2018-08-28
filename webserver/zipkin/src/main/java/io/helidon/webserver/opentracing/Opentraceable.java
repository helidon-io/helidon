/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.opentracing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.webserver.ServerRequest;

import io.opentracing.Tracer;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.server.ClientBinding;

/**
 * The Opentraceable provides JAX-RS {@link javax.ws.rs.client.Client} Zipkin integration. The resulting
 * {@link javax.ws.rs.client.Client} or {@link javax.ws.rs.client.WebTarget} propagates the OpenTracing
 * context from the {@link ServerRequest} to the remote application.
 * <p>
 * The idea is that
 * <ol>
 *     <li>the {@link javax.ws.rs.client.Client} needs to have registered {@link OpentracingClientFilter} client filter</li>
 *     <li>the tracing context is provided by
 *         <ol>
 *             <li>
 *                 the {@link Tracer} registered as property {@link OpentracingClientFilter#TRACER_PROPERTY_NAME} on the client
 *             </li>
 *             <li>
 *                 additionally, an optional parent {@link io.opentracing.SpanContext} registered as property
 *                 {@link OpentracingClientFilter#CURRENT_SPAN_CONTEXT_PROPERTY_NAME}
 *             </li>
 *         </ol>
 *     </li>
 *     <li>when in a context of a server request, it is also possible to provide {@link ServerRequest} as a property
 *     {@link OpentracingClientFilter#SERVER_REQUEST_PROPERTY_NAME} from where the tracing context is injected</li>
 * </ol>
 * Usage:
 * <pre><code>
 * // Create the JAX-RS client with registered OpentracingClientFilter:
 * Client client = ClientBuilder.newClient(new ClientConfig(OpentracingClientFilter.class));
 *
 * // Provide the tracing context; i.e, 'tracer' and optionally 'parentSpan' to the client call:
 * Tracer tracer;
 * Span parentSpan;
 *
 * Response response = client.target("http://localhost:9080")
 *                           .property(OpentracingClientFilter.TRACER_PROPERTY_NAME, tracer)
 *                           .property(OpentracingClientFilter.CURRENT_SPAN_PROPERTY_NAME, parentSpan)
 *                           .request()
 *                           .get();
 * </code></pre>
 * When using a managed client, the filter gets registered thanks to the {@link Opentraceable} annotation:
 * <pre><code>
 * class MyResource {
 *     {@literal @}Inject ServerRequest request;
 *
 *     {@literal @}GET
 *      public String getText({@literal @}Uri("http://remote.server.my:9080") {@literal @}Opentraceable WebTarget target) {
 *          Response response = target.property(OpentracingClientFilter.SERVER_REQUEST_PROPERTY_NAME, request)
 *                                    .request()
 *                                    .get();
 *     }
 * }
 * </code></pre>
 *
 * @see OpentracingClientFilter
 */
@ClientBinding(configClass = Opentraceable.Config.class)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface Opentraceable {

    /** Client config with registered {@link OpentracingClientFilter}. */
    class Config extends ClientConfig {

        /** Creates the client config with registered {@link OpentracingClientFilter}. */
        @SuppressWarnings("checkstyle:RedundantModifier")
        public Config() {
            this.register(new OpentracingClientFilter());
        }
    }

}
