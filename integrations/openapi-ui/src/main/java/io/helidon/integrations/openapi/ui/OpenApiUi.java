/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.openapi.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.LazyValue;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.openapi.OpenApiService;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.staticcontent.StaticContentService;

import io.smallrye.openapi.ui.IndexHtmlCreator;
import io.smallrye.openapi.ui.Option;

/**
 * An {@link OpenApiService} that serves OpenApi UI.
 */
@RuntimeType.PrototypedBy(OpenApiUiConfig.class)
public final class OpenApiUi implements OpenApiService, RuntimeType.Api<OpenApiUiConfig> {

    /**
     * Returns a new builder.
     *
     * @return new builder
     */
    public static OpenApiUiConfig.Builder builder() {
        return OpenApiUiConfig.builder();
    }

    /**
     * Create a new instance with default configuration.
     *
     * @return new instance
     */
    public static OpenApiUi create() {
        return builder().build();
    }

    /**
     * Create a new instance from typed configuration.
     *
     * @param config typed configuration
     * @return new instance
     */
    static OpenApiUi create(OpenApiUiConfig config) {
        return new OpenApiUi(config);
    }

    /**
     * Create a new instance with custom configuration.
     *
     * @param builderConsumer consumer of configuration builder
     * @return new instance
     */
    public static OpenApiUi create(Consumer<OpenApiUiConfig.Builder> builderConsumer) {
        OpenApiUiConfig.Builder b = OpenApiUiConfig.builder();
        builderConsumer.accept(b);
        return b.build();
    }

    private static final String LOGO_RESOURCE = "logo.svg";
    private static final String HELIDON_IO_LINK = "https://helidon.io";

    private static final MediaType[] ACCEPTED_MEDIA_TYPES = new MediaType[] {
            MediaTypes.APPLICATION_JSON,
            MediaTypes.TEXT_YAML,
            MediaTypes.TEXT_PLAIN,
            MediaTypes.TEXT_HTML,
    };

    private static final HttpMediaType TEXT_HTML = HttpMediaType.create(MediaTypes.TEXT_HTML);

    private static final Map<Option, String> HELIDON_OPTIONS = Map.of(
            Option.title, "Helidon OpenAPI UI",
            Option.logoHref, LOGO_RESOURCE,
            // workaround for a bug in IndexHtmlCreator
            Option.oauth2RedirectUrl, "-",
            // link applied to the rendered logo image
            Option.backHref, HELIDON_IO_LINK,
            // link applied to the title if there is no logo (but there is; set this anyway)
            Option.selfHref, HELIDON_IO_LINK);

    private final LazyValue<byte[]> indexHtml = LazyValue.create(this::createIndexHtml);
    private final OpenApiUiConfig config;
    private volatile String docPath = "/openapi";
    private volatile String uiPath = "/openapi/ui";

    OpenApiUi(OpenApiUiConfig config) {
        this.config = config;
    }

    @Override
    public OpenApiUiConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return "openapi-ui";
    }

    @Override
    public String type() {
        return "openapi-ui";
    }

    @Override
    public boolean supports(ServerRequestHeaders headers) {
        return headers.bestAccepted(ACCEPTED_MEDIA_TYPES)
                .map(TEXT_HTML::test)
                .orElse(false);
    }

    @Override
    public void setup(HttpRules rules, String docPath, Function<MediaType, String> content) {
        if (!config.isEnabled()) {
            return;
        }
        this.docPath = docPath;
        this.uiPath = config.webContext().orElseGet(() -> docPath + "/ui");
        rules.get(docPath + "[/]", (req, res) -> handle(req, res, content))
                .get(uiPath + "[/]", this::redirectIndex)
                .get(uiPath + "/index.html", this::index)
                .register(uiPath, StaticContentService.create("helidon-openapi-ui"))
                .register(uiPath, StaticContentService.create("META-INF/resources/openapi-ui"));
    }

    private void index(ServerRequest req, ServerResponse res) {
        req.headers()
                .bestAccepted(ACCEPTED_MEDIA_TYPES)
                .filter(TEXT_HTML::test)
                .ifPresentOrElse(ct -> {
                    res.headers().contentType(ct);
                    res.send(indexHtml.get());
                }, res::next);
    }

    private void redirectIndex(ServerRequest req, ServerResponse res) {
        res.status(Status.TEMPORARY_REDIRECT_307);
        res.header(HeaderNames.LOCATION, uiPath + "/index.html");
        res.send();
    }

    private void handle(ServerRequest req, ServerResponse res, Function<MediaType, String> content) {
        req.headers()
                .bestAccepted(ACCEPTED_MEDIA_TYPES)
                .ifPresentOrElse(ct -> {
                    if (TEXT_HTML.test(ct)) {
                        redirectIndex(req, res);
                    } else {
                        res.headers().contentType(ct);
                        res.send(content.apply(ct));
                    }
                }, res::next);
    }

    private byte[] createIndexHtml() {
        Map<Option, String> options = new HashMap<>(HELIDON_OPTIONS);
        options.put(Option.url, docPath);  // location of the OpenAPI document
        config.options().forEach((k, v) -> options.put(Option.valueOf(k), v)); // user options
        try {
            return IndexHtmlCreator.createIndexHtml(options);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to initialize the index.html content for the OpenAPI UI", e);
        }
    }
}
