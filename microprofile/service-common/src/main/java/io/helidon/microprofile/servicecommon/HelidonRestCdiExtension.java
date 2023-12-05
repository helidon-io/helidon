/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.servicecommon;

import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.mp.MpConfig;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.http.HttpRouting;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.ProcessProducerField;
import jakarta.enterprise.inject.spi.ProcessProducerMethod;
import jakarta.interceptor.Interceptor;
import org.eclipse.microprofile.config.ConfigProvider;

import static io.helidon.webserver.WebServer.DEFAULT_SOCKET_NAME;

/**
 * Abstract superclass of service-specific, REST-based CDI extensions.
 * <p>
 * This class implements a substantial amount of the work many extensions must do to process
 * annotated types for REST-based services.
 * </p>
 * <p>
 * Each CDI extension is presumed to layer on an SE-style service support class which itself is a subclass of
 * {@link io.helidon.webserver.servicecommon.HelidonFeatureSupport} with an associated {@code Builder} class.
 * The service support base class and its builder are both type parameters to this class.
 * </p>
 * <p>
 * Each concrete implementation should:
 * <ul>
 *     <li>Invoke {@link #recordAnnotatedType} for each class which bears an annotation of interest to the
 *     extension, often from a {@code ProcessAnnotatedType} observer method.</li>
 *     <li>Implement {@link #processManagedBean(ProcessManagedBean)} which this base class invokes to notify the
 *     implementation class of each managed bean type that was reported by the concrete extension but not vetoed by some
 *     other extension. Each extension can interpret "process" however it needs to. Metrics, for example, creates
 *     metrics and registers them with the appropriate metrics registry.</li>
 * </ul>
 */
public abstract class HelidonRestCdiExtension implements Extension {

    private final Map<Bean<?>, AnnotatedMember<?>> producers = new HashMap<>();

    private final Set<Class<?>> annotatedClasses = new HashSet<>();
    private final Set<Class<?>> annotatedClassesProcessed = new HashSet<>();

    private final System.Logger logger;
    private final String[] configPrefixes;

    private volatile Config rootConfig;
    private volatile Config componentConfig;

    /**
     * Common initialization for concrete implementations.
     *
     * @param logger         Logger instance to use for logging messages
     * @param configPrefixes prefixes for retrieving config related to this extension
     */
    protected HelidonRestCdiExtension(
            System.Logger logger,
            String... configPrefixes) {
        this.logger = logger;
        this.configPrefixes = configPrefixes;
    }

