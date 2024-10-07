/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.soap.ws;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.configurable.ServerThreadPoolSupplier;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.webserver.Handler;
import io.helidon.webserver.HttpException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import com.sun.xml.ws.api.server.Container;
import com.sun.xml.ws.transport.http.DeploymentDescriptorParser;
import com.sun.xml.ws.transport.http.HttpAdapter;
import com.sun.xml.ws.transport.http.ResourceLoader;
import com.sun.xml.ws.transport.http.client.HttpTransportPipe;

/**
 * Metro support.
 */
public class MetroSupport implements Service {

    private static final Logger LOGGER = Logger.getLogger(MetroSupport.class.getName());
    private static final AtomicReference<ExecutorService> DEFAULT_THREAD_POOL = new AtomicReference<>();
    private final ExecutorService service;
    private final HelidonContainer container;
    private final List<HelidonAdapter> adapterList;
    private final boolean publishStatusPage;
    private final String rootCtx;

    private MetroSupport(Builder builder) {
        rootCtx = builder.webContext;
        //we want listing on the "root" context instead of
        //for GET on the endpoint URL
        publishStatusPage = builder.publishStatusPage;
        HttpAdapter.setPublishStatus(false);

        HttpAdapter.setDump(builder.dumpService);
        HttpTransportPipe.setDump(builder.dumpClient);
        HttpAdapter.setDumpThreshold(builder.dumpTreshold);

        List<HelidonAdapter> e = Collections.emptyList();
        container = new HelidonContainer(rootCtx);
        try {
            e = parseEndpoints(builder.dd,
                    new HelidonResourceLoader(builder.catalog, builder.loadcustomschema),
                    container);
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, null, ioe);
        }
        adapterList = e;
        if (adapterList.isEmpty()) {
            LOGGER.warning("No XML Web Services were recognized.");
        }
        service = Contexts.wrap(getDefaultThreadPool(null));
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get(rootCtx, this::publishStatusPage);
        for (HelidonAdapter ha : adapterList) {
            rules.any(ha.getServiceContextPath(), new MetroHandler(ha));
//            rules.any(webContext + ha.urlPattern, new MetroHandler(ha));
        }
    }

    private void publishStatusPage(ServerRequest req, ServerResponse res) {
        if (!publishStatusPage) {
            throw new HttpException("Listing available web services is forbidden", Http.Status.FORBIDDEN_403);
        }
        res.status(Http.Status.OK_200);
        res.headers().contentType(MediaType.TEXT_HTML.withCharset(StandardCharsets.UTF_8.name()));
        res.send(WSUtils.writeWebServicesHtmlPage(WSUtils.getBaseUri(req), container.getBoundEndpoints()));
    }

    private List<HelidonAdapter> parseEndpoints(String dd, ResourceLoader loader, Container container) throws IOException {
        DeploymentDescriptorParser<HelidonAdapter> parser = new DeploymentDescriptorParser<>(
                Thread.currentThread().getContextClassLoader(),
                loader,
                container,
                new HelidonAdapterList());
        URL cfg = loader.getResource(dd);
        try (InputStream is = cfg.openStream()) {
            return parser.parse(cfg.toExternalForm(), is);
        }
    }

    private static synchronized ExecutorService getDefaultThreadPool(Config config) {
        if (DEFAULT_THREAD_POOL.get() == null) {
//            Config executorConfig = config.get("executor-service");
            DEFAULT_THREAD_POOL.set(ServerThreadPoolSupplier.builder()
                    .name("server")
                    //                                            .config(executorConfig)
                    .build()
                    .get());
        }
        return DEFAULT_THREAD_POOL.get();
    }

    /**
     * Get a builder to configure {@code MetroSupport} instance.
     *
     * @return fluent API builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new {@code MetroSupport} with default configuration.
     *
     * @return {@code MetroSupport} in its default configuration
     */
    public static MetroSupport create() {
        return builder().build();
    }

    /**
     * Create a new {@code MetroSupport} configured from provided {@code config}.
     *
     * @param config {@code MetroSupport} configuration
     *
     * @return {@code MetroSupport} in with options defined in provided {@code config}
     */
    public static MetroSupport create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Builder for convenient way to create {@link MetroSupport}.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, MetroSupport> {

        private String dd = "sun-jaxws.xml";
        private boolean loadcustomschema = false;
        private String wsdlRoot = "WEB-INF/wsdl";
        private String catalog = "metro-catalog.xml";
        private String webContext = "/metro";
        private boolean publishStatusPage = true;
        private boolean dumpClient = false;
        private boolean dumpService = false;
        private int dumpTreshold = 4096;

        private Builder() {
        }

        /**
         * XML Catalog to use.
         *
         * @param catalog catalog resource to use, defaults to {@code metro-catalog.xml}
         * @return updated builder instance
         */
        public Builder catalog(String catalog) {
            this.catalog = catalog;
            return this;
        }

        /**
         * Update this builder from configuration.
         *
         * @param config node located on this component's configuration
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("catalog").asString().ifPresent(this::catalog);
            config.get("descriptor").asString().ifPresent(this::descriptor);
            config.get("loadcustomschema").asBoolean().ifPresent(this::loadcustomschema);
//            config.get("dump-client").asBoolean().ifPresent(this::dumpClient);
//            config.get("dump-service").asBoolean().ifPresent(this::dumpService);
            config.get("dump").asString().ifPresent((value) -> {
                switch (value) {
                    case "service" :
                        dumpService(true);
                        break;
                    case "client":
                        dumpClient(true);
                        break;
                    case "all":
                        dumpClient(true);
                        dumpService(true);
                        break;
                    case "none":
                        dumpClient(false);
                        dumpService(false);
                        break;
                    default:
                        throw new IllegalArgumentException(
                                String.format("(all|none|client|service) allowed but was: ''{0}'", value));
                }
            });
            config.get("dump-treshold").asInt().ifPresent(this::dumpTreshold);
            config.get("status-page").asBoolean().ifPresent(this::publishStatusPage);
            config.get("web-context").asString().ifPresent(this::webContext);
//            config.get("wsdl-root").asString().ifPresent(this::wsdlRoot);

            return this;
        }

        /**
         * XML Descriptor to use.
         *
         * @param descriptor descriptor to use, defaults to {@code sun-jaxws.xml}
         * @return updated builder instance
         */
        public Builder descriptor(String descriptor) {
            dd = descriptor;
            return this;
        }

        /**
         * Dump request to web service client and its response to {@code System.out}.
         *
         * @param enabled whether to dump request/response to {@code System.out} (defaults to {@code false})
         * @return updated builder instance
         */
        public Builder dumpClient(boolean enabled) {
            dumpClient = enabled;
            return this;
        }

        /**
         * Dump request to web service and its response to {@code System.out}.
         *
         * @param enabled whether to dump request/response to {@code System.out} (defaults to {@code false})
         * @return updated builder instance
         */
        public Builder dumpService(boolean enabled) {
            dumpService = enabled;
            return this;
        }

        /**
         * Request/response message dump treshold.
         *
         * @param treshold limit (defaults to {@code 4096})
         * @return updated builder instance
         * @throws IllegalArgumentException if {@code treshold} is {@literal <}0
         */
        public Builder dumpTreshold(int treshold) {
            if (treshold < 0) {
                throw new IllegalArgumentException("'treshold' must be positive number, was '" + treshold + "'");
            }
            this.dumpTreshold = treshold;
            return this;
        }

//        JAX-WS RI currently relies on the exact "WEB-INF/wsdl" and "/WEB-INF/wsdl/" paths
//        public Builder wsdlRoot(String root) {
//            wsdlRoot = root;
//            return this;
//        }

        /**
         * Publishing status page can be disabled by invoking this method.
         *
         * @param enabled whether to enable status page (defaults to {@code true})
         * @return updated builder instance
         */
        public Builder publishStatusPage(boolean enabled) {
            publishStatusPage = enabled;
            return this;
        }

        /**
         * It will load every schema found in the resource path.
         *
         * @param enabled whether it should search for all resources (defaults to {@code false})
         * @return updated builder instance
         */
        public Builder loadcustomschema(boolean enabled) {
            loadcustomschema = enabled;
            return this;
        }

        /**
         * Path under which to register metro services on the web server.
         *
         * @param path webContext to use (defaults to {@code ""}
         * @return updated builder instance
         */
        public Builder webContext(String path) {
            webContext = path.startsWith("/") ? path : "/" + path;
            return this;
        }

        @Override
        public MetroSupport build() {
            return new MetroSupport(this);
        }
    }

    private class MetroHandler implements Handler {

        private final HelidonAdapter adapter;

        private MetroHandler(HelidonAdapter ha) {
            this.adapter = ha;
            LOGGER.log(Level.INFO, "Published endpoint: {0} on {1}", new Object[] {adapter.getName(), adapter.urlPattern});
        }

        @Override
        public void accept(ServerRequest req, ServerResponse res) {
            Context parent = Contexts.context()
                    .orElseThrow(() -> new IllegalStateException("Context must be propagated from server"));
            Context metroContext = Context.create(parent);

            Object runInContext = Contexts.runInContext(metroContext, () -> doAccept(req, res));
        }

        private Object doAccept(ServerRequest req, ServerResponse res) {
            service.execute(() -> {
                adapter.handle(req, res);
            });
            return null;
        }

    }
}
