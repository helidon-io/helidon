/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.server;

import javax.enterprise.inject.spi.CDI;

/**
 * Start a Helidon microprofile server that collects JAX-RS resources from
 * configuration or from classpath.
 * <p>
 * Uses {@code logging.properties} to configure Java logging unless a configuration is defined through
 * a Java system property. The file is expected either in the directory the application was started, or on
 * the classpath.
 * @deprecated since 2.0.0, use {@link io.helidon.microprofile.cdi.Main} instead
 */
@Deprecated
public final class Main {
    private static int port = 0;

    private Main() {
    }

    /**
     * Main method to start server. The server will collect JAX-RS application automatically (through
     * CDI extension - just annotate it with {@link javax.enterprise.context.ApplicationScoped}).
     *
     * @param args command line arguments, currently ignored
     */
    public static void main(String[] args) {
        io.helidon.microprofile.cdi.Main.main(args);

        port = CDI.current()
                .getBeanManager()
                .getExtension(ServerCdiExtension.class)
                .port();
    }

    /**
     * Once the server is started (e.g. the main method finished), the
     * server port can be obtained with this method.
     * This method will return a reasonable value only if the
     * server is started through {@link #main(String[])} method.
     * Otherwise use {@link Server#port()}.
     *
     * How to get the port in Helidon 2.0:
     * <pre>
     * port = CDI.current()
     *   .getBeanManager()
     *   .getExtension(ServerCdiExtension.class)
     *   .port();
     * </pre>
     *
     * @return port the server started on
     * @deprecated use {@link io.helidon.microprofile.server.ServerCdiExtension} to get the port
     */
    @Deprecated
    public static int serverPort() {
        return port;
    }
}
