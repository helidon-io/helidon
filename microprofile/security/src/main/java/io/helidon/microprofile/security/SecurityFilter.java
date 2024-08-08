/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.security;

import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.common.context.Contexts;
import io.helidon.jersey.common.InvokedResource;
import io.helidon.security.AuditEvent;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityLevel;
import io.helidon.security.annotations.Audited;
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.annotations.Authorized;
import io.helidon.security.integration.common.ResponseTracing;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.security.internal.SecurityAuditEvent;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer;
import io.helidon.webserver.security.SecurityHttpFeature;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.AbstractResourceModelVisitor;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.model.RuntimeResource;

/**
 * A filter that handles authentication and authorization.
 */
@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
@Priority(Priorities.AUTHENTICATION)
@ConstrainedTo(RuntimeType.SERVER)
public class SecurityFilter extends SecurityFilterCommon implements ContainerRequestFilter, ContainerResponseFilter {
    private static final System.Logger LOGGER = System.getLogger(SecurityFilter.class.getName());

    /**
     * Since Helidon supports multiple {@code Application} subclasses as well as resource
     * sharing among them, the caching for security definitions is first keyed on the
     * application class.
     */
    private final Map<Class<?>, CacheEntry> applicationClassCache = new HashMap<>();
    private final ReentrantLock applicationClassCacheLock = new ReentrantLock();
    private final ReentrantLock resourceClassSecurityLock = new ReentrantLock();
    private final ReentrantLock resourceMethodSecurityLock = new ReentrantLock();
    private final ReentrantLock subResourceMethodSecurityLock = new ReentrantLock();

    private final SecurityContext securityContext;
    private final List<AnnotationAnalyzer> analyzers = new LinkedList<>();

    /**
     * Constructor to be used by Jersey when creating an instance, injects all parameters.
     *
     * @param security        security instance
     * @param featureConfig   feature config
     * @param securityContext security context
     */
    @SuppressWarnings("unused")
    public SecurityFilter(@Context Security security,
                          @Context FeatureConfig featureConfig,
                          @Context SecurityContext securityContext) {
        super(security, featureConfig);

        this.securityContext = securityContext;

        loadAnalyzers();
    }

    // due to a bug in Jersey @Context in constructor injection is failing
    // this method is needed for unit tests
    SecurityFilter(FeatureConfig featureConfig, Security security, SecurityContext securityContext) {
        super(security, featureConfig);
        this.securityContext = securityContext;
        loadAnalyzers();
    }

    private void loadAnalyzers() {
        HelidonServiceLoader.builder(ServiceLoader.load(AnnotationAnalyzer.class))
                            .build()
                            .forEach(analyzers::add);
    }