    /**
     * Cleans up any data structures created during annotation processing but which are not needed once the CDI container has
     * started.
     *
     * @param adv the {@code AfterDeploymentValidation} event
     */
    // method needs to be public so it is registered for reflection (native image)
    public void clearAnnotationInfo(@Observes AfterDeploymentValidation adv) {
        if (logger.isLoggable(Level.DEBUG)) {
            Set<Class<?>> annotatedClassesIgnored = new HashSet<>(annotatedClasses);
            annotatedClassesIgnored.removeAll(annotatedClassesProcessed);
            if (!annotatedClassesIgnored.isEmpty()) {
                logger.log(Level.DEBUG, () ->
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
    // method needs to be public so it is registered for reflection (native image)
    public void observeManagedBeans(@Observes ProcessManagedBean<?> pmb) {
        AnnotatedType<?> type = pmb.getAnnotatedBeanClass();
        Class<?> clazz = type.getJavaClass();
        if (!annotatedClasses.contains(clazz)) {
            return;
        }

        annotatedClassesProcessed.add(clazz);

        logger.log(Level.DEBUG, () -> "Processing managed bean " + clazz.getName());

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
     * @param processManagedBean the managed bean, with at least one annotation of interest to the extension
     */
    protected void processManagedBean(ProcessManagedBean<?> processManagedBean) {
    }

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
            logger.log(Level.TRACE, () -> "Ignoring " + clazz.getName()
                    + " with annotations " + annotatedType.getAnnotations()
                    + " for later processing: "
                    + (Modifier.isAbstract(clazz.getModifiers()) ? "abstract " : "")
                    + (annotatedType.isAnnotationPresent(Interceptor.class) ? "interceptor " : ""));
            return false;
        }
        logger.log(Level.DEBUG, () -> "Accepting " + clazz.getName() + " for later bean processing");
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
     * SE Configuration, root.
     *
     * @return config instance
     */
    protected Config rootConfig() {
        if (rootConfig == null) {
            rootConfig = MpConfig.toHelidonConfig(ConfigProvider.getConfig());
        }
        return rootConfig;
    }

    /**
     * SE Configuration of the current compoennt.
     *
     * @return component configuration
     */
    protected Config componentConfig() {
        return componentConfig(rootConfig());
    }

    /**
     * Find routing builder to use for this component to be registered on.
     * Uses the {@code routing} key on the service level to choose the correct routing
     * (listener).
     *
     * @param server server CDI extension
     * @return routing builder to use
     */
    protected HttpRouting.Builder routingBuilder(ServerCdiExtension server) {
        String routingName = componentConfig(rootConfig()).get("routing")
                .asString()
                .filter(String::isBlank)
                .orElse(DEFAULT_SOCKET_NAME);
        return DEFAULT_SOCKET_NAME.equals(routingName)
                ? server.serverRoutingBuilder()
                : server.serverNamedRoutingBuilder(routingName);
    }

    /**
     * Returns the config key for settings for the specified suffix nested within the server config tree.
     *
     * @param suffix the config key suffix (typically the name of the component: e.g., health)
     * @return full nested config key for the specified suffix
     */
    protected static String nestedConfigKey(String suffix) {
        return "server.features.observe.observers." + suffix;
    }

    /**
     * Configure with runtime config.
     *
     * @param config config to use
     */
    public void prepareRuntime(@Observes @RuntimeStart Config config) {
        // this method must be public, so it is registered for reflection by Helidon GraalVM feature
        this.rootConfig = config;
    }


    private Config componentConfig(Config rootConfig) {
        if (componentConfig == null) {
            for (String configPrefix : configPrefixes) {
                Config componentConfig = rootConfig.get(configPrefix);
                if (componentConfig.exists()) {
                    this.componentConfig = componentConfig;
                }
            }
            if (this.componentConfig == null) {
                if (configPrefixes.length == 0) {
                    this.componentConfig = rootConfig;
                } else {
                    this.componentConfig = rootConfig.get(configPrefixes[0]);
                }
            }
        }
        return componentConfig;
    }

    /**
     * Records producer fields and methods defined by the application. Ignores producers with non-default qualifiers and
     * library producers.
     *
     * @param logPrefix typically denotes the method to distinguish whether fields or methods are being recorded
     * @param member    the field or method
     * @param bean      the bean which might bear producer members we are interested in
     */
    private void recordProducerMember(String logPrefix, AnnotatedMember<?> member, Bean<?> bean) {
        logger.log(Level.DEBUG, () -> logPrefix + " " + bean.getBeanClass());
        producers.put(bean, member);
    }

    /**
     * Manages a very simple multi-map of {@code Executable} to {@code Class<? extends Annotation>} to a {@code Set} of typed
     * work items.
     *
     * @param <W> type of work items managed
     */
    protected static class WorkItemsManager<W> {

        private final Map<Executable, Map<Class<? extends Annotation>, List<W>>> workItemsByExecutable = new HashMap<>();

        private WorkItemsManager() {
        }

        public static <W> WorkItemsManager<W> create() {
            return new WorkItemsManager<>();
        }

        public void put(Executable executable, Class<? extends Annotation> annotationType, W workItem) {
            List<W> workItems = workItemsByExecutable
                    .computeIfAbsent(executable, e -> new HashMap<>())
                    .computeIfAbsent(annotationType, t -> new ArrayList<>());
            // This method is invoked only during annotation processing from CDI extensions, so linear scans of the lists
            // does not hurt runtime performance during request handling.
            if (!workItems.contains(workItem)) {
                workItems.add(workItem);
            }
        }

        public Iterable<W> workItems(Executable executable, Class<? extends Annotation> annotationType) {
            return workItemsByExecutable
                    .getOrDefault(executable, Collections.emptyMap())
                    .getOrDefault(annotationType, Collections.emptyList());
        }
    }
}
