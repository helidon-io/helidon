/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.config.mp.MpConfig;
import io.helidon.webserver.cors.CrossOriginConfig;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.config.ConfigProvider;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * CDI extension for processing CORS-annotated types.
 * <p>
 *     Pre-computes the {@link CrossOriginConfig} for each method which should have one and makes sure that the
 *     {@link CrossOrigin} annotation appears only on methods which also have {@code OPTIONS}.
 * </p>
 */
public class CorsCdiExtension implements Extension {

    private static final Logger LOGGER = Logger.getLogger(CorsCdiExtension.class.getName());

    /**
     * Key used for retrieving CORS-related configuration from MP configuration.
     */
    static final String CORS_CONFIG_KEY = "cors";

    private Supplier<Optional<CrossOriginConfig>> supplierOfCrossOriginConfigFromAnnotation;

    private CorsSupportMp corsSupportMp;

    private final Set<Method> methodsWithCrossOriginIncorrectlyUsed = new HashSet<>();
    private final Map<Method, CrossOriginConfig> corsConfigs = new HashMap<>();
    private Class<?> clazzWithClassLevelAnnotationCrossOriginWithoutOptionMethods = null;

    void processManagedBean(@Observes ProcessManagedBean<?> pmb) {
        AnnotatedType<?> annotatedBeanClass = pmb.getAnnotatedBeanClass();

        if (annotatedBeanClass.isAnnotationPresent(CrossOrigin.class)) {
            List<AnnotatedMethod<?>> optionMethods = getOptionMethods(pmb);
            Class<?> resourceJavaClass = annotatedBeanClass.getJavaClass();

            if (optionMethods.isEmpty()) {
                clazzWithClassLevelAnnotationCrossOriginWithoutOptionMethods = resourceJavaClass;
                return;
            } else if (isEveryMethodHasOwnCrossOriginAnnotation(optionMethods)) {
                LOGGER.log(Level.FINE, String.format("%s has unnecessary class level annotation @%s. "
                                + "Every @%s method has own @%s. Method-level annotation takes precedence.",
                        resourceJavaClass.getSimpleName(),
                        CrossOrigin.class.getSimpleName(),
                        OPTIONS.class.getSimpleName(),
                        CrossOrigin.class.getSimpleName()));
            }
        }

        annotatedBeanClass.getMethods().forEach(am -> {
            Method method = am.getJavaMember();
            if (am.isAnnotationPresent(CrossOrigin.class) && !am.isAnnotationPresent(OPTIONS.class)) {
                methodsWithCrossOriginIncorrectlyUsed.add(method);
            } else {
                crossOriginConfigFromAnnotationOnAssociatedMethod(method)
                        .ifPresent(crossOriginConfig -> corsConfigs.put(method,
                                                                        crossOriginConfig));
            }
        });
    }

    void recordSupplierOfCrossOriginConfigFromAnnotation(Supplier<Optional<CrossOriginConfig>> supplier) {
        supplierOfCrossOriginConfigFromAnnotation = supplier;
    }

