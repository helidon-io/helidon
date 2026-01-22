/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.websocket;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.service.registry.Service;

/**
 * Container class for WebSocket related annotations that are shared by client and server.
 * <p>
 * Each annotated method can have an unqualified parameter {@link io.helidon.websocket.WsSession}.
 */
public class WebSocket {
    /**
     * A message listener method. There can be maximally two methods on a service with this annotation, and they must
     * use different message type (i.e. one method max for binary, and one method max for string messages).
     * <p>
     * The following parameters are supported for any kind of message (binary and text):
     * <ul>
     *     <li>{@code boolean} - without any qualifier, determining whether the message is the last one (optional,
     *      if not specified, message is always delivered in full); this parameter must be the LAST parameter of the method.</li>
     * </ul>
     *
     * Binary messages can have the following parameters type (MUST have one of these):
     * <ul>
     *     <li>{@link io.helidon.common.buffers.BufferData}</li>
     *     <li>{@link java.nio.ByteBuffer}</li>
     *     <li>{@code byte[]}</li>
     *     <li>{@link java.io.InputStream} - this method will be invoked on a separate virtual thread</li>
     * </ul>
     *
     * Text messages can have the following parameter type (MUST have one of these):
     * <ul>
     *     <li>{@link java.lang.String}</li>
     *     <li>{@link java.io.Reader} - this method will be invoked on a separate virtual thread</li>
     *     <li>non-boolean primitive type</li>
     * </ul>
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    @Service.Singleton
    public @interface OnMessage {
    }

    /**
     * A method invoked when the websocket connection is established.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    @Service.Singleton
    public @interface OnOpen {
    }

    /**
     * A method invoked when the websocket communication encounters an error.
     * This method may have a {@link java.lang.Throwable} parameter.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    @Service.Singleton
    public @interface OnError {
    }

    /**
     * A method invoked when the websocket connection is closed.
     * This method may have an {@code int} parameter that will receive the close code,
     * and a {@link java.lang.String} parameter that will receive the close reason.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    @Service.Singleton
    public @interface OnClose {
    }

    /**
     * A method invoked during HTTP to WebSocket upgrade.
     * <p>
     * The annotated method may have the following parameters (only):
     * <ul>
     *     <li>{@link io.helidon.http.HttpPrologue} - request prologue</li>
     *     <li>{@link io.helidon.http.Headers} - request headers</li>
     *     <li>Parameter(s) qualified with {@link io.helidon.http.Http.PathParam}</li>
     * </ul>
     *
     * The annotated method MUST return either {@code void}, or {@link io.helidon.http.Headers}
     * or {@link java.util.Optional} of {@link io.helidon.http.Headers} that will provide
     * the headers to be returned during upgrade. The returned value must not be null.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Inherited
    @Service.Singleton
    public @interface OnHttpUpgrade {
    }
}
