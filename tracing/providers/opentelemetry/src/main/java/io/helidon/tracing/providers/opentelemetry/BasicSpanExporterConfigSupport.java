/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.tracing.providers.opentelemetry;

import java.time.Duration;
import java.util.function.Consumer;

class BasicSpanExporterConfigSupport {

    static void apply(BasicSpanExporterConfig configBuilder,
                      String defaultProtocol,
                      String defaultHost,
                      Integer defaultPort,
                      String defaultPath,
                      Consumer<String> doEndpoint,
                      Consumer<String> doCompression,
                      Consumer<Duration> doTimeout) {
        doEndpoint.accept(endpoint(configBuilder, defaultProtocol, defaultHost, defaultPort, defaultPath));
        configBuilder.compression().ifPresent(doCompression);
        configBuilder.timeout().ifPresent(doTimeout);
    }

    static String endpoint(BasicSpanExporterConfig exporterConfig,
                           String defaultProtocol,
                           String defaultHost,
                           Integer defaultPort,
                           String defaultPath) {

        return endpoint(exporterConfig.collectorProtocol().orElse(defaultProtocol),
                        exporterConfig.collectorHost().orElse(defaultHost),
                        exporterConfig.collectorPort().orElse(defaultPort),
                        exporterConfig.collectorPath().orElse(defaultPath));

    }

    private static String endpoint(String protocol, String host, int port, String path) {
        return protocol + (protocol.endsWith(":") ? "" : ":")
                + host
                + (port != -1
                        ? ":" + port
                        : "") + (path == null ? "" : (path.charAt(0) != '/' ? "/" : "") + path);
    }
}
