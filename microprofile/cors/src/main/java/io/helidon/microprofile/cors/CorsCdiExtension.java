/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
package io.helidon.microprofile.cors;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.cors.Cors;
import io.helidon.webserver.cors.CorsFeature;
import io.helidon.webserver.cors.CorsPathConfig;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.Path;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * CDI extension for processing CORS-annotated types.
 * <p>
 * Pre-computes the {@link io.helidon.webserver.cors.CorsPathConfig} for each method which should have one and makes
 * sure that CORS annotations appear only on methods which also have {@code OPTIONS}.
 * </p>
 */
public class CorsCdiExtension implements Extension {

    /**
     * Key used for retrieving CORS-related configuration from MP configuration.
     */
    static final String CORS_CONFIG_KEY = "cors";

    private final Set<Method> methodsWithCrossOriginIncorrectlyUsed = new HashSet<>();
    private final List<CorsPathConfig> corsConfigs = new ArrayList<>();

    private Config config;

    static int endpointPathComparator(CorsPathConfig corsPathConfig, CorsPathConfig corsPathConfig1) {
        String[] firstPath = corsPathConfig.pathPattern().split("/");
        String[] secondPath = corsPathConfig1.pathPattern().split("/");

        if (firstPath.length != secondPath.length) {
            return Integer.compare(secondPath.length, firstPath.length);
        }

        int len = firstPath.length;
        for (int i = 0; i < len; i++) {
            String firstElement = firstPath[i];
            String secondElement = secondPath[i];
            if (firstElement.equals(secondElement)) {
                // same prefix, continue
                continue;
            }
            // different element on the same depth - just use alphabet
            return firstElement.compareTo(secondElement);
        }
        return 0;
    }

    void processManagedBean(@Observes ProcessManagedBean<?> pmb) {
        // we need to collect all annotated options methods, sorted by most specific path first
        // i.e. if there is /greet and /greet/{name}, /greet/{name} must be first

        List<CorsPathConfig> endpointList = new ArrayList<>();

        pmb.getAnnotatedBeanClass().getMethods().forEach(am -> {
            Method method = am.getJavaMember();
            String path = pathOfMethod(method);

            var corsConfig = corsConfig(am, path);

            if (corsConfig.isPresent()) {
                if (am.isAnnotationPresent(OPTIONS.class)) {
                    endpointList.add(corsConfig.get());
                } else {
                    methodsWithCrossOriginIncorrectlyUsed.add(method);
                }
            }
        });

        if (endpointList.isEmpty()) {
            return;
        }

        // most specific paths must be registered first
        endpointList.sort(CorsCdiExtension::endpointPathComparator);

        corsConfigs.addAll(endpointList);
    }

    void prepareRuntime(@Observes @RuntimeStart Config config) {
        this.config = config;
    }

    void ready(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class) Object adv,
               ServerCdiExtension server) {

        if (!methodsWithCrossOriginIncorrectlyUsed.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            "CORS annotations are valid only on @OPTIONS methods; found incorrectly on the following "
                                    + "methods:%n%s",
                            methodsWithCrossOriginIncorrectlyUsed));
        }

        CorsFeature corsFeature = CorsFeature.builder()
                .config(config.get(CORS_CONFIG_KEY))
                .addPaths(List.copyOf(corsConfigs))
                .build();

        server.addFeature(corsFeature);
    }

    @SuppressWarnings("removal")
    private static CorsPathConfig annotationToConfig(CrossOrigin crossOrigin, String path) {
        return CorsPathConfig.builder()
                .pathPattern(path)
                .allowOrigins(Set.of(crossOrigin.value()))
                .allowHeaders(Set.of(crossOrigin.allowHeaders()))
                .exposeHeaders(Set.of(crossOrigin.exposeHeaders()))
                .allowMethods(Set.of(crossOrigin.allowMethods()))
                .allowCredentials(crossOrigin.allowCredentials())
                .maxAge(Duration.ofSeconds(crossOrigin.maxAge()))
                .build();
    }

    private String pathOfMethod(Method javaMember) {
        var containingClass = javaMember.getDeclaringClass();
        String prefix = "/";
        if (containingClass.isAnnotationPresent(Path.class)) {
            prefix = containingClass.getAnnotation(Path.class).value();
            if (!prefix.endsWith("/")) {
                prefix = prefix + "/";
            }
        }

        if (javaMember.isAnnotationPresent(Path.class)) {
            String methodPath = javaMember.getAnnotation(Path.class).value();
            if (methodPath.equals("/") || methodPath.isEmpty()) {
                return prefix + "*";
            }
            String path = prefix + (methodPath.startsWith("/") ? methodPath.substring(1) : methodPath);
            return path + (path.endsWith("/") ? "*" : "/*");
        } else {
            return prefix + "*";
        }
    }

    @SuppressWarnings("removal")
    private Optional<CorsPathConfig> corsConfig(AnnotatedMethod<?> am, String path) {
        if (am.isAnnotationPresent(CrossOrigin.class)) {
            return Optional.of(annotationToConfig(am.getAnnotation(CrossOrigin.class), path));
        }
        boolean found = false;
        var builder = CorsPathConfig.builder()
                .pathPattern(path);

        if (am.isAnnotationPresent(Cors.Defaults.class)) {
            // use all defaults
            return Optional.of(builder.build());
        }

        if (am.isAnnotationPresent(Cors.AllowOrigins.class)) {
            found = true;
            builder.allowOrigins(Set.of(am.getAnnotation(Cors.AllowOrigins.class).value()));
        }
        if (am.isAnnotationPresent(Cors.AllowHeaders.class)) {
            found = true;
            builder.allowHeaders(Set.of(am.getAnnotation(Cors.AllowHeaders.class).value()));
        }
        if (am.isAnnotationPresent(Cors.AllowMethods.class)) {
            found = true;
            builder.allowMethods(Set.of(am.getAnnotation(Cors.AllowMethods.class).value()));
        }
        if (am.isAnnotationPresent(Cors.ExposeHeaders.class)) {
            found = true;
            builder.exposeHeaders(Set.of(am.getAnnotation(Cors.ExposeHeaders.class).value()));
        }
        if (am.isAnnotationPresent(Cors.MaxAgeSeconds.class)) {
            found = true;
            builder.maxAge(Duration.ofSeconds(am.getAnnotation(Cors.MaxAgeSeconds.class).value()));
        }
        if (am.isAnnotationPresent(Cors.AllowCredentials.class)) {
            found = true;
            builder.allowCredentials(am.getAnnotation(Cors.AllowCredentials.class).value());
        }

        if (found) {
            return Optional.of(builder.build());
        } else {
            return Optional.empty();
        }
    }

}
