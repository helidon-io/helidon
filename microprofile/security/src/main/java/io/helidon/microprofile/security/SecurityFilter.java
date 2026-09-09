/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.DeprecationSupport;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.context.Contexts;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.common.uri.UriPath;
import io.helidon.config.Config;
import io.helidon.jersey.common.InvokedResource;
import io.helidon.metadata.reflection.AnnotationFactory;
import io.helidon.metadata.reflection.TypedElementFactory;
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
import org.glassfish.jersey.internal.util.ReflectionHelper;
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
    private static final Set<String> STANDARD_SECURITY_ANNOTATIONS = Set.of(
            "jakarta.annotation.security.DenyAll",
            "jakarta.annotation.security.PermitAll",
            "jakarta.annotation.security.RolesAllowed",
            "javax.annotation.security.DenyAll",
            "javax.annotation.security.PermitAll",
            "javax.annotation.security.RolesAllowed");
    private static final Set<String> HELIDON_ACCESS_ANNOTATIONS = Set.of(
            Authenticated.class.getName(),
            Authorized.class.getName());

    /**
     * Since Helidon supports multiple {@code Application} subclasses as well as resource
     * sharing among them, the caching for security definitions is first keyed on the
     * application class.
     */
    private final Map<Class<?>, CacheEntry> applicationClassCache = new HashMap<>();
    private final Map<ResourceMethodIdentity, ResourceMethodKey> resourceMethodKeyCache = new ConcurrentHashMap<>();
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
            LOGGER.log(Level.TRACE, () -> "Filter after authentication. Should finish: " + filterContext.shouldFinish());
            // authentication failed
            if (filterContext.shouldFinish()) {
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

        if (fc.explicitAtz() && !securityContext.isAuthorized()) {
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
                    LOGGER.log(Level.ERROR, "Authorization failure. Request for" + fc.resourcePath()
                            + " has failed, as it was marked"
                            + "as explicitly authorized, yet authorization was never called on security context. The "
                            + "method was invoked and may have changed data. Marking as internal server error");
                    fc.shouldFinish(true);
                    break;
            }
        }

        ResponseTracing responseTracing = SecurityTracing.get().responseTracing();

        try {
            if (methodSecurity.audited()) {
                AuditEvent.AuditSeverity auditSeverity;
                if (responseContext.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                    auditSeverity = methodSecurity.auditOkSeverity();
                } else {
                    auditSeverity = methodSecurity.auditErrorSeverity();
                }

                SecurityAuditEvent auditEvent = SecurityAuditEvent
                        .audit(auditSeverity, methodSecurity.auditEventType(), methodSecurity.auditMessageFormat())
                        .addParam(AuditEvent.AuditParam.plain("method", fc.method()))
                        .addParam(AuditEvent.AuditParam.plain("path", fc.resourcePath()))
                        .addParam(AuditEvent.AuditParam.plain("status", String.valueOf(responseContext.getStatus())))
                        .addParam(AuditEvent.AuditParam.plain("subject",
                                securityContext.user()
                                               .or(securityContext::service)
                                               .orElse(SecurityContext.ANONYMOUS)))
                        .addParam(AuditEvent.AuditParam.plain("transport", "http"))
                        .addParam(AuditEvent.AuditParam.plain("resourceType", fc.resourceName()))
                        .addParam(AuditEvent.AuditParam.plain("targetUri", fc.targetUri()));

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
                    context.methodSecurity(getMethodSecurity(invokedResource,
                                                             definitionMethod,
                                                             (ExtendedUriInfo) requestContext.getUriInfo(),
                                                             requestContext));
                    context.resourceName(definitionMethod.getDeclaringClass().getSimpleName());

                    return configureContext(context, requestContext, requestContext.getUriInfo());
                })
                .orElseGet(() -> {
                    // this will end in 404, just let it on
                    context.shouldFinish(true);
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
     * @param theClass        class from which to create security definition
     * @param parent          base security definition or {@code null}
     * @param annotationScope class annotations to process
     * @return security definition for the class
     */
    private SecurityDefinition securityForClass(Class<?> theClass,
                                                SecurityDefinition parent,
                                                ClassAnnotationScope annotationScope) {
        return securityForClass(theClass, parent, annotationScope, AnalyzerResponses.EMPTY);
    }

    private SecurityDefinition securityForClass(Class<?> theClass,
                                                SecurityDefinition parent,
                                                ClassAnnotationScope annotationScope,
                                                AnalyzerResponses classicResponses) {
        Class<?> realClass = getRealClass(theClass);
        Annotation[] classAnnotations = classAnnotations(realClass, annotationScope);
        if (annotationScope == ClassAnnotationScope.INHERITED_ONLY && classAnnotations.length == 0) {
            return parent;
        }
        Authenticated atn = findAnnotation(classAnnotations, Authenticated.class);
        Authorized atz = findAnnotation(classAnnotations, Authorized.class);
        Audited audited = findAnnotation(classAnnotations, Audited.class);

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
        addCustomAnnotations(customAnnotsMap, classAnnotations);

        SecurityLevel securityLevel = SecurityLevel.create(realClass.getName())
                                                   .withClassAnnotations(customAnnotsMap)
                                                   .build();
        definition.securityLevels().add(securityLevel);

        boolean filteredAnnotations = switch (annotationScope) {
            case ALL_EXCLUDING_STANDARD_SECURITY,
                 DECLARED_ONLY_EXCLUDING_STANDARD_SECURITY,
                 DECLARED_ONLY_EXCLUDING_ACCESS_SECURITY,
                 INHERITED_ONLY -> true;
            default -> false;
        };
        List<io.helidon.common.types.Annotation> analyzerAnnotations = filteredAnnotations
                ? Arrays.stream(classAnnotations)
                        .map(AnnotationFactory::create)
                        .toList()
                : AnnotationFactory.create(realClass);
        List<io.helidon.common.types.Annotation> applicationAnalyzerAnnotations = null == parent && filteredAnnotations
                ? AnnotationFactory.create(realClass)
                : analyzerAnnotations;
        for (AnnotationAnalyzer analyzer : analyzers) {
            boolean expandedAnalysis = analyzer.supportsExpandedEndpointAnalysis();
            // A reflection-based analyzer cannot separate inherited from declared class annotations.
            // Keep its indivisible concrete-class view at the handling-class level after the method.
            if (expandedAnalysis
                    && annotationScope == ClassAnnotationScope.INHERITED_ONLY
                    && !DeprecationSupport.isOverridden(analyzer,
                                                        AnnotationAnalyzer.class,
                                                        "analyze",
                                                        TypeName.class,
                                                        List.class,
                                                        AnnotationAnalyzer.AnalyzerResponse.class)) {
                continue;
            }

            AnnotationAnalyzer.AnalyzerResponse analyzerResponse;
            if (null == parent) {
                analyzerResponse = analyzer.analyze(TypeName.create(realClass),
                                                    applicationAnalyzerAnnotations);
            } else if (!expandedAnalysis) {
                analyzerResponse = classicResponses.response(analyzer);
                if (analyzerResponse == null) {
                    continue;
                }
            } else {
                analyzerResponse = analyzer.analyze(TypeName.create(realClass),
                                                    analyzerAnnotations,
                                                    parent.analyzerResponse(analyzer));
            }

            definition.analyzerResponse(analyzer, analyzerResponse);
        }
        if (logger().isLoggable(Level.TRACE)) {
            logger().log(Level.TRACE, "Security definition for resource {0}: {1}", theClass.getName(), definition);
        }
        return definition;
    }

    private static Annotation[] classAnnotations(Class<?> resourceClass, ClassAnnotationScope annotationScope) {
        return switch (annotationScope) {
            case ALL -> resourceClass.getAnnotations();
            case ALL_EXCLUDING_STANDARD_SECURITY -> withoutStandardSecurity(resourceClass.getAnnotations());
            case DECLARED_ONLY -> resourceClass.getDeclaredAnnotations();
            case DECLARED_ONLY_EXCLUDING_STANDARD_SECURITY -> withoutStandardSecurity(
                    resourceClass.getDeclaredAnnotations());
            case DECLARED_ONLY_EXCLUDING_ACCESS_SECURITY -> withoutAccessSecurity(resourceClass.getDeclaredAnnotations());
            case INHERITED_ONLY -> Arrays.stream(resourceClass.getAnnotations())
                    .filter(annotation -> resourceClass.getDeclaredAnnotation(annotation.annotationType()) == null)
                    .toArray(Annotation[]::new);
        };
    }

    private static Annotation[] withoutStandardSecurity(Annotation[] annotations) {
        return Arrays.stream(annotations)
                .filter(annotation -> !isStandardSecurity(annotation))
                .toArray(Annotation[]::new);
    }

    private static List<io.helidon.common.types.Annotation> withoutStandardSecurity(
            List<io.helidon.common.types.Annotation> annotations) {
        return annotations.stream()
                .filter(annotation -> !STANDARD_SECURITY_ANNOTATIONS.contains(annotation.typeName().fqName()))
                .toList();
    }

    private static Annotation[] withoutAccessSecurity(Annotation[] annotations) {
        return Arrays.stream(annotations)
                .filter(annotation -> !isStandardSecurity(annotation))
                .filter(annotation -> !isHelidonAccessSecurity(annotation))
                .toArray(Annotation[]::new);
    }

    private static boolean isStandardSecurity(Annotation annotation) {
        return STANDARD_SECURITY_ANNOTATIONS.contains(annotation.annotationType().getName());
    }

    private static boolean isHelidonAccessSecurity(Annotation annotation) {
        return HELIDON_ACCESS_ANNOTATIONS.contains(annotation.annotationType().getName());
    }

    private static <T extends Annotation> T findAnnotation(Annotation[] annotations, Class<T> annotationType) {
        return Arrays.stream(annotations)
                .filter(annotationType::isInstance)
                .map(annotationType::cast)
                .findFirst()
                .orElse(null);
    }

    private SecurityDefinition getMethodSecurity(InvokedResource invokedResource,
                                                 Method definitionMethod,
                                                 ExtendedUriInfo uriInfo,
                                                 ContainerRequestContext requestContext) {
        ResourceMethodKey resourceKey = resourceMethodKey(invokedResource, definitionMethod);
        Class<?> jaxRsDefinitionClass = invokedResource.definitionClass()
                .map(SecurityFilter::getRealClass)
                .orElse(resourceKey.definitionClass());

        // Get the application for this request in case there's more than one
        Application appInstance = Contexts.context()
                                          .flatMap(it -> it.get(Application.class))
                                          .orElseThrow(() -> new IllegalStateException("Context not available"));

        // Create and cache security definition for application
        Class<?> appRealClass = getRealClass(appInstance.getClass());
        SecurityDefinition appClassSecurity = appClassSecurity(appRealClass);

        if (jaxRsDefinitionClass.getAnnotation(Path.class) == null) {
            // this is a sub-resource
            // I must locate the resource class and method that was invoked
            PathVisitor visitor = new PathVisitor();
            visitor.visit(uriInfo.getMatchedRuntimeResources());
            Collections.reverse(visitor.list);
            List<ResourceMethodKey> resourcesToProcess = new ArrayList<>();

            ResourceMethodKey previousLocator = null;
            for (Invocable invocable : visitor.list) {
                // first the top most class (MpMainResource.sub())
                // then the one under it (MpSubResource.sub())
                // these methods are above our sub resource
                ResourceMethodKey locator = resourceMethodKey(invocable, previousLocator == null);
                if (previousLocator != null) {
                    locator = locatedResourceKey(previousLocator, locator);
                }
                resourcesToProcess.add(locator);
                previousLocator = locator;
            }
            if (previousLocator != null) {
                resourceKey = locatedResourceKey(previousLocator, resourceKey);
            }
            resourcesToProcess.add(resourceKey);
            List<ResourceMethodKey> resourcePath = List.copyOf(resourcesToProcess);

            Config methodConfig = findMethodConfig(UriPath.create(uriInfo.getPath()), requestContext.getMethod());
            SubResourceMethodCacheKey methodKey = new SubResourceMethodCacheKey(resourcePath, methodConfig.key().toString());
            Map<SubResourceMethodCacheKey, SecurityDefinition> subResourceMethodSecurity =
                    subResourceMethodSecurity(appRealClass);

            // now full path together with matched method configuration can be used as a cache
            SecurityDefinition cached = cachedMethodSecurity(subResourceMethodSecurityLock, subResourceMethodSecurity, methodKey);
            if (cached != null) {
                return cached;
            }

            ClassicSubResourceMethodCacheKey classicMethodKey = new ClassicSubResourceMethodCacheKey(
                    jaxRsDefinitionClass,
                    resourcesToProcess.stream().map(ResourceMethodKey::definitionMethod).toList(),
                    methodConfig.key().toString());
            List<ClassicMethodAnalysis> classicAnalysis = classicSubResourceAnalysis(appRealClass,
                                                                                      classicMethodKey,
                                                                                      resourcesToProcess,
                                                                                      appClassSecurity);

            // now process each definition method and class
            SecurityDefinition current = appClassSecurity;
            for (int i = 0; i < resourcesToProcess.size(); i++) {
                ResourceMethodKey resource = resourcesToProcess.get(i);
                ClassicMethodAnalysis classicStage = classicAnalysis.get(i);
                Method method = resource.definitionMethod();
                current = securityForDefinitionClass(ResourceClassKey.create(resource,
                                                                             classicStage.classType()),
                                                     current,
                                                     classicStage);
                if (!implementationOwnsDefinitionMethod(resource)) {
                    current = processMethod(current,
                                            uriInfo.getPath(),
                                            requestContext.getMethod(),
                                            resource,
                                            method,
                                            classicStage);
                }
                current = processImplementation(current,
                                                uriInfo.getPath(),
                                                requestContext.getMethod(),
                                                resource,
                                                implementationOwnsDefinitionMethod(resource)
                                                        ? classicStage
                                                        : ClassicMethodAnalysis.EMPTY);
            }

            cached = cacheMethodSecurity(subResourceMethodSecurityLock, subResourceMethodSecurity, methodKey, current);
            return cached == null ? current : cached;
        }

        Config methodConfig = findMethodConfig(UriPath.create(uriInfo.getRequestUri().getPath()), requestContext.getMethod());
        ResourceMethodCacheKey methodKey = new ResourceMethodCacheKey(resourceKey, methodConfig.key().toString());
        Map<ResourceMethodCacheKey, SecurityDefinition> resourceMethodSecurity = resourceMethodSecurity(appRealClass);

        SecurityDefinition cached = cachedMethodSecurity(resourceMethodSecurityLock, resourceMethodSecurity, methodKey);
        if (cached != null) {
            return cached;
        }

        ResourceClassAnalysis classAnalysis = obtainClassSecurityDefinition(appRealClass,
                                                                             appClassSecurity,
                                                                             resourceKey,
                                                                             jaxRsDefinitionClass);
        SecurityDefinition classSecurity = classAnalysis.definition();
        SecurityDefinition methodDef = classSecurity;
        ClassicResourceMethodCacheKey classicMethodKey = new ClassicResourceMethodCacheKey(jaxRsDefinitionClass,
                                                                                             definitionMethod,
                                                                                             methodConfig.key().toString());
        ClassicMethodAnalysis classicMethodAnalysis = classicResourceMethodAnalysis(appRealClass,
                                                                                      classicMethodKey,
                                                                                      appClassSecurity,
                                                                                      classAnalysis);
        if (!implementationOwnsDefinitionMethod(resourceKey)) {
            methodDef = processMethod(classSecurity,
                                      uriInfo.getRequestUri().getPath(),
                                      requestContext.getMethod(),
                                      resourceKey,
                                      definitionMethod,
                                      classicMethodAnalysis);
        }

        SecurityDefinition finalDefinition = processImplementation(methodDef,
                                                                   uriInfo.getRequestUri().getPath(),
                                                                   requestContext.getMethod(),
                                                                   resourceKey,
                                                                   implementationOwnsDefinitionMethod(resourceKey)
                                                                           ? classicMethodAnalysis
                                                                           : ClassicMethodAnalysis.EMPTY);
        cached = cacheMethodSecurity(resourceMethodSecurityLock, resourceMethodSecurity, methodKey, finalDefinition);
        return cached == null ? finalDefinition : cached;
    }

    private ResourceClassAnalysis obtainClassSecurityDefinition(Class<?> appRealClass, SecurityDefinition appClassSecurity,
                                                                ResourceMethodKey resource,
                                                                Class<?> analyzerClass) {
        Class<?> classicClass = getRealClass(analyzerClass);
        ResourceClassKey resourceClassKey = ResourceClassKey.create(resource, classicClass);
        CacheEntry cacheEntry = appClassCacheEntry(appRealClass);
        try {
            resourceClassSecurityLock.lock();
            AnalyzerResponses classicResponses = cacheEntry.classicResourceClassAnalysis.computeIfAbsent(
                    classicClass,
                    key -> analyzeClassicClass(key, appClassSecurity));
            ClassicMethodAnalysis classicAnalysis = new ClassicMethodAnalysis(classicClass,
                                                                                classicResponses,
                                                                                AnalyzerResponses.EMPTY);
            SecurityDefinition definition = cacheEntry.resourceClassSecurity.computeIfAbsent(
                    resourceClassKey,
                    key -> securityForDefinitionClass(key, appClassSecurity, classicAnalysis));
            return new ResourceClassAnalysis(definition, classicClass, classicResponses);
        } finally {
            resourceClassSecurityLock.unlock();
        }
    }

    private SecurityDefinition securityForDefinitionClass(ResourceClassKey resource,
                                                          SecurityDefinition parent,
                                                          ClassicMethodAnalysis classicAnalysis) {
        Class<?> definitionClass = resource.definitionClass();
        Class<?> methodClass = resource.definitionMethodClass();
        ClassAnnotationScope methodClassScope = resource.definitionMethodStandardSecurityApplies()
                ? ClassAnnotationScope.ALL
                : ClassAnnotationScope.ALL_EXCLUDING_STANDARD_SECURITY;
        // Standard class security belongs to the class which declares the selected member. Keep the selected
        // definition level for Helidon annotations and custom analyzers, but do not import roles from another type.
        SecurityDefinition current;
        if (resource.implementationOwnsDefinitionMethod()) {
            current = securityForClass(definitionClass,
                                       parent,
                                       ClassAnnotationScope.ALL_EXCLUDING_STANDARD_SECURITY,
                                       classicClassResponses(definitionClass, classicAnalysis));
        } else if (definitionClass.equals(methodClass)) {
            current = securityForClass(definitionClass,
                                       parent,
                                       methodClassScope,
                                       classicClassResponses(definitionClass, classicAnalysis));
        } else if (methodClass.isAssignableFrom(definitionClass)) {
            current = securityForClass(methodClass,
                                       parent,
                                       methodClassScope,
                                       classicClassResponses(methodClass, classicAnalysis));
            current = securityForClass(definitionClass,
                                       current,
                                       ClassAnnotationScope.ALL_EXCLUDING_STANDARD_SECURITY,
                                       classicClassResponses(definitionClass, classicAnalysis));
        } else {
            current = securityForClass(definitionClass,
                                       parent,
                                       ClassAnnotationScope.ALL_EXCLUDING_STANDARD_SECURITY,
                                       classicClassResponses(definitionClass, classicAnalysis));
            current = securityForClass(methodClass,
                                       current,
                                       methodClassScope,
                                       classicClassResponses(methodClass, classicAnalysis));
        }
        if (resource.inheritHandlingClassAnnotations()) {
            current = securityForClass(resource.handlingClass(), current, ClassAnnotationScope.INHERITED_ONLY);
        }
        return current;
    }

    private void addCustomAnnotations(Map<Class<? extends Annotation>, List<Annotation>> customAnnotsMap,
                                      Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            addToMap(annotation.annotationType(), customAnnotsMap, annotation);
        }
    }

    private void addToMap(Class<? extends Annotation> annotClass,
                          Map<Class<? extends Annotation>, List<Annotation>> customAnnotsMap,
                          Annotation... annot) {
        customAnnotsMap.computeIfAbsent(annotClass, key -> new LinkedList<>()).addAll(Arrays.asList(annot));
    }

    private <K, V> V cachedMethodSecurity(ReentrantLock lock, Map<K, V> cache, K key) {
        try {
            lock.lock();
            return cache.get(key);
        } finally {
            lock.unlock();
        }
    }

    private <K, V> V cacheMethodSecurity(ReentrantLock lock, Map<K, V> cache, K key, V value) {
        try {
            lock.lock();
            return cache.putIfAbsent(key, value);
        } finally {
            lock.unlock();
        }
    }

    private ResourceMethodKey resourceMethodKey(InvokedResource invokedResource, Method definitionMethod) {
        Method observedHandlingMethod = invokedResource.handlingMethod().orElse(definitionMethod);
        Class<?> definitionClass = invokedResource.definitionClass()
                .map(SecurityFilter::getRealClass)
                .orElseGet(() -> getRealClass(definitionMethod.getDeclaringClass()));
        Class<?> handlingClass = invokedResource.handlingClass()
                .map(SecurityFilter::getRealClass)
                .orElseGet(() -> getRealClass(observedHandlingMethod.getDeclaringClass()));
        return resourceMethodKey(definitionClass,
                                 handlingClass,
                                 definitionMethod,
                                 observedHandlingMethod,
                                 false,
                                 false);
    }

    private ResourceMethodKey resourceMethodKey(Invocable invocable, boolean rootLocator) {
        Method definitionMethod = invocable.getDefinitionMethod();
        Method handlingMethod = invocable.getHandlingMethod();
        if (handlingMethod == null) {
            handlingMethod = definitionMethod;
        }

        Class<?> handlingClass = getRealClass(invocable.getHandler().getHandlerClass());
        Class<?> definitionClass = rootLocator
                ? jaxRsDefinitionClass(handlingClass)
                : getRealClass(definitionMethod.getDeclaringClass());
        return resourceMethodKey(definitionClass, handlingClass, definitionMethod, handlingMethod, true, false);
    }

    private ResourceMethodKey resourceMethodKey(Class<?> definitionClass,
                                                Class<?> handlingClass,
                                                Method definitionMethod,
                                                Method handlingMethod,
                                                boolean locator,
                                                boolean inheritHandlingClassAnnotations) {
        // Resolve proxy, bridge, and inherited methods only on a cache miss.
        ResourceMethodIdentity identity = new ResourceMethodIdentity(definitionClass,
                                                                     handlingClass,
                                                                     definitionMethod,
                                                                     handlingMethod,
                                                                     locator,
                                                                     inheritHandlingClassAnnotations);
        return resourceMethodKeyCache.computeIfAbsent(identity,
                                                      key -> {
                                                          Method resolvedHandlingMethod = HandlingMethodResolver.resolve(
                                                                  key.handlingClass(),
                                                                  key.definitionMethod(),
                                                                  key.handlingMethod());
                                                          Class<?> resolvedDefinitionClass = resourceDefinitionClass(
                                                                  key.definitionClass(),
                                                                  key.handlingClass(),
                                                                  key.definitionMethod());
                                                          boolean inheritHandlerAnnotations =
                                                                  key.inheritHandlingClassAnnotations()
                                                                          || !resolvedDefinitionClass.equals(
                                                                                  key.handlingClass());
                                                          Class<?> resolvedDefinitionType = null;
                                                          boolean genericResponseType = key.definitionMethod()
                                                                  .getGenericReturnType()
                                                                  instanceof TypeVariable<?>;
                                                          if (key.locator() && genericResponseType) {
                                                              resolvedDefinitionType = resolvedResponseType(
                                                                      key.handlingClass(),
                                                                      key.definitionMethod());
                                                          }
                                                          genericResponseType = resolvedHandlingMethod.getGenericReturnType()
                                                                  instanceof TypeVariable<?>;
                                                          Class<?> resolvedHandlingType = null;
                                                          if (key.locator()
                                                                  && !key.definitionMethod().equals(resolvedHandlingMethod)
                                                                  && genericResponseType) {
                                                              resolvedHandlingType = resolvedResponseType(
                                                                      key.handlingClass(),
                                                                      resolvedHandlingMethod);
                                                          }
                                                          Class<?> resolvedResponseType = covariantInterface(
                                                                  resolvedDefinitionType,
                                                                  resolvedHandlingType);
                                                          return new ResourceMethodKey(
                                                                  resolvedDefinitionClass,
                                                                  key.handlingClass(),
                                                                  key.definitionMethod(),
                                                                  resolvedHandlingMethod,
                                                                  resolvedResponseType,
                                                                  key.locator(),
                                                                  inheritHandlerAnnotations);
                                                      });
    }

    private SecurityDefinition processImplementation(SecurityDefinition current,
                                                     String path,
                                                     String httpMethod,
                                                     ResourceMethodKey resource,
                                                     ClassicMethodAnalysis classicAnalysis) {
        boolean processHandlingMethod = processesHandlingMethod(resource);
        boolean handlingClassAdded = !resource.definitionClass().equals(resource.handlingClass());
        boolean classicClassProcessed = definitionProcessesClass(resource, classicAnalysis.classType());
        ClassicMethodAnalysis handlingClassicAnalysis = classicClassProcessed
                ? ClassicMethodAnalysis.EMPTY
                : classicAnalysis;
        if (processHandlingMethod) {
            current = securityForHandlingClasses(resource, current, handlingClassicAnalysis);
        } else if (handlingClassAdded) {
            current = securityForClass(resource.handlingClass(),
                                       current,
                                       ClassAnnotationScope.DECLARED_ONLY_EXCLUDING_ACCESS_SECURITY,
                                       classicClassResponses(resource.handlingClass(), handlingClassicAnalysis));
        }
        if (!classicClassProcessed
                && !handlingProcessesClass(resource, classicAnalysis.classType(), processHandlingMethod, handlingClassAdded)) {
            applyAnalyzerResponses(current, classicAnalysis.classResponses());
        }

        if (!processHandlingMethod) {
            if (handlingClassAdded) {
                findMethodConfig(UriPath.create(path), httpMethod)
                        .asNode()
                        .ifPresent(current::fromConfig);
            }
            return current;
        }

        Method handlingMethod = resource.handlingMethod();
        return processMethod(current,
                             path,
                             httpMethod,
                             resource,
                             handlingMethod,
                             classicAnalysis);
    }

    private static void addMethodSecurityLevel(SecurityDefinition definition,
                                               Method method,
                                               List<io.helidon.common.types.Annotation> annotations) {
        List<SecurityLevel> securityLevels = definition.securityLevels();
        TypeName declaringType = TypeName.create(getRealClass(method.getDeclaringClass()));
        int levelIndex = securityLevels.size() - 1;
        if (levelIndex >= 0 && declaringType.equals(securityLevels.get(levelIndex).typeName())) {
            SecurityLevel securityLevel = securityLevels.get(levelIndex);
            securityLevels.set(levelIndex, SecurityLevel.builder()
                    .from(securityLevel)
                    .methodName(method.getName())
                    .methodAnnotations(annotations)
                    .build());
            return;
        }
        // Preserve method-last precedence when a custom-only selected or handling class follows the method owner.
        securityLevels.add(SecurityLevel.builder()
                                   .type(declaringType)
                                   .methodName(method.getName())
                                   .methodAnnotations(annotations)
                                   .build());
    }

    private SecurityDefinition securityForHandlingClasses(ResourceMethodKey resource,
                                                          SecurityDefinition parent,
                                                          ClassicMethodAnalysis classicAnalysis) {
        Class<?> handlingClass = resource.handlingClass();
        Class<?> methodClass = getRealClass(resource.handlingMethod().getDeclaringClass());
        boolean includeStandardSecurity = standardSecurityApplies(resource, resource.handlingMethod());
        if (handlingClass.equals(methodClass)) {
            ClassAnnotationScope methodClassScope = includeStandardSecurity
                    ? ClassAnnotationScope.DECLARED_ONLY
                    : ClassAnnotationScope.DECLARED_ONLY_EXCLUDING_STANDARD_SECURITY;
            return securityForClass(handlingClass,
                                    parent,
                                    methodClassScope,
                                    classicClassResponses(handlingClass, classicAnalysis));
        }
        // The concrete handler still participates in custom analysis, but it does not own the inherited method.
        ClassAnnotationScope methodClassScope = includeStandardSecurity
                ? ClassAnnotationScope.ALL
                : ClassAnnotationScope.ALL_EXCLUDING_STANDARD_SECURITY;
        SecurityDefinition current = securityForClass(methodClass,
                                                      parent,
                                                      methodClassScope,
                                                      classicClassResponses(methodClass, classicAnalysis));
        return securityForClass(handlingClass,
                                current,
                                ClassAnnotationScope.DECLARED_ONLY_EXCLUDING_ACCESS_SECURITY,
                                classicClassResponses(handlingClass, classicAnalysis));
    }

    private static boolean processesHandlingMethod(ResourceMethodKey resource) {
        return !resource.definitionMethod().equals(resource.handlingMethod())
                || implementationOwnsDefinitionMethod(resource);
    }

    private static boolean definitionProcessesClass(ResourceMethodKey resource, Class<?> classicClass) {
        if (classicClass == null) {
            return false;
        }
        if (implementationOwnsDefinitionMethod(resource)) {
            return resource.definitionClass().equals(classicClass);
        }
        return resource.definitionClass().equals(classicClass)
                || getRealClass(resource.definitionMethod().getDeclaringClass()).equals(classicClass);
    }

    private static boolean handlingProcessesClass(ResourceMethodKey resource,
                                                  Class<?> classicClass,
                                                  boolean processHandlingMethod,
                                                  boolean handlingClassAdded) {
        if (classicClass == null) {
            return false;
        }
        if (processHandlingMethod) {
            return resource.handlingClass().equals(classicClass)
                    || getRealClass(resource.handlingMethod().getDeclaringClass()).equals(classicClass);
        }
        return handlingClassAdded && resource.handlingClass().equals(classicClass);
    }

    private static AnalyzerResponses classicClassResponses(Class<?> resourceClass,
                                                            ClassicMethodAnalysis classicAnalysis) {
        return getRealClass(resourceClass).equals(classicAnalysis.classType())
                ? classicAnalysis.classResponses()
                : AnalyzerResponses.EMPTY;
    }

    private static boolean implementationOwnsDefinitionMethod(ResourceMethodKey resource) {
        Class<?> methodClass = getRealClass(resource.definitionMethod().getDeclaringClass());
        return resource.definitionMethod().equals(resource.handlingMethod())
                && !methodClass.isInterface()
                && methodClass.equals(resource.handlingClass())
                && !methodClass.isAssignableFrom(resource.definitionClass());
    }

    private static boolean standardSecurityApplies(ResourceMethodKey resource, Method method) {
        return !method.getDeclaringClass().isInterface()
                && method.equals(resource.handlingMethod());
    }

    private static Class<?> resourceDefinitionClass(Class<?> definitionClass,
                                                    Class<?> handlingClass,
                                                    Method definitionMethod) {
        Class<?> methodClass = getRealClass(definitionMethod.getDeclaringClass());
        boolean selectedDefinition = definitionClass.isAssignableFrom(handlingClass);
        if (selectedDefinition && definitionClass.isAnnotationPresent(Path.class)) {
            return definitionClass;
        }
        selectedDefinition = selectedDefinition
                && (definitionClass.isAssignableFrom(methodClass) || methodClass.isAssignableFrom(definitionClass));

        Class<?> current = handlingClass;
        while (current != null) {
            if (current.isAnnotationPresent(Path.class)) {
                return current;
            }
            current = current.getSuperclass();
        }

        if (selectedDefinition
                && !definitionClass.equals(methodClass)
                && (definitionClass.isInterface() || !methodClass.isInterface())) {
            return definitionClass;
        }

        if (methodClass.isInterface()) {
            current = handlingClass;
            while (current != null) {
                for (Class<?> resourceInterface : current.getInterfaces()) {
                    Class<?> selectedInterface = resourceDefinitionInterface(resourceInterface, methodClass, true);
                    if (selectedInterface != null) {
                        return selectedInterface;
                    }
                }
                current = current.getSuperclass();
            }
        }

        if (selectedDefinition && (definitionClass.isInterface() || !methodClass.isInterface())) {
            return definitionClass;
        }

        if (methodClass.isInterface()) {
            Class<?> selectedInterface = null;
            current = handlingClass;
            while (current != null) {
                for (Class<?> resourceInterface : current.getInterfaces()) {
                    Class<?> candidate = resourceDefinitionInterface(resourceInterface, methodClass, false);
                    if (candidate == null
                            || candidate.equals(selectedInterface)
                            || (selectedInterface != null && candidate.isAssignableFrom(selectedInterface))) {
                        continue;
                    }
                    if (selectedInterface != null && !selectedInterface.isAssignableFrom(candidate)) {
                        return methodClass;
                    }
                    selectedInterface = candidate;
                }
                current = current.getSuperclass();
            }
            if (selectedInterface != null) {
                return selectedInterface;
            }
        }
        return methodClass;
    }

    private static Class<?> jaxRsDefinitionClass(Class<?> resourceClass) {
        Class<?> interfaceDefinition = null;
        Class<?> current = resourceClass;
        do {
            if (current.isAnnotationPresent(Path.class)) {
                return current;
            }
            if (interfaceDefinition == null) {
                for (Class<?> candidate : current.getInterfaces()) {
                    if (candidate.isAnnotationPresent(Path.class)) {
                        interfaceDefinition = candidate;
                        break;
                    }
                }
            }
        } while ((current = current.getSuperclass()) != null);
        return interfaceDefinition == null ? resourceClass : interfaceDefinition;
    }

    private static Class<?> locatedResourceType(ResourceMethodKey locator, ResourceMethodKey resource) {
        Class<?> definitionType = locatedResourceType(locator.resolvedResponseType(), resource);
        if (definitionType == null) {
            definitionType = locatedResourceType(locator.definitionMethod(), resource);
        }
        Class<?> handlingType = locatedResourceType(locator.handlingMethod(), resource);
        // Interface hierarchy processing preserves the broader definition when selecting a covariant return.
        return covariantInterface(definitionType, handlingType);
    }

    private static Class<?> covariantInterface(Class<?> definitionType, Class<?> handlingType) {
        if (definitionType == null) {
            return handlingType;
        }
        if (handlingType != null
                && definitionType.isInterface()
                && handlingType.isInterface()
                && !definitionType.equals(handlingType)
                && definitionType.isAssignableFrom(handlingType)) {
            return handlingType;
        }
        return definitionType;
    }

    private ResourceMethodKey locatedResourceKey(ResourceMethodKey locator, ResourceMethodKey resource) {
        Class<?> locatedType = locatedResourceType(locator, resource);
        Class<?> resourceType = resource.definitionClass();
        if (locatedType == null
                || (locatedType.isInterface()
                    && resourceType.isInterface()
                    && locatedType.isAssignableFrom(resourceType))) {
            return resource;
        }
        boolean inheritHandlingClassAnnotations = resource.inheritHandlingClassAnnotations()
                || (!locatedType.equals(resource.definitionClass())
                    && locatedType.isAssignableFrom(resource.definitionClass())
                    && resource.definitionClass().equals(resource.handlingClass()));
        return resourceMethodKey(locatedType,
                                 resource.handlingClass(),
                                 resource.definitionMethod(),
                                 resource.handlingMethod(),
                                 resource.locator(),
                                 inheritHandlingClassAnnotations);
    }

    private static Class<?> locatedResourceType(Method locatorMethod, ResourceMethodKey resource) {
        return locatedResourceType(getRealClass(locatorMethod.getReturnType()), resource);
    }

    private static Class<?> locatedResourceType(Class<?> locatedType, ResourceMethodKey resource) {
        Class<?> methodClass = getRealClass(resource.definitionMethod().getDeclaringClass());
        if (locatedType != null
                && !Object.class.equals(locatedType)
                && locatedType.isAssignableFrom(resource.handlingClass())
                && (locatedType.isAssignableFrom(methodClass) || methodClass.isAssignableFrom(locatedType))) {
            return locatedType;
        }
        return null;
    }

    private static Class<?> resourceDefinitionInterface(Class<?> candidate,
                                                        Class<?> methodClass,
                                                        boolean pathRequired) {
        if (!methodClass.isAssignableFrom(candidate)) {
            return null;
        }
        if (!pathRequired || candidate.isAnnotationPresent(Path.class)) {
            return candidate;
        }
        for (Class<?> parentInterface : candidate.getInterfaces()) {
            Class<?> selectedInterface = resourceDefinitionInterface(parentInterface, methodClass, true);
            if (selectedInterface != null) {
                return selectedInterface;
            }
        }
        return null;
    }

    private SecurityDefinition processMethod(SecurityDefinition current,
                                             String path,
                                             String httpMethod,
                                             ResourceMethodKey resource,
                                             Method method,
                                             ClassicMethodAnalysis classicAnalysis) {
        SecurityDefinition methodDef = current.copyMe();
        findMethodConfig(UriPath.create(path), httpMethod)
                .asNode()
                .ifPresentOrElse(methodDef::fromConfig,
                                 () -> {
                                     Authenticated atn = method.getAnnotation(Authenticated.class);
                                     Authorized atz = method.getAnnotation(Authorized.class);
                                     Audited audited = method.getAnnotation(Audited.class);
                                     methodDef.add(atn);
                                     methodDef.add(atz);
                                     methodDef.add(audited);
                                 });
        TypedElementInfo methodInfo = TypedElementFactory.create(method);
        if (!standardSecurityApplies(resource, method)) {
            methodInfo = TypedElementInfo.builder(methodInfo)
                    .annotations(withoutStandardSecurity(methodInfo.annotations()))
                    .build();
        }
        addMethodSecurityLevel(methodDef, method, methodInfo.annotations());
        for (AnnotationAnalyzer analyzer : analyzers) {
            boolean expandedAnalysis = analyzer.supportsExpandedEndpointAnalysis();
            AnnotationAnalyzer.AnalyzerResponse analyzerResponse;
            if (expandedAnalysis) {
                analyzerResponse = analyzer.analyze(TypeName.create(method.getDeclaringClass()),
                                                    methodInfo,
                                                    current.analyzerResponse(analyzer));
            } else {
                analyzerResponse = classicAnalysis.methodResponses().response(analyzer);
                if (analyzerResponse == null) {
                    continue;
                }
            }
            methodDef.analyzerResponse(analyzer, analyzerResponse);
        }
        return methodDef;
    }

    private AnalyzerResponses analyzeClassicClass(Class<?> resourceClass, SecurityDefinition parent) {
        Class<?> realClass = getRealClass(resourceClass);
        TypeName typeName = TypeName.create(realClass);
        List<io.helidon.common.types.Annotation> annotations = AnnotationFactory.create(realClass);
        Map<AnnotationAnalyzer, AnnotationAnalyzer.AnalyzerResponse> responses = new IdentityHashMap<>();
        for (AnnotationAnalyzer analyzer : analyzers) {
            if (!analyzer.supportsExpandedEndpointAnalysis()) {
                responses.put(analyzer,
                              analyzer.analyze(typeName, annotations, parent.analyzerResponse(analyzer)));
            }
        }
        return new AnalyzerResponses(responses);
    }

    @SuppressWarnings("removal")
    private AnalyzerResponses analyzeClassicResourceMethod(Method method, SecurityDefinition parent) {
        Map<AnnotationAnalyzer, AnnotationAnalyzer.AnalyzerResponse> responses = new IdentityHashMap<>();
        for (AnnotationAnalyzer analyzer : analyzers) {
            if (!analyzer.supportsExpandedEndpointAnalysis()) {
                responses.put(analyzer, analyzer.analyze(method, parent.analyzerResponse(analyzer)));
            }
        }
        return new AnalyzerResponses(responses);
    }

    private AnalyzerResponses analyzeClassicSubResourceMethod(Method method, SecurityDefinition parent) {
        TypeName typeName = TypeName.create(method.getDeclaringClass());
        TypedElementInfo methodInfo = TypedElementFactory.create(method);
        Map<AnnotationAnalyzer, AnnotationAnalyzer.AnalyzerResponse> responses = new IdentityHashMap<>();
        for (AnnotationAnalyzer analyzer : analyzers) {
            if (!analyzer.supportsExpandedEndpointAnalysis()) {
                responses.put(analyzer,
                              analyzer.analyze(typeName, methodInfo, parent.analyzerResponse(analyzer)));
            }
        }
        return new AnalyzerResponses(responses);
    }

    private ClassicMethodAnalysis classicResourceMethodAnalysis(Class<?> appClass,
                                                                ClassicResourceMethodCacheKey methodKey,
                                                                SecurityDefinition applicationDefinition,
                                                                ResourceClassAnalysis classAnalysis) {
        Map<ClassicResourceMethodCacheKey, AnalyzerResponses> cache =
                appClassCacheEntry(appClass).classicResourceMethodAnalysis;
        AnalyzerResponses cached = cachedMethodSecurity(resourceMethodSecurityLock, cache, methodKey);
        if (cached != null) {
            return new ClassicMethodAnalysis(classAnalysis.classType(), classAnalysis.classResponses(), cached);
        }
        SecurityDefinition methodParent = applicationDefinition.copyMe();
        applyAnalyzerResponses(methodParent, classAnalysis.classResponses());
        AnalyzerResponses analysis = analyzeClassicResourceMethod(methodKey.method(), methodParent);
        cached = cacheMethodSecurity(resourceMethodSecurityLock, cache, methodKey, analysis);
        return new ClassicMethodAnalysis(classAnalysis.classType(),
                                         classAnalysis.classResponses(),
                                         cached == null ? analysis : cached);
    }

    private List<ClassicMethodAnalysis> classicSubResourceAnalysis(Class<?> appClass,
                                                                   ClassicSubResourceMethodCacheKey methodKey,
                                                                   List<ResourceMethodKey> resources,
                                                                   SecurityDefinition parent) {
        Map<ClassicSubResourceMethodCacheKey, List<ClassicMethodAnalysis>> cache =
                appClassCacheEntry(appClass).classicSubResourceMethodAnalysis;
        List<ClassicMethodAnalysis> cached = cachedMethodSecurity(subResourceMethodSecurityLock, cache, methodKey);
        if (cached != null) {
            return cached;
        }

        SecurityDefinition current = parent.copyMe();
        List<ClassicMethodAnalysis> stages = new ArrayList<>();
        for (ResourceMethodKey resource : resources) {
            Method method = resource.definitionMethod();
            AnalyzerResponses classResponses = analyzeClassicClass(method.getDeclaringClass(), current);
            applyAnalyzerResponses(current, classResponses);
            AnalyzerResponses methodResponses = analyzeClassicSubResourceMethod(method, current);
            applyAnalyzerResponses(current, methodResponses);
            stages.add(new ClassicMethodAnalysis(getRealClass(method.getDeclaringClass()),
                                                 classResponses,
                                                 methodResponses));
        }
        List<ClassicMethodAnalysis> analysis = List.copyOf(stages);
        cached = cacheMethodSecurity(subResourceMethodSecurityLock, cache, methodKey, analysis);
        return cached == null ? analysis : cached;
    }

    private SecurityDefinition applyAnalyzerResponses(SecurityDefinition definition, AnalyzerResponses responses) {
        for (AnnotationAnalyzer analyzer : analyzers) {
            AnnotationAnalyzer.AnalyzerResponse response = responses.response(analyzer);
            if (response != null) {
                definition.analyzerResponse(analyzer, response);
            }
        }
        return definition;
    }

    private CacheEntry appClassCacheEntry(Class<?> appClass) {
        try {
            applicationClassCacheLock.lock();
            return applicationClassCache.computeIfAbsent(appClass, c -> {
                SecurityDefinition appClassSecurity = securityForClass(c,
                                                                       null,
                                                                       ClassAnnotationScope.ALL_EXCLUDING_STANDARD_SECURITY);
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

    private Map<ResourceMethodCacheKey, SecurityDefinition> resourceMethodSecurity(Class<?> appClass) {
        return appClassCacheEntry(appClass).resourceMethodSecurity;
    }

    private Map<SubResourceMethodCacheKey, SecurityDefinition> subResourceMethodSecurity(Class<?> appClass) {
        return appClassCacheEntry(appClass).subResourceMethodSecurity;
    }

    private static List<Class<?>> resourceInterfaces(Class<?> definitionClass) {
        List<Class<?>> result = new ArrayList<>();
        Set<Class<?>> processed = new HashSet<>();
        addResourceInterface(definitionClass, result, processed);
        return List.copyOf(result);
    }

    private static Class<?> resolvedResponseType(Class<?> handlingClass, Method method) {
        Class<?> rawResponseType = getRealClass(method.getReturnType());
        Class<?> current = handlingClass;
        while (current != null) {
            for (Class<?> candidate : resourceInterfaces(current)) {
                if (!method.getDeclaringClass().isAssignableFrom(candidate)) {
                    continue;
                }
                Class<?> responseType = getRealClass(ReflectionHelper.resolveGenericType(candidate,
                                                                                         method.getDeclaringClass(),
                                                                                         method.getReturnType(),
                                                                                         method.getGenericReturnType())
                                                             .rawClass());
                if (!rawResponseType.equals(responseType)) {
                    return responseType;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static void addResourceInterface(Class<?> resourceInterface,
                                             List<Class<?>> resourceClasses,
                                             Set<Class<?>> processed) {
        if (!processed.add(resourceInterface)) {
            return;
        }
        for (Class<?> parentInterface : resourceInterface.getInterfaces()) {
            addResourceInterface(parentInterface, resourceClasses, processed);
        }
        resourceClasses.add(resourceInterface);
    }

    private enum ClassAnnotationScope {
        ALL,
        ALL_EXCLUDING_STANDARD_SECURITY,
        DECLARED_ONLY,
        DECLARED_ONLY_EXCLUDING_STANDARD_SECURITY,
        DECLARED_ONLY_EXCLUDING_ACCESS_SECURITY,
        INHERITED_ONLY
    }

    private record ResourceMethodCacheKey(ResourceMethodKey resource, String configKey) {
    }

    private record SubResourceMethodCacheKey(List<ResourceMethodKey> resourcePath, String configKey) {
    }

    private record ClassicResourceMethodCacheKey(Class<?> resourceClass, Method method, String configKey) {
    }

    private record ClassicSubResourceMethodCacheKey(Class<?> finalResourceClass,
                                                     List<Method> methods,
                                                     String configKey) {

        private ClassicSubResourceMethodCacheKey {
            methods = List.copyOf(methods);
        }
    }

    /**
     * Cache entry for main cache. Includes application class security definition as
     * well as security definitions for resource classes, methods and sub-resources.
     */
    private static class CacheEntry {
        private SecurityDefinition appClassSecurity;
        private final Map<Class<?>, AnalyzerResponses> classicResourceClassAnalysis = new HashMap<>();
        private final Map<ClassicResourceMethodCacheKey, AnalyzerResponses>
                classicResourceMethodAnalysis = new HashMap<>();
        private final Map<ClassicSubResourceMethodCacheKey, List<ClassicMethodAnalysis>>
                classicSubResourceMethodAnalysis = new HashMap<>();
        private final Map<ResourceClassKey, SecurityDefinition> resourceClassSecurity = new HashMap<>();
        private final Map<ResourceMethodCacheKey, SecurityDefinition> resourceMethodSecurity = new HashMap<>();
        private final Map<SubResourceMethodCacheKey, SecurityDefinition> subResourceMethodSecurity = new HashMap<>();
    }

    private record ResourceMethodKey(Class<?> definitionClass,
                                     Class<?> handlingClass,
                                     Method definitionMethod,
                                     Method handlingMethod,
                                     Class<?> resolvedResponseType,
                                     boolean locator,
                                     boolean inheritHandlingClassAnnotations) {
    }

    private record ResourceClassKey(Class<?> definitionClass,
                                    Class<?> handlingClass,
                                    Class<?> definitionMethodClass,
                                    Class<?> analyzerClass,
                                    boolean implementationOwnsDefinitionMethod,
                                    boolean definitionMethodStandardSecurityApplies,
                                    boolean inheritHandlingClassAnnotations) {

        private static ResourceClassKey create(ResourceMethodKey resource, Class<?> analyzerClass) {
            return new ResourceClassKey(resource.definitionClass(),
                                        resource.handlingClass(),
                                        getRealClass(resource.definitionMethod().getDeclaringClass()),
                                        getRealClass(analyzerClass),
                                        SecurityFilter.implementationOwnsDefinitionMethod(resource),
                                        SecurityFilter.standardSecurityApplies(resource, resource.definitionMethod()),
                                        resource.inheritHandlingClassAnnotations());
        }
    }

    private record ResourceMethodIdentity(Class<?> definitionClass,
                                          Class<?> handlingClass,
                                          Method definitionMethod,
                                          Method handlingMethod,
                                          boolean locator,
                                          boolean inheritHandlingClassAnnotations) {
    }

    private record ResourceClassAnalysis(SecurityDefinition definition,
                                         Class<?> classType,
                                         AnalyzerResponses classResponses) {
    }

    private record ClassicMethodAnalysis(Class<?> classType,
                                         AnalyzerResponses classResponses,
                                         AnalyzerResponses methodResponses) {
        private static final ClassicMethodAnalysis EMPTY = new ClassicMethodAnalysis(null,
                                                                                      AnalyzerResponses.EMPTY,
                                                                                      AnalyzerResponses.EMPTY);
    }

    private static final class AnalyzerResponses {
        private static final AnalyzerResponses EMPTY = new AnalyzerResponses(Map.of());

        private final Map<AnnotationAnalyzer, AnnotationAnalyzer.AnalyzerResponse> responses;

        private AnalyzerResponses(Map<AnnotationAnalyzer, AnnotationAnalyzer.AnalyzerResponse> responses) {
            this.responses = Collections.unmodifiableMap(new IdentityHashMap<>(responses));
        }

        private AnnotationAnalyzer.AnalyzerResponse response(AnnotationAnalyzer analyzer) {
            return responses.get(analyzer);
        }
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
