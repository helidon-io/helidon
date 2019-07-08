/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.Path;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;

import io.helidon.tracing.jersey.AbstractTracingFilter;

import io.opentracing.Tracer;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.opentracing.Traced;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.ResourceMethod;

/**
 * Adds tracing of Jersey calls using a post-matching filter.
 *  Microprofile Opentracing implementation.
 */
@ConstrainedTo(RuntimeType.SERVER)
@Priority(Integer.MIN_VALUE + 5)
@ApplicationScoped
public class MpTracingFilter extends AbstractTracingFilter {
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
        return findTraced(context)
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
        return findTraced(context)
                .map(Traced::operationName)
                .filter(str -> !str.isEmpty())
                .orElseGet(() -> utils.operationName(context));
    }

    @Override
    protected void configureSpan(Tracer.SpanBuilder spanBuilder) {

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
                hostHeader = hostHeader.replace("127.0.0.1", "localhost");
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

    private Optional<Traced> findTraced(ContainerRequestContext requestContext) {
        Class<?> definitionClass = getDefinitionClass(resourceInfo.getResourceClass());
        ExtendedUriInfo uriInfo = (ExtendedUriInfo) requestContext.getUriInfo();
        Method definitionMethod = getDefinitionMethod(requestContext, uriInfo);

        if (definitionMethod == null) {
            return Optional.empty();
        }

        Traced annotation = definitionMethod.getAnnotation(Traced.class);
        if (null != annotation) {
            return Optional.of(annotation);
        }

        return Optional.ofNullable(definitionClass.getAnnotation(Traced.class));
    }

    private Method getDefinitionMethod(ContainerRequestContext requestContext, ExtendedUriInfo uriInfo) {
        ResourceMethod matchedResourceMethod = uriInfo.getMatchedResourceMethod();
        Invocable invocable = matchedResourceMethod.getInvocable();
        return invocable.getDefinitionMethod();
    }

    // taken from org.glassfish.jersey.server.model.internal.ModelHelper#getAnnotatedResourceClass
    private Class<?> getDefinitionClass(Class<?> resourceClass) {
        Class<?> foundInterface = null;

        // traverse the class hierarchy to find the annotation
        // According to specification, annotation in the super-classes must take precedence over annotation in the
        // implemented interfaces
        Class<?> cls = resourceClass;
        do {
            if (cls.isAnnotationPresent(Path.class)) {
                return cls;
            }

            // if no annotation found on the class currently traversed, check for annotation in the interfaces on this
            // level - if not already previously found
            if (foundInterface == null) {
                for (final Class<?> i : cls.getInterfaces()) {
                    if (i.isAnnotationPresent(Path.class)) {
                        // store the interface reference in case no annotation will be found in the super-classes
                        foundInterface = i;
                        break;
                    }
                }
            }

            cls = cls.getSuperclass();
        } while (cls != null);

        if (foundInterface != null) {
            return foundInterface;
        }

        return resourceClass;
    }
}