    /**
     * A life-cycle method invoked by Jersey that finished initialization of the filter.
     */
    @PostConstruct
    public void postConstruct() {
        // we must initialize the analyzers before using them in appWideSecurity
        Config analyzersConfig = config("jersey.analyzers");
        analyzers.forEach(analyzer -> analyzer.init(analyzersConfig));
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (featureConfig().shouldUsePrematchingAuthentication() && featureConfig().shouldUsePrematchingAuthorization()) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "Security handled by pre-matching filter, ignoring.");
            }
            return;
        }
        // only filter when not pre-matching (see SecurityPreMatchingFilter)
        doFilter(request, securityContext);
    }

    @Override
    protected void processSecurity(ContainerRequestContext request,
                                   SecurityFilterContext filterContext,
                                   SecurityTracing tracing,
                                   SecurityContext securityContext) {

        // if we use pre-matching authentication, skip this step
        if (!featureConfig().shouldUsePrematchingAuthentication()) {
            /*
             * Authentication
             */
            authenticate(filterContext, securityContext, tracing.atnTracing());
            LOGGER.log(Level.TRACE, () -> "Filter after authentication. Should finish: " + filterContext.isShouldFinish());
            // authentication failed
            if (filterContext.isShouldFinish()) {
                return;
            }

            filterContext.clearTrace();
        }

        // for the sake of consistency, check this again
        if (!featureConfig().shouldUsePrematchingAuthorization()) {
            /*
             * Authorization
             */
            authorize(filterContext, securityContext, tracing.atzTracing());

            LOGGER.log(Level.TRACE, () -> "Filter completed (after authorization)");
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        jakarta.ws.rs.core.SecurityContext jSecurityContext = requestContext.getSecurityContext();
        if (null == jSecurityContext) {
            return;
        }

        JerseySecurityContext jerseySecurityContext;
        if (jSecurityContext instanceof JerseySecurityContext) {
            jerseySecurityContext = (JerseySecurityContext) jSecurityContext;
        } else {
            return;
        }
        io.helidon.common.context.Context helidonContext = Contexts.context()
                .orElseThrow(() -> new IllegalStateException("Context must be available in Jersey"));
        helidonContext.get(SecurityHttpFeature.CONTEXT_RESPONSE_HEADERS, Map.class)
                .map(it -> (Map<String, List<String>>) it)
                .ifPresent(it -> {
                    MultivaluedMap<String, Object> headers = responseContext.getHeaders();
                    for (Map.Entry<String, List<String>> entry : it.entrySet()) {
                        entry.getValue().forEach(value -> headers.add(entry.getKey(), value));
                    }
                });

        SecurityFilterContext fc = (SecurityFilterContext) requestContext.getProperty(PROP_FILTER_CONTEXT);
        SecurityDefinition methodSecurity = jerseySecurityContext.methodSecurity();
        SecurityContext securityContext = jerseySecurityContext.securityContext();

        if (fc.isExplicitAtz() && !securityContext.isAuthorized()) {
            // now we have an option that the response code is already an error (e.g. BadRequest)
            // in such a case we return the original error, as we may have never reached the method code
            switch (responseContext.getStatusInfo().getFamily()) {
                case CLIENT_ERROR:
                case SERVER_ERROR:
                    break;
                case INFORMATIONAL:
                case SUCCESSFUL:
                case REDIRECTION:
                case OTHER:
                default:
                    // authorization should have been explicit, yet it was not checked - this is a programmer error
                    if (featureConfig().isDebug()) {
                        responseContext.setEntity("Authorization was marked as explicit,"
                                + " yet it was never called in resource method");
                    } else {
                        responseContext.setEntity("");
                    }
                    responseContext.setStatus(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
                    LOGGER.log(Level.ERROR, "Authorization failure. Request for" + fc.getResourcePath()
                            + " has failed, as it was marked"
                            + "as explicitly authorized, yet authorization was never called on security context. The "
                            + "method was invoked and may have changed data. Marking as internal server error");
                    fc.setShouldFinish(true);
                    break;
            }
        }

        ResponseTracing responseTracing = SecurityTracing.get().responseTracing();

        try {
            if (methodSecurity.isAudited()) {
                AuditEvent.AuditSeverity auditSeverity;
                if (responseContext.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                    auditSeverity = methodSecurity.getAuditOkSeverity();
                } else {
                    auditSeverity = methodSecurity.getAuditErrorSeverity();
                }

                SecurityAuditEvent auditEvent = SecurityAuditEvent
                        .audit(auditSeverity, methodSecurity.getAuditEventType(), methodSecurity.getAuditMessageFormat())
                        .addParam(AuditEvent.AuditParam.plain("method", fc.getMethod()))
                        .addParam(AuditEvent.AuditParam.plain("path", fc.getResourcePath()))
                        .addParam(AuditEvent.AuditParam.plain("status", String.valueOf(responseContext.getStatus())))
                        .addParam(AuditEvent.AuditParam.plain("subject",
                                securityContext.user()
                                               .or(securityContext::service)
                                               .orElse(SecurityContext.ANONYMOUS)))
                        .addParam(AuditEvent.AuditParam.plain("transport", "http"))
                        .addParam(AuditEvent.AuditParam.plain("resourceType", fc.getResourceName()))
                        .addParam(AuditEvent.AuditParam.plain("targetUri", fc.getTargetUri()));

                securityContext.audit(auditEvent);
            }
        } finally {
            responseTracing.finish();
        }
    }

    @Override
    protected SecurityFilterContext initRequestFiltering(ContainerRequestContext requestContext) {
        SecurityFilterContext context = new SecurityFilterContext();
        InvokedResource invokedResource = InvokedResource.create(requestContext);

        return invokedResource
                .definitionMethod()
                .map(definitionMethod -> {
                    context.setMethodSecurity(getMethodSecurity(invokedResource,
                            definitionMethod,
                            (ExtendedUriInfo) requestContext.getUriInfo()));
                    context.setResourceName(definitionMethod.getDeclaringClass().getSimpleName());

                    return configureContext(context, requestContext, requestContext.getUriInfo());
                })
                .orElseGet(() -> {
                    // this will end in 404, just let it on
                    context.setShouldFinish(true);
                    return context;
                });
    }

    @Override
    protected System.Logger logger() {
        return LOGGER;
    }

    /**
     * Creates security definition based on the annotations on a class and using a
     * parent as a starting point. Obtains real class before processing to skip
     * proxies.
     *
     * @param theClass class from which to create security definition
     * @param parent   base security definition or {@code null}
     * @return security definition for the class
     */
    private SecurityDefinition securityForClass(Class<?> theClass, SecurityDefinition parent) {
        Class<?> realClass = getRealClass(theClass);
        Authenticated atn = realClass.getAnnotation(Authenticated.class);
        Authorized atz = realClass.getAnnotation(Authorized.class);
        Audited audited = realClass.getAnnotation(Audited.class);

        // as sometimes we may want to prevent calls to authorization provider unless
        // explicitly invoked by developer
        SecurityDefinition definition = (
                (null == parent)
                        ? new SecurityDefinition(featureConfig().shouldAuthorizeAnnotatedOnly(),
                        featureConfig().failOnFailureIfOptional())
                        : parent.copyMe());
        definition.add(atn);
        definition.add(atz);
        definition.add(audited);
        if (!featureConfig().shouldAuthenticateAnnotatedOnly()) {
            definition.requiresAuthentication(true);
        }

        Map<Class<? extends Annotation>, List<Annotation>> customAnnotsMap = new HashMap<>();
        addCustomAnnotations(customAnnotsMap, realClass);

        SecurityLevel securityLevel = SecurityLevel.create(realClass.getName())
                                                   .withClassAnnotations(customAnnotsMap)
                                                   .build();
        definition.getSecurityLevels().add(securityLevel);

        for (AnnotationAnalyzer analyzer : analyzers) {
            AnnotationAnalyzer.AnalyzerResponse analyzerResponse;

            if (null == parent) {
                analyzerResponse = analyzer.analyze(realClass);
            } else {
                analyzerResponse = analyzer.analyze(realClass, parent.analyzerResponse(analyzer));
            }

            definition.analyzerResponse(analyzer, analyzerResponse);
        }
        if (logger().isLoggable(Level.TRACE)) {
            logger().log(Level.TRACE, "Security definition for resource {0}: {1}", theClass.getName(), definition);
        }
        return definition;
    }

    /**
     * Returns the real class of this object, skipping proxies.
     *
     * @param object The object.
     * @return Its class.
     */
    private static Class<?> getRealClass(Class<?> object) {
        Class<?> result = object;
        while (result.isSynthetic()) {
            result = result.getSuperclass();
        }
        return result;
    }

    private SecurityDefinition getMethodSecurity(InvokedResource invokedResource,
                                                 Method definitionMethod,
                                                 ExtendedUriInfo uriInfo) {
        // Check cache

        // Jersey model 'definition method' is the method that contains JAX-RS/Jersey annotations. JAX-RS does not support
        // merging annotations from a parent, so we don't have to look for annotations on corresponding methods of interfaces
        // and abstract classes implemented by the definition method.

        // Jersey model does not have a 'definition class', so we have to find it from a handler class
        Class<?> obtainedClass = invokedResource.definitionClass()
                .orElseThrow(() -> new SecurityException("Got definition method, cannot get definition class"));
        Class<?> definitionClass = getRealClass(obtainedClass);

        // Get the application for this request in case there's more than one
        Application appInstance = Contexts.context()
                                          .flatMap(it -> it.get(Application.class))
                                          .orElseThrow(() -> new IllegalStateException("Context not available"));

        // Create and cache security definition for application
        Class<?> appRealClass = getRealClass(appInstance.getClass());
        SecurityDefinition appClassSecurity = appClassSecurity(appRealClass);

        if (definitionClass.getAnnotation(Path.class) == null) {
            // this is a sub-resource
            // I must locate the resource class and method that was invoked
            PathVisitor visitor = new PathVisitor();
            visitor.visit(uriInfo.getMatchedRuntimeResources());
            Collections.reverse(visitor.list);
            StringBuilder fullPathBuilder = new StringBuilder();

            List<Method> methodsToProcess = new LinkedList<>();

            for (Invocable m : visitor.list) {
                // first the top most class (MpMainResource.sub())
                // then the one under it (MpSubResource.sub())
                // these methods are above our sub resource
                Method parentDefMethod = m.getDefinitionMethod();
                Class<?> parentClass = parentDefMethod.getDeclaringClass();

                fullPathBuilder.append("/")
                               .append(parentClass.getName())
                               .append(".")
                               .append(parentDefMethod.getName());
                methodsToProcess.add(parentDefMethod);
            }

            fullPathBuilder.append("/")
                           .append(definitionClass.getName())
                           .append(".")
                           .append(definitionMethod.getName());
            methodsToProcess.add(definitionMethod);

            String fullPath = fullPathBuilder.toString();
            // now full path can be used as a cache
            try {
                subResourceMethodSecurityLock.lock();
                if (subResourceMethodSecurity(appRealClass).containsKey(fullPath)) {
                    return subResourceMethodSecurity(appRealClass).get(fullPath);
                }
            } finally {
                subResourceMethodSecurityLock.unlock();
            }

            // now process each definition method and class
            SecurityDefinition current = appClassSecurity;
            for (Method method : methodsToProcess) {
                Class<?> clazz = method.getDeclaringClass();
                current = securityForClass(clazz, current);
                SecurityDefinition methodDef = processMethod(current.copyMe(), method);

                SecurityLevel currentSecurityLevel = methodDef.getSecurityLevels().get(methodDef.getSecurityLevels().size() - 1);

                Map<Class<? extends Annotation>, List<Annotation>> methodAnnotations = new HashMap<>();
                addCustomAnnotations(methodAnnotations, method);
                SecurityLevel newSecurityLevel = SecurityLevel.create(currentSecurityLevel)
                                                              .withMethodName(method.getName())
                                                              .withMethodAnnotations(methodAnnotations)
                                                              .build();
                methodDef.getSecurityLevels().set(methodDef.getSecurityLevels().size() - 1, newSecurityLevel);
                for (AnnotationAnalyzer analyzer : analyzers) {
                    AnnotationAnalyzer.AnalyzerResponse analyzerResponse = analyzer.analyze(method,
                            current.analyzerResponse(analyzer));

                    methodDef.analyzerResponse(analyzer, analyzerResponse);
                }
                current = methodDef;
            }

            try {
                subResourceMethodSecurityLock.lock();
                subResourceMethodSecurity(appRealClass).put(fullPath, current);
            } finally {
                subResourceMethodSecurityLock.unlock();
            }
            return current;
        }

        try {
            resourceMethodSecurityLock.lock();
            if (resourceMethodSecurity(appRealClass).containsKey(definitionMethod)) {
                return resourceMethodSecurity(appRealClass).get(definitionMethod);
            }
        } finally {
            resourceMethodSecurityLock.unlock();
        }

        SecurityDefinition resClassSecurity = obtainClassSecurityDefinition(appRealClass, appClassSecurity, definitionClass);


        SecurityDefinition methodDef = processMethod(resClassSecurity, definitionMethod);

        int index = methodDef.getSecurityLevels().size() - 1;
        SecurityLevel currentSecurityLevel = methodDef.getSecurityLevels().get(index);
        Map<Class<? extends Annotation>, List<Annotation>> methodLevelAnnotations = new HashMap<>();
        addCustomAnnotations(methodLevelAnnotations, definitionMethod);

        methodDef.getSecurityLevels().set(index, SecurityLevel.create(currentSecurityLevel)
                                                              .withMethodName(definitionMethod.getName())
                                                              .withMethodAnnotations(methodLevelAnnotations)
                                                              .build());
        try {
            resourceMethodSecurityLock.lock();
            resourceMethodSecurity(appRealClass).put(definitionMethod, methodDef);
        } finally {
            resourceMethodSecurityLock.unlock();
        }

        for (AnnotationAnalyzer analyzer : analyzers) {
            AnnotationAnalyzer.AnalyzerResponse analyzerResponse = analyzer.analyze(definitionMethod,
                    resClassSecurity.analyzerResponse(analyzer));

            methodDef.analyzerResponse(analyzer, analyzerResponse);
        }

        return methodDef;
    }

    private SecurityDefinition obtainClassSecurityDefinition(Class<?> appRealClass, SecurityDefinition appClassSecurity,
                                                             Class<?> definitionClass) {
        Map<Class<?>, SecurityDefinition> classSecurityDefinitionMap = resourceClassSecurity(appRealClass);
        try {
            resourceClassSecurityLock.lock();
            return classSecurityDefinitionMap.computeIfAbsent(definitionClass,
                                                              aClass -> securityForClass(definitionClass, appClassSecurity));
        } finally {
            resourceClassSecurityLock.unlock();
        }
    }

    private void addCustomAnnotations(Map<Class<? extends Annotation>, List<Annotation>> customAnnotsMap, Class<?> theClass) {
        Annotation[] annotations = theClass.getAnnotations();
        for (Annotation annotation : annotations) {
            addToMap(annotation.annotationType(), customAnnotsMap, annotation);
        }
    }

    private void addToMap(Class<? extends Annotation> annotClass,
                          Map<Class<? extends Annotation>, List<Annotation>> customAnnotsMap,
                          Annotation... annot) {
        customAnnotsMap.computeIfAbsent(annotClass, key -> new LinkedList<>()).addAll(Arrays.asList(annot));
    }

    private void addCustomAnnotations(Map<Class<? extends Annotation>, List<Annotation>> customAnnotsMap, Method theMethod) {
        Annotation[] annotations = theMethod.getAnnotations();
        for (Annotation annotation : annotations) {
            addToMap(annotation.annotationType(), customAnnotsMap, annotation);
        }
    }

    // unit test method
    List<AnnotationAnalyzer> analyzers() {
        return this.analyzers;
    }

    private static SecurityDefinition processMethod(SecurityDefinition current, Method method) {
        Authenticated atn = method.getAnnotation(Authenticated.class);
        Authorized atz = method.getAnnotation(Authorized.class);
        Audited audited = method.getAnnotation(Audited.class);
        SecurityDefinition methodDef = current.copyMe();
        methodDef.add(atn);
        methodDef.add(atz);
        methodDef.add(audited);
        return methodDef;
    }

    /**
     * Cache entry for main cache. Includes application class security definition as
     * well as security definitions for resource classes, methods and sub-resources.
     */
    private static class CacheEntry {
        private SecurityDefinition appClassSecurity;
        private final Map<Class<?>, SecurityDefinition> resourceClassSecurity = new HashMap<>();
        private final Map<Method, SecurityDefinition> resourceMethodSecurity = new HashMap<>();
        private final Map<String, SecurityDefinition> subResourceMethodSecurity = new HashMap<>();
    }

    private CacheEntry appClassCacheEntry(Class<?> appClass) {
        try {
            applicationClassCacheLock.lock();
            return applicationClassCache.computeIfAbsent(appClass, c -> {
                SecurityDefinition appClassSecurity = securityForClass(c, null);
                CacheEntry entry = new CacheEntry();
                entry.appClassSecurity = appClassSecurity;
                return entry;
            });
        } finally {
            applicationClassCacheLock.unlock();
        }
    }

    private SecurityDefinition appClassSecurity(Class<?> appClass) {
        return appClassCacheEntry(appClass).appClassSecurity;
    }

    private Map<Class<?>, SecurityDefinition> resourceClassSecurity(Class<?> appClass) {
        return appClassCacheEntry(appClass).resourceClassSecurity;
    }

    private Map<Method, SecurityDefinition> resourceMethodSecurity(Class<?> appClass) {
        return appClassCacheEntry(appClass).resourceMethodSecurity;
    }

    private Map<String, SecurityDefinition> subResourceMethodSecurity(Class<?> appClass) {
        return appClassCacheEntry(appClass).subResourceMethodSecurity;
    }

    private static final class PathVisitor extends AbstractResourceModelVisitor {
        private final List<Invocable> list = new LinkedList<>();

        private PathVisitor() {
        }

        @Override
        public void visitResource(Resource resource) {
            if (resource.getResourceLocator() != null) {
                resource.getResourceLocator().accept(this);
            }
        }

        @Override
        public void visitChildResource(Resource resource) {
            visitResource(resource);
        }

        @Override
        public void visitResourceMethod(ResourceMethod method) {
            list.add(method.getInvocable());
        }

        @Override
        public void visitRuntimeResource(RuntimeResource runtimeResource) {
            for (Resource resource : runtimeResource.getResources()) {
                resource.accept(this);
            }
        }

        public void visit(List<RuntimeResource> runtimeResources) {
            for (RuntimeResource runtimeResource : runtimeResources) {
                runtimeResource.accept(this);
            }
        }
    }
}
