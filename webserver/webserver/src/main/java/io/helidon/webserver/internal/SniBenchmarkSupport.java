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

package io.helidon.webserver.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.channels.Channels;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.tls.Tls;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.SniContext;

/**
 * Internal access used by in-repository SNI benchmarks.
 * <p>
 * The production SNI registry and ClientHello reader are package-private implementation details. This class keeps the
 * benchmark module out of the {@code io.helidon.webserver} package while still allowing it to measure those hot paths.
 */
@Api.Internal
public final class SniBenchmarkSupport {
    private static final MethodHandle REGISTRY_CREATE;
    private static final MethodHandle REGISTRY_SELECT;
    private static final MethodHandle REGISTRY_SELECT_WITHOUT_SNI;
    private static final MethodHandle SELECTION_SNI_CONTEXT;
    private static final MethodHandle CLIENT_HELLO_READ;
    private static final MethodHandle CLIENT_HELLO_SNI_HOST;

    static {
        try {
            Class<?> registryType = Class.forName("io.helidon.webserver.VirtualHostRegistry");
            Class<?> selectionType = Class.forName("io.helidon.webserver.VirtualHostRegistry$Selection");
            Class<?> readerType = Class.forName("io.helidon.webserver.ClientHelloPrefaceReader");
            Class<?> prefaceType = Class.forName("io.helidon.webserver.ClientHelloPrefaceReader$ClientHelloPreface");

            REGISTRY_CREATE = unreflect(registryType, "create", String.class, ListenerConfig.class, Tls.class);
            REGISTRY_SELECT = unreflect(registryType, "select", String.class);
            REGISTRY_SELECT_WITHOUT_SNI = unreflect(registryType, "selectWithoutSni");
            SELECTION_SNI_CONTEXT = unreflect(selectionType, "sniContext");
            CLIENT_HELLO_READ = unreflect(readerType, "read", java.nio.channels.ReadableByteChannel.class);
            CLIENT_HELLO_SNI_HOST = unreflect(prefaceType, "sniHost");
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private SniBenchmarkSupport() {
    }

    /**
     * Create an internal SNI virtual-host registry.
     *
     * @param socketName     socket name
     * @param listenerConfig listener configuration
     * @param defaultTls     fallback listener TLS
     * @return registry handle
     */
    public static Registry registry(String socketName, ListenerConfig listenerConfig, Tls defaultTls) {
        try {
            Object delegate = REGISTRY_CREATE.invoke(Objects.requireNonNull(socketName),
                                                    Objects.requireNonNull(listenerConfig),
                                                    Objects.requireNonNull(defaultTls));
            return new Registry(delegate);
        } catch (Throwable e) {
            throw rethrow(e);
        }
    }

    /**
     * Select SNI context for a presented host.
     *
     * @param registry registry handle
     * @param sniHost  normalized SNI host
     * @return selected SNI context
     */
    public static SniContext select(Registry registry, String sniHost) {
        try {
            Object selection = REGISTRY_SELECT.invoke(Objects.requireNonNull(registry).delegate,
                                                      Objects.requireNonNull(sniHost));
            return (SniContext) SELECTION_SNI_CONTEXT.invoke(selection);
        } catch (Throwable e) {
            throw rethrow(e);
        }
    }

    /**
     * Select SNI context for a connection without a presented SNI host.
     *
     * @param registry registry handle
     * @return selected SNI context
     */
    public static SniContext selectWithoutSni(Registry registry) {
        try {
            Object selection = REGISTRY_SELECT_WITHOUT_SNI.invoke(Objects.requireNonNull(registry).delegate);
            return (SniContext) SELECTION_SNI_CONTEXT.invoke(selection);
        } catch (Throwable e) {
            throw rethrow(e);
        }
    }

    /**
     * Read in-memory ClientHello bytes and select the corresponding SNI context.
     * <p>
     * This helper intentionally measures ClientHello parser and virtual-host registry costs only. It does not exercise
     * the production {@code SocketChannel} pre-read timeout, selector registration, or blocking-mode restore path.
     *
     * @param clientHello TLS ClientHello record bytes
     * @param registry    registry handle
     * @return selected SNI context
     * @throws IOException if the ClientHello bytes cannot be read
     */
    public static SniContext readParserAndSelect(byte[] clientHello, Registry registry) throws IOException {
        var channel = Channels.newChannel(new ByteArrayInputStream(Objects.requireNonNull(clientHello)));
        try {
            Object preface = CLIENT_HELLO_READ.invoke(channel);
            Optional<?> sniHost = (Optional<?>) CLIENT_HELLO_SNI_HOST.invoke(preface);
            Object selection = sniHost.isPresent()
                    ? REGISTRY_SELECT.invoke(Objects.requireNonNull(registry).delegate, sniHost.get())
                    : REGISTRY_SELECT_WITHOUT_SNI.invoke(Objects.requireNonNull(registry).delegate);
            return (SniContext) SELECTION_SNI_CONTEXT.invoke(selection);
        } catch (Throwable e) {
            Throwable cause = unwrap(e);
            if (cause instanceof IOException ioe) {
                throw ioe;
            }
            throw rethrow(cause);
        }
    }

    private static MethodHandle unreflect(Class<?> type, String name, Class<?>... parameters)
            throws ReflectiveOperationException {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return MethodHandles.lookup().unreflect(method);
    }

    private static RuntimeException rethrow(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof RuntimeException re) {
            return re;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof java.lang.reflect.InvocationTargetException invocation
                ? invocation.getCause()
                : throwable;
    }

    /**
     * Handle to an internal virtual-host registry.
     */
    @Api.Internal
    public static final class Registry {
        private final Object delegate;

        private Registry(Object delegate) {
            this.delegate = delegate;
        }
    }
}
