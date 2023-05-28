/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tracing;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.jersey.common.InvokedResource;
import io.helidon.tracing.Span;
import io.helidon.tracing.jersey.AbstractTracingFilter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.opentracing.Traced;

/**
 * Adds tracing of Jersey calls using a post-matching filter.
 *  Microprofile Opentracing implementation.
 */
@ConstrainedTo(RuntimeType.SERVER)
@Priority(Integer.MIN_VALUE + 5)
@ApplicationScoped
public class MpTracingFilter extends AbstractTracingFilter {
    private static final Pattern LOCALHOST_PATTERN = Pattern.compile("127.0.0.1", Pattern.LITERAL);

    @Context
    private ResourceInfo resourceInfo;

    private MpTracingHelper utils;
    private Function<String, Boolean> skipPatternFunction;

    /**
     * Post construct method, initialization procedures.
     */
    @PostConstruct
    public void postConstruct() {
        this.utils = MpTracingHelper.create();
        // use skip pattern first
        Config config = ConfigProvider.getConfig();

        Optional<String> skipPattern = config.getOptionalValue("mp.opentracing.server.skip-pattern", String.class);

        this.skipPatternFunction = skipPattern.map(Pattern::compile)
                .map(pattern -> (Function<String, Boolean>) path -> pattern.matcher(path).matches())
                .orElse(path -> false);
    }

    @Override
    protected boolean tracingEnabled(ContainerRequestContext context) {
        if (skipPatternFunction.apply(addForwardSlash(context.getUriInfo().getPath()))) {
            return false;
        }
        return InvokedResource.create(context)
                .findAnnotation(Traced.class)
                .map(Traced::value)
                .orElseGet(utils::tracingEnabled);
    }

    private String addForwardSlash(String path) {
        if (path.isEmpty()) {
            return "/";
        }

        if (path.charAt(0) == '/') {
            return path;
        }

        return "/" + path;
    }

    @Override
    protected String spanName(ContainerRequestContext context) {
        return InvokedResource.create(context)
                .findAnnotation(Traced.class)
                .map(Traced::operationName)
                .filter(str -> !str.isEmpty())
                .orElseGet(() -> utils.operationName(context));
    }

    @Override
    protected void configureSpan(Span.Builder spanBuilder) {

    }

    @Override
    protected String url(ContainerRequestContext requestContext) {
        String hostHeader = requestContext.getHeaderString("host");
        URI requestUri = requestContext.getUriInfo().getRequestUri();

        if (null != hostHeader) {
            String query = requestUri.getQuery();
            if (null == query) {
                query = "";
            } else {
                if (!query.isEmpty()) {
                    query = "?" + query;
                }
            }

            if (hostHeader.contains("127.0.0.1")) {
                // TODO this is a bug in TCK tests, that expect localhost even though IP is sent
                hostHeader = LOCALHOST_PATTERN.matcher(hostHeader).replaceAll(Matcher.quoteReplacement("localhost"));
            }

            // let us use host header instead of local interface
            return requestUri.getScheme()
                    + "://"
                    + hostHeader
                    + requestUri.getPath()
                    + query;
        }

        return requestUri.toString();
    }
}
