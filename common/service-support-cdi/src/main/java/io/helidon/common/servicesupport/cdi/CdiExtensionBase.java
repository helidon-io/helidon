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
package io.helidon.common.servicesupport.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
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
import javax.enterprise.inject.spi.AnnotatedConstructor;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.inject.Qualifier;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;

import io.helidon.common.servicesupport.ServiceSupportBase;
import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.Routing;

import org.eclipse.microprofile.config.ConfigProvider;

import static io.helidon.common.servicesupport.cdi.LookupResult.lookupAnnotation;
import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * Abstract superclass of service-specific CDI extensions.
 * <p>
 *     This heavily parameterized class implements a substantial amount of the work many extensions need to do to process
 *     annotated types. Although originally inspired by the needs of metrics extensions, this class can be suitable for other
 *     extensions as well.
 * </p>
 * <p>
 *     Each extension is presumed to layer on an SE-style service support class which itself is a subclass of
 *     {@link ServiceSupportBase} with an associated {@code Builder} class. The service support base class and its builder are
 *     both type parameters to this class.
 * </p>
 * <p>
 *     Inner classes contain information harvested by the extension plus logic that might be useful outside the extension, such as
 *     from interceptors. This class identifies "asynchronous" annotated methods as those with a
 *     {@code Suspended} {@code AsyncResponse}
 *     parameter. It records information about those as instances of concrete subclasses of {@link AsyncResponseInfo}. These
 *     info instances are collected inside a REST endpoint info data structure which extends {@link RestEndpointInfo}. Both of
 *     these types are parameters to this class so concrete implementations can add data that is specific to their specific
 *     technologies to the data structures. Concrete implementations of this class provide factory methods for these
 *     parameterized types.
 * </p>
 *
 * @param <M> Common supertype of all classes (e.g., metrics) for which producers are managed by the extension
 * @param <A> concrete {@code AsyncResponseInfo} type
 * @param <R> concrete {@code RestEndpointInfo} type
 * @param <T> concrete type of {@code ServiceSupportBase} used
 * @param <B> Builder for the concrete type of {@code }ServiceSupportBase}
 */
