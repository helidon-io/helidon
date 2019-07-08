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
package io.helidon.tracing.jersey;

import java.lang.reflect.Method;
import java.util.function.Function;

import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;

import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.ResourceMethod;

/**
 * Utilities for tracing in helidon.
 */
public class TracingHelper {
    private final Function<ContainerRequestContext, String> nameFunction;

    private TracingHelper(Function<ContainerRequestContext, String> nameFunction) {
        this.nameFunction = nameFunction;
    }

    /**
     * Create helper with default span name function.
     *
     * @return tracing helper
     * @see #classMethodName(ContainerRequestContext)
     */
    public static TracingHelper create() {
        return new TracingHelper(TracingHelper::classMethodName);
    }

    /**
     * Create helper with custom span name function.
     * @param nameFunction function to get span name from context
     * @return tracing helper
     * @see #classMethodName(ContainerRequestContext)
     * @see #httpPathMethodName(ContainerRequestContext)
     */
    public static TracingHelper create(Function<ContainerRequestContext, String> nameFunction) {
        return new TracingHelper(nameFunction);
    }

    /**
     * Name is generated from path as {http-method}:{request-path}.
     *
     * @param requestContext context to extract information from
     * @return name of span to use
     */
    public static String httpPathMethodName(ContainerRequestContext requestContext) {
        Method m = getDefinitionMethod(requestContext);
        Path methodPath = m.getAnnotation(Path.class);
        Path resourcePath = m.getDeclaringClass().getAnnotation(Path.class);

        StringBuilder fullPath = new StringBuilder();
        fullPath.append(requestContext.getMethod().toUpperCase());
        fullPath.append(":");

        if (null != resourcePath) {
            String resourcePathS = resourcePath.value();
            if (!resourcePathS.startsWith("/")) {
                fullPath.append("/");
            }
            fullPath.append(resourcePath.value());
        }
        if (null != methodPath) {
            String methodPathS = methodPath.value();

            if ((fullPath.length() != 0) && (fullPath.charAt(fullPath.length() - 1) != '/') && !methodPathS.startsWith("/")) {
                fullPath.append("/");
            }
            fullPath.append(methodPath.value());
        }

        return fullPath.toString();
    }

    /**
     * Name is generated from class and method as {http-method}:{fully-qualified-class-name}.{method-name}.
     *
     * @param requestContext context to extract information from
     * @return name of span to use
     */
    public static String classMethodName(ContainerRequestContext requestContext) {
        Method m = getDefinitionMethod(requestContext);

        return requestContext.getMethod()
                + ":" + m.getDeclaringClass().getName()
                + "." + m.getName();
    }

    /**
     * The term 'definition method' used by the Jersey model means the method that contains JAX-RS/Jersey annotations.
     */
    static Method getDefinitionMethod(ContainerRequestContext requestContext) {
        if (!(requestContext.getUriInfo() instanceof ExtendedUriInfo)) {
            throw new IllegalStateException("Could not get Extended Uri Info. Incompatible version of Jersey?");
        }

        ExtendedUriInfo uriInfo = (ExtendedUriInfo) requestContext.getUriInfo();
        ResourceMethod matchedResourceMethod = uriInfo.getMatchedResourceMethod();
        Invocable invocable = matchedResourceMethod.getInvocable();
        return invocable.getDefinitionMethod();
    }

    /**
     * Generate span using the function provided by {@link #create(Function)}.
     *
     * @param context request context of the container
     * @return name of the span to use
     */
    public String generateSpanName(ContainerRequestContext context) {
        return nameFunction.apply(context);
    }
}
