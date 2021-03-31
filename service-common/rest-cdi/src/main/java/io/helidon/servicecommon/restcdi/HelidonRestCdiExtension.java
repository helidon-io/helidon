/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.servicecommon.restcdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.interceptor.Interceptor;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.servicecommon.rest.HelidonRestServiceSupport;
import io.helidon.webserver.Routing;

import org.eclipse.microprofile.config.ConfigProvider;

import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * Abstract superclass of service-specific, REST-based CDI extensions.
 * <p>
 *     This class implements a substantial amount of the work many extensions must do to process
 *     annotated types for REST-based services.
 * </p>
 * <p>
 *     Each CDI extension is presumed to layer on an SE-style service support class which itself is a subclass of
 *     {@link HelidonRestServiceSupport} with an associated {@code Builder} class. The service support base class and its
 *     builder are both type parameters to this class.
 * </p>
 * <p>
 *     Each concrete implementation should:
 *     <ul>
 *         <li>Invoke {@link #recordAnnotatedType} for each class which bears an annotation of interest to the
 *         extension, often from a {@code ProcessAnnotatedType} observer method.</li>
 *         <li>Implement {@link #processManagedBean(ProcessManagedBean)} which this base class invokes to notify the
 *         implementation class of each managed bean type that was reported by the concrete extension but not vetoed by some
 *         other extension. Each extension can interpret "process" however it needs to. Metrics, for example, creates
 *         metrics and registers them with the appropriate metrics registry.</li>
 *     </ul>
 *
 * @param <T> concrete type of {@code HelidonRestServiceSupport} used
 * @param <B> Builder for the concrete type of {@code }HelidonRestServiceSupport}
 */
public abstract class HelidonRestCdiExtension<
        T extends HelidonRestServiceSupport,
        B extends HelidonRestServiceSupport.Builder<T, B>> implements Extension {

    private final Map<Bean<?>, AnnotatedMember<?>> producers = new HashMap<>();

    private final Set<Class<?>> annotatedClasses = new HashSet<>();
    private final Set<Class<?>> annotatedClassesProcessed = new HashSet<>();

    private final Logger logger;
    private final Function<Config, T> serviceSupportFactory;
    private final String configPrefix;

    private T serviceSupport = null;

    /**
     * Common initialization for concrete implementations.
     *
     * @param logger                Logger instance to use for logging messages
     * @param serviceSupportFactory function from config to the corresponding SE-style service support object
     * @param configPrefix          prefix for retrieving config related to this extension
     */
    protected HelidonRestCdiExtension(
            Logger logger,
            Function<Config, T> serviceSupportFactory,
            String configPrefix) {
        this.logger = logger;
        this.serviceSupportFactory = serviceSupportFactory;
        this.configPrefix = configPrefix;
    }

    protected Set<Class<?>> annotatedClasses() {
        return annotatedClasses;
    }

    protected Set<Class<?>> annotatedClassesProcessed() {
        return annotatedClassesProcessed;
    }

    /**
     * Cleans up any data structures created during annotation processing but which are not needed once the CDI container has
     * started.
     *
     * @param adv the {@code AfterDeploymentValidation} event
     */
    protected void clearAnnotationInfo(@Observes AfterDeploymentValidation adv) {
        if (logger.isLoggable(Level.FINE)) {
            Set<Class<?>> annotatedClassesIgnored = new HashSet<>(annotatedClasses());
            annotatedClassesIgnored.removeAll(annotatedClassesProcessed());
            if (!annotatedClassesIgnored.isEmpty()) {
                logger.log(Level.FINE, () ->
                        "Classes originally found with selected annotations that were not processed, probably "
                                + "because they were vetoed:" + annotatedClassesIgnored.toString());
            }
        }
        annotatedClasses.clear();
        annotatedClassesProcessed.clear();
    }

    /**
     * Observes all managed beans but immediately dismisses ones for which the Java class was not previously noted by the {@code
     * ProcessAnnotatedType} observer (which recorded only classes with selected annotations).
     *
     * @param pmb event describing the managed bean being processed
     */
    protected void observeManagedBeans(@Observes ProcessManagedBean<?> pmb) {
        AnnotatedType<?> type = pmb.getAnnotatedBeanClass();
        Class<?> clazz = type.getJavaClass();
        if (!annotatedClasses.contains(clazz)) {
            return;
        }

        annotatedClassesProcessed.add(clazz);

        logger.log(Level.FINE, () -> "Processing annotations for " + clazz.getName());

        processManagedBean(pmb);
   }

    /**
     * Deals with a managed bean that survived vetoing, provided by concrete extension implementations.
     * <p>
     * The meaning of "process" varies among the concrete implementations. At this point, this base implementation has managed
     * the annotation processing in a general way (e.g., only non-vetoed beans survive) and now delegates to the concrete
     * implementations to actually respond appropriately to the bean and whichever of its members are annotated.
     * </p>
     *
     * @param processManagedBean      the managed bean, with at least one annotation of interest to the extension
     */
    protected abstract void processManagedBean(ProcessManagedBean<?> processManagedBean);

    /**
     * Checks to make sure the annotated type is not abstract and is not an interceptor.
     *
     * @param pat {@code ProcessAnnotatedType} event
     * @return true if the annotated type should be kept for potential processing later; false otherwise
     */
    protected boolean isConcreteNonInterceptor(ProcessAnnotatedType<?> pat) {
        AnnotatedType<?> annotatedType = pat.getAnnotatedType();
        Class<?> clazz = annotatedType.getJavaClass();

        // Abstract classes are handled when we deal with a concrete subclass. Also, ignore if @Interceptor is present.
        if (annotatedType.isAnnotationPresent(Interceptor.class)
                || Modifier.isAbstract(clazz.getModifiers())) {
            logger.log(Level.FINER, () -> "Ignoring " + clazz.getName()
                    + " with annotations " + annotatedType.getAnnotations()
                    + " for later processing: "
                    + (Modifier.isAbstract(clazz.getModifiers()) ? "abstract " : "")
                    + (annotatedType.isAnnotationPresent(Interceptor.class) ? "interceptor " : ""));
            return false;
        }
        logger.log(Level.FINE, () -> "Accepting " + clazz.getName() + " for later bean processing");
        return true;
    }

    /**
     * Records the Java class underlying an annotated type.
     *
     * @param pat {@code ProcessAnnotatedType} event
     */
    protected void recordAnnotatedType(ProcessAnnotatedType<?> pat) {
        annotatedClasses.add(pat.getAnnotatedType()
                    .getJavaClass());
    }

    protected boolean isOwnProducerOrNonDefaultQualified(Bean<?> bean, Class<?> ownProducerClass) {
        return ownProducerClass.equals(bean.getBeanClass())
                || bean.getQualifiers()
                        .stream()
                        .noneMatch(Default.class::isInstance);
    }

    /**
     * Records a producer field defined by the application. Ignores producers with non-default qualifiers and library producers.
     *
     * @param ppf Producer field.
     */
    protected void recordProducerField(ProcessProducerField<?, ?> ppf) {
        recordProducerMember("recordProducerField", ppf.getAnnotatedProducerField(), ppf.getBean());
    }

    /**
     * Records a producer method defined by the application. Ignores producers with non-default qualifiers and library producers.
     *
     * @param ppm Producer method.
     */
    protected void recordProducerMethod(ProcessProducerMethod<?, ?> ppm) {
        recordProducerMember("recordProducerMethod", ppm.getAnnotatedProducerMethod(), ppm.getBean());
    }

    protected Map<Bean<?>, AnnotatedMember<?>> producers() {
        return producers;
    }

    /**
     * Registers the service-related endpoint, after security and as CDI initializes the app scope, returning the default routing
     * for optional use by the caller.
     *
     * @param adv    app-scoped initialization event
     * @param bm     BeanManager
     * @param server the ServerCdiExtension
     * @return default routing
     */
    protected Routing.Builder registerService(
            @Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class) Object adv,
            BeanManager bm, ServerCdiExtension server) {

        Config config = ((Config) ConfigProvider.getConfig()).get(configPrefix);
        serviceSupport = serviceSupportFactory.apply(config);

        ConfigValue<String> routingNameConfig = config.get("routing")
                .asString();
        Routing.Builder defaultRouting = server.serverRoutingBuilder();

        Routing.Builder endpointRouting = defaultRouting;

        if (routingNameConfig.isPresent()) {
            String routingName = routingNameConfig.get();
            // support for overriding this back to default routing using config
            if (!"@default".equals(routingName)) {
                endpointRouting = server.serverNamedRoutingBuilder(routingName);
            }
        }

        serviceSupport.configureEndpoint(endpointRouting);

        return defaultRouting;
    }

    protected T serviceSupport() {
        return serviceSupport;
    }

    /**
     * Manages a very simple multi-map of {@code Executable} to {@code Class<? extends Annotation>} to a {@code Set} of typed
     * work items.
     *
     * @param <W> type of work items managed
     */
    protected static class WorkItemsManager<W> {

        public static <W>  WorkItemsManager<W> create() {
            return new WorkItemsManager<>();
        }

        private WorkItemsManager() {
        }

        private final Map<Executable, Map<Class<? extends Annotation>, Set<W>>> workItemsByExecutable = new HashMap<>();

        public void put(Executable executable, Class<? extends Annotation> annotationType, W workItem) {
            workItemsByExecutable
                    .computeIfAbsent(executable, e -> new HashMap<>())
                    .computeIfAbsent(annotationType, t -> new HashSet<>())
                    .add(workItem);
        }

        public Iterable<W> workItems(Executable executable, Class<? extends Annotation> annotationType) {
            return workItemsByExecutable
                    .getOrDefault(executable, Collections.emptyMap())
                    .getOrDefault(annotationType, Collections.emptySet());
        }
    }

    /**
     * Records producer fields and methods defined by the application. Ignores producers with non-default qualifiers and
     * library producers.
     *
     * @param logPrefix typically denotes the method to distinguish whether fields or methods are being recorded
     * @param member the field or method
     * @param bean the bean which might bear producer members we are interested in
     */
    private void recordProducerMember(String logPrefix, AnnotatedMember<?> member, Bean<?> bean) {
        logger.log(Level.FINE, () -> logPrefix + " " + bean.getBeanClass());
        producers.put(bean, member);
    }
}