public abstract class CdiExtensionBase<M,
        A extends CdiExtensionBase.AsyncResponseInfo,
        R extends CdiExtensionBase.RestEndpointInfo,
        T extends ServiceSupportBase<T, B>,
        B extends ServiceSupportBase.Builder<T, B>> implements Extension {
    private final Map<Bean<?>, AnnotatedMember<?>> producers = new HashMap<>();

    private final Set<Class<?>> annotatedClasses = new HashSet<>();
    private final Set<Class<?>> annotatedClassesProcessed = new HashSet<>();
    private final Set<Class<? extends Annotation>> annotations;

    private final Logger logger;
    private final Class<?> ownProducer;
    private final Function<Config, T> serviceSupportFactory;
    private final String configPrefix;

    private T serviceSupport = null;

    private R restEndpointInfo = null;

    /**
     * Common initialization for concrete implementations.
     *
     * @param logger Logger instance to use for logging messages
     * @param annotations set of annotations this extension handles
     * @param ownProducer type of producer class use in creating beans needed by the extension
     * @param serviceSupportFactory function from config to the corresponding SE-style service support object
     * @param configPrefix prefix for retrieving config related to this extension
     */
    protected CdiExtensionBase(
            Logger logger,
            Set<Class<? extends Annotation>> annotations,
            Class<?> ownProducer,
            Function<Config, T> serviceSupportFactory,
            String configPrefix) {
        this.logger = logger;
        this.annotations = annotations;
        this.ownProducer = ownProducer; // class containing producers provided by this module
        this.serviceSupportFactory = serviceSupportFactory;
        this.configPrefix = configPrefix;
    }

    /**
     * Returns the real class of this object, skipping proxies.
     *
     * @param object The object.
     * @return Its class.
     */
    public static Class<?> getRealClass(Object object) {
        Class<?> result = object.getClass();
        while (result.isSynthetic()) {
            result = result.getSuperclass();
        }
        return result;
    }

    protected Set<Class<?>> annotatedClasses() {
        return annotatedClasses;
    }

    protected Set<Class<?>> annotatedClassesProcessed() {
        return annotatedClassesProcessed;
    }

    protected void before(@Observes BeforeBeanDiscovery discovery) {
        restEndpointInfo = newRestEndpointInfo();
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

    protected R restEndpointInfo() {
        return restEndpointInfo;
    }

    /**
     * Finds an existing or adds a new extension-specific {@code AsyncResponseInfo} for the indicated method.
     *
     * @param method the Method for which the AsyncResponseInfo is needed
     * @return the pre-existing or newly-created instance, if any; null if no existing mapping was found and the factory method
     * declined to create one
     */
    protected A computeIfAbsentAsyncResponseInfo(Method method){
        Map<Method, A> asyncResponseInfo = restEndpointInfo.asyncResponseInfo();
        return asyncResponseInfo.computeIfAbsent(method, this::newAsyncResponseInfo);
    }

    /**
     * Returns a {@code AsyncResponseInfo} (or subclass) instance describing the async information about the specified method.
     *
     * @param method Method to examine for asynchronous behavior
     * @return AsyncResponseInfo describing the async behavior; null if the method is synchronous
     */
    protected abstract A newAsyncResponseInfo(Method method);

    /**
     * Returns the index in the method's array of parameters, if any, with type {@code AsyncResponse} and annotated with
     * {@code Suspended}.
     *
     * @param m the method to examine
     * @return the array index of the async parameter, if any; -1 otherwise
     */
    protected static int asyncParameterSlot(Method m) {
        int candidateAsyncResponseParameterSlot = 0;

        for (Parameter p : m.getParameters()) {
            if (AsyncResponse.class.isAssignableFrom(p.getType()) && p.getAnnotation(Suspended.class) != null) {
                return candidateAsyncResponseParameterSlot;
            }
            candidateAsyncResponseParameterSlot++;

        }
        return -1;
    }

    /**
     * Returns a new instance of the extension-specific REST endpoint information.
     *
     * @return newly-initialized instance
     */
    protected abstract R newRestEndpointInfo();


    /**
     * Observes all beans but immediately dismisses ones for which the Java class was not previously noted
     * by the {@code ProcessAnnotatedType} observer (which recorded only classes with selected annotations).
     *
     * @param pmb event describing the managed bean being processed
     */
    protected void registerObjects(@Observes ProcessManagedBean<?> pmb) {
        AnnotatedType<?> type =  pmb.getAnnotatedBeanClass();
        Class<?> clazz = type.getJavaClass();
        if (!annotatedClasses.contains(clazz)) {
            return;
        }
        // Recheck for Interceptor.
        if (type.isAnnotationPresent(Interceptor.class)) {
            logger.log(Level.FINE, "Ignoring objects defined on type " + clazz.getName()
                    + " because a CDI portable extension added @Interceptor to it dynamically");
            return;
        }
        annotatedClassesProcessed.add(clazz);

        logger.log(Level.FINE, () -> "Processing annotations for " + clazz.getName());

        // Process methods keeping non-private declared on this class
        for (AnnotatedMethod<?> annotatedMethod : type.getMethods()) {
            if (Modifier.isPrivate(annotatedMethod.getJavaMember().getModifiers())) {
                continue;
            }
            annotations.forEach(annotation -> {
                for (LookupResult<? extends Annotation> lookupResult : LookupResult.lookupAnnotations(
                        type, annotatedMethod, annotation)) {
                    // For methods, register the object only on the declaring
                    // class, not subclasses per the MP Metrics 2.0 TCK
                    // VisibilityTimedMethodBeanTest.
                    if (lookupResult.getType() != MatchingType.METHOD
                            || clazz.equals(annotatedMethod.getJavaMember()
                            .getDeclaringClass())) {
                        register(annotatedMethod.getJavaMember(), clazz, lookupResult);
                    }
                }
            });
        }

        // Process constructors
        for (AnnotatedConstructor<?> annotatedConstructor : type.getConstructors()) {
            Constructor c = annotatedConstructor.getJavaMember();
            if (Modifier.isPrivate(c.getModifiers())) {
                continue;
            }
            annotations.forEach(annotation -> {
                LookupResult<? extends Annotation> lookupResult
                        = lookupAnnotation(c, annotation, clazz);
                if (lookupResult != null) {
                    register(c, clazz, lookupResult);
                }
            });
        }
    }

    /**
     * Registers an object based on an annotation site.
     * <p>
     *     The meaning of "register" varies among the concrete implementations. At this point, this base implementation has
     *     managed the annotation processing in a general way (e.g., only non-vetoed beans survive) and now delegates to the
     *     concrete implementations to actually respond appropriately to the annotation site.
     * </p>
     *
     * @param element the Element hosting the annotation
     * @param clazz the class on which the hosting Element appears
     * @param lookupResult result of looking up an annotation on an element, its class, and its ancestor classes
     * @param <E> type of method or field or constructor
     */
    protected abstract <E extends Member & AnnotatedElement>
    void register(E element, Class<?> clazz, LookupResult<? extends Annotation> lookupResult);

    /**
     * Checks to make sure the annotated type is not abstract and is not an interceptor.
     *
     * @param pat {@code ProcessAnnotatedType} event
     * @return true if the annotated type should be kept for potential processing later; false otherwise
     */
    protected boolean checkCandidateClass(ProcessAnnotatedType<?> pat) {
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
     * Make sure the annotated type is neither abstract nor an interceptor and stores the Java class.
     *
     * @param pat {@code ProcessAnnotatedType} event
     * @return true if the annotated type should be kept for potential processing later; false otherwise
     */
    protected boolean checkAndRecordCandidateClass(ProcessAnnotatedType<?> pat) {
        boolean result = checkCandidateClass(pat);
        if (result) {
            annotatedClasses.add(pat.getAnnotatedType().getJavaClass());
        }
        return result;
    }


    /**
     * Records producer fields defined by the application. Ignores producers
     * with non-default qualifiers and library producers.
     *
     * @param ppf Producer field.
     */
    protected void recordProducerFields(@Observes ProcessProducerField<? extends M, ?> ppf) {
        recordProducerMember("recordProducerFields", ppf.getAnnotatedProducerField(), ppf.getBean());
    }

    /**
     * Records producer methods defined by the application. Ignores producers
     * with non-default qualifiers and library producers.
     *
     * @param ppm Producer method.
     */
    protected void recordProducerMethods(@Observes ProcessProducerMethod<? extends M, ?> ppm) {
        recordProducerMember("recordProducerMethods", ppm.getAnnotatedProducerMethod(), ppm.getBean());
    }

    protected Map<Bean<?>, AnnotatedMember<?>> producers() {
        return producers;
    }

    /**
     * Registers the service-related endpoint, after security and as CDI initializes the app scope, returning the default
     * routing for optional use by the caller.
     *
     * @param adv app-scoped initialization event
     * @param bm BeanManager
     * @return default routing
     */
    protected Routing.Builder registerService(
            @Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class) Object adv,
            BeanManager bm) {
        Config config = ((Config) ConfigProvider.getConfig()).get(configPrefix);

        serviceSupport = serviceSupportFactory.apply(config);

        ServerCdiExtension server = bm.getExtension(ServerCdiExtension.class);

        ConfigValue<String> routingNameConfig = config.get("routing").asString();
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

    private void recordProducerMember(String logPrefix, AnnotatedMember<?> member, Bean<?> bean) {
        logger.log(Level.FINE, () -> logPrefix + " " + bean.getBeanClass());
        if (!ownProducer.equals(bean.getBeanClass())) {
            Set<Class<? extends Annotation>> siteAnnotationTypes = new HashSet<>();

            for (Annotation memberAnnotation : member.getAnnotations()) {
                Class<? extends Annotation> memberAnnotationType = memberAnnotation.annotationType();
                if (annotations.contains(memberAnnotationType)) {
                    siteAnnotationTypes.add(memberAnnotationType);
                }
            }
            if (!siteAnnotationTypes.isEmpty()) {
                Optional<Class<? extends Annotation>> hasQualifier
                        = siteAnnotationTypes
                        .stream()
                        .filter(annotationType -> annotationType.isAnnotationPresent(Qualifier.class))
                        .findFirst();
                // Ignore producers with non-default qualifiers
                if (!hasQualifier.isPresent() || Default.class.isInstance(hasQualifier.get())) {
                    producers.put(bean, member);
                }
            }
        }
    }

    /**
     * Captures information about REST endpoints so, for example, interceptors can be quicker. Includes
     * which JAX-RS endpoint methods (if any) are asynchronous.
     *
     * @param <A> concrete type of {@code AsyncResponseInfo}
     */
    protected static class RestEndpointInfo<A extends AsyncResponseInfo> {

        private final Map<Method, A> asyncResponseInfo = new HashMap<>();

        public AsyncResponse asyncResponse(InvocationContext context) {
            A info = asyncResponseInfo.get(context.getMethod());
            return info == null ? null : info.asyncResponse(context);
        }

        public Map<Method, A> asyncResponseInfo() {
            return asyncResponseInfo;

        }
    }

    /**
     * Description of an {@code AsyncResponse} parameter annotated with {@code Suspended} in a JAX-RS method.
     */
    protected static class AsyncResponseInfo {

        // which parameter slot in the method the AsyncResponse is
        private final int parameterSlot;


        protected AsyncResponseInfo(int parameterSlot) {
            this.parameterSlot = parameterSlot;
        }

        /**
         * Returns the {@code AsyncResponse} argument object in the given invocation.
         *
         * @param context the {@code InvocationContext} representing the call with an {@code AsyncResponse} parameter
         * @return the {@code AsyncResponse} instance
         */
        public AsyncResponse asyncResponse(InvocationContext context) {
            return AsyncResponse.class.cast(context.getParameters()[parameterSlot]);
        }
    }
}