    void ready(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class) Object adv) {

        if (!methodsWithCrossOriginIncorrectlyUsed.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            "%s annotation is valid only on @OPTIONS methods; found incorrectly on the following methods:%n%s",
                            CrossOrigin.class.getSimpleName(),
                            methodsWithCrossOriginIncorrectlyUsed));
        }
        if (clazzWithClassLevelAnnotationCrossOriginWithoutOptionMethods != null) {
            throw new IllegalArgumentException(
                    String.format(
                            "%s has class-level annotation @%s and should have at least one @%s method",
                            clazzWithClassLevelAnnotationCrossOriginWithoutOptionMethods.getSimpleName(),
                            CrossOrigin.class.getSimpleName(),
                            OPTIONS.class.getSimpleName()));
        }

        Config corsConfig = MpConfig.toHelidonConfig(ConfigProvider.getConfig()).get(CORS_CONFIG_KEY);

        CorsSupportMp.Builder corsBuilder = CorsSupportMp.builder();
        corsConfig.ifExists(corsBuilder::mappedConfig);
        corsSupportMp = corsBuilder
                .secondaryLookupSupplier(this::deferringSecondaryLookupSupplier)
                .build();
    }

    Optional<CrossOriginConfig> crossOriginConfig(Method method) {
        return Optional.ofNullable(corsConfigs.get(method));
    }

    CorsSupportMp corsSupportMp() {
        return corsSupportMp;
    }

    private boolean isEveryMethodHasOwnCrossOriginAnnotation(List<AnnotatedMethod<?>> methods) {
        return methods
                .stream()
                .allMatch(am -> am.isAnnotationPresent(CrossOrigin.class));
    }

    private List<AnnotatedMethod<?>> getOptionMethods(ProcessManagedBean<?> pmb) {
        return pmb.getAnnotatedBeanClass().getMethods()
                .stream()
                .filter(am -> am.isAnnotationPresent(OPTIONS.class))
                .collect(Collectors.toList());
    }

    private Optional<CrossOriginConfig> deferringSecondaryLookupSupplier() {
        /*
         * The CrossOriginFilter instance is not created yet when this extension is initialized, but only the
         * filter knows the active resource method which this code uses to look up the correct CORS config.
         * To work around that, we register a deferring supplier with the CorsMpSupport.Builder (in the @Initialized observer).
         * When the filter is instantiated, it registers its own supplier with this extension. The deferring supplier
         * invokes the filter's supplier, which the filter has registered by the time we need it.
         */
        return supplierOfCrossOriginConfigFromAnnotation.get();
    }

    private Optional<CrossOriginConfig> crossOriginConfigFromAnnotationOnAssociatedMethod(Method resourceMethod) {

        /*
         * Only @OPTIONS methods should bear the @CrossOrigin annotation, but the annotation on such methods applies to
         * all methods sharing the same path.
         */
        Class<?> resourceClass = resourceMethod.getDeclaringClass();

        CrossOrigin corsAnnot;
        OPTIONS optsAnnot = resourceMethod.getAnnotation(OPTIONS.class);
        Path pathAnnot = resourceMethod.getAnnotation(Path.class);
        if (optsAnnot != null) {
            corsAnnot = getCrossOriginAnnotation(resourceMethod, resourceClass);
        } else {
            // Find the @OPTIONS method with the same path as the resource method, if any. That one might have a
            // @CrossOrigin annotation which applies to the resource method.
            Optional<Method> optionsMethod = Arrays.stream(resourceClass.getDeclaredMethods())
                    .filter(m -> {
                        OPTIONS optsAnnot2 = m.getAnnotation(OPTIONS.class);
                        Path pathAnnot2 = m.getAnnotation(Path.class);
                        if (optsAnnot2 != null) {
                            if (pathAnnot != null) {
                                return pathAnnot2 != null && pathAnnot.value()
                                        .equals(pathAnnot2.value());
                            }
                            return pathAnnot2 == null;
                        }
                        return false;
                    })
                    .findFirst();
            corsAnnot = optionsMethod.map(m -> getCrossOriginAnnotation(m, resourceClass))
                    .orElse(null);
        }
        return Optional.ofNullable(corsAnnot == null ? null : annotationToConfig(corsAnnot));
    }

    private CrossOrigin getCrossOriginAnnotation(Method resourceOptionMethod, Class<?> resourceClass) {
        return resourceOptionMethod.isAnnotationPresent(CrossOrigin.class)
                ? resourceOptionMethod.getAnnotation(CrossOrigin.class)
                : resourceClass.getAnnotation(CrossOrigin.class);
    }

    private static CrossOriginConfig annotationToConfig(CrossOrigin crossOrigin) {
        return CrossOriginConfig.builder()
                .allowOrigins(crossOrigin.value())
                .allowHeaders(crossOrigin.allowHeaders())
                .exposeHeaders(crossOrigin.exposeHeaders())
                .allowMethods(crossOrigin.allowMethods())
                .allowCredentials(crossOrigin.allowCredentials())
                .maxAgeSeconds(crossOrigin.maxAge())
                .build();
    }

}
