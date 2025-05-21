/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.openapi;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.helidon.common.LazyValue;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.BadRequestException;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Status;
import io.helidon.webserver.cors.CorsEnabledServiceHelper;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.SecureHandler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Separate feature exists so we correctly handle feature weight.
 */
class OpenApiHttpFeature implements HttpFeature {
    private static final System.Logger LOGGER = System.getLogger(OpenApiHttpFeature.class.getName());
    private static final MediaType[] PREFERRED_MEDIA_TYPES = new MediaType[] {
            MediaTypes.APPLICATION_OPENAPI_YAML,
            MediaTypes.APPLICATION_X_YAML,
            MediaTypes.APPLICATION_YAML,
            MediaTypes.APPLICATION_OPENAPI_JSON,
            MediaTypes.APPLICATION_JSON,
            MediaTypes.TEXT_X_YAML,
            MediaTypes.TEXT_YAML
    };

    private final ConcurrentMap<OpenApiFormat, String> cachedDocuments = new ConcurrentHashMap<>();
    private final OpenApiFeatureConfig config;
    private final OpenApiManager<?> manager;
    private final LazyValue<Object> model;
    private final CorsEnabledServiceHelper corsService;

    OpenApiHttpFeature(OpenApiFeatureConfig config,
                       OpenApiManager<?> manager,
                       LazyValue<Object> model,
                       CorsEnabledServiceHelper corsService) {
        this.config = config;
        this.manager = manager;
        this.model = model;
        this.corsService = corsService;
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        String path = config.webContext();
        if (!config.permitAll()) {
            routing.any(path, SecureHandler.authorize(config.roles().toArray(new String[0])));
        }
        routing.any(path, corsService.processor())
                .get(path, this::handle);
        config.services().forEach(service -> service.setup(routing, path, this::content));
    }

    @SuppressWarnings("unchecked")
    private static <T> String format(OpenApiManager<T> manager, OpenApiFormat format, Object model) {
        return manager.format((T) model, format);
    }

    private void handle(ServerRequest req, ServerResponse res) {
        String format = req.query().first("format").map(String::toLowerCase).orElse(null);
        if (format != null) {
            MediaType contentType = OpenApiFeature.SUPPORTED_FORMATS.get(format.toLowerCase());
            if (contentType == null) {
                throw new BadRequestException(String.format(
                        "Unsupported format: %s, supported formats: %s",
                        format, OpenApiFeature.SUPPORTED_FORMATS.keySet()));
            }
            res.status(Status.OK_200);
            res.header(HeaderValues.X_CONTENT_TYPE_OPTIONS_NOSNIFF)
                    .headers().contentType(contentType);
            res.send(content(contentType));
        } else {
            // check if we should delegate to a service
            for (OpenApiService service : config.services()) {
                if (service.supports(req.headers())) {
                    res.next();
                    return;
                }
            }

            HttpMediaType contentType = req.headers()
                    .bestAccepted(PREFERRED_MEDIA_TYPES)
                    .map(HttpMediaType::create)
                    .orElse(null);

            if (contentType == null) {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE, "Accepted types not supported: {0}", req.headers().acceptedTypes());
                }
                res.next();
                return;
            }

            res.status(Status.OK_200);
            res.header(HeaderValues.X_CONTENT_TYPE_OPTIONS_NOSNIFF)
                    .headers().contentType(contentType);
            res.send(content(contentType));
        }
    }

    private String content(MediaType mediaType) {
        OpenApiFormat format = OpenApiFormat.valueOf(mediaType);
        if (format == OpenApiFormat.UNSUPPORTED) {
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE, "Requested format {0} not supported", mediaType);
            }
        }
        return cachedDocuments.computeIfAbsent(format, fmt -> format(manager, fmt, model.get()));
    }

}
