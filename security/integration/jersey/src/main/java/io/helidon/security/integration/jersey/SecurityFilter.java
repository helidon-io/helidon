/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.integration.jersey;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Priority;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.Path;
import javax.ws.rs.Priorities;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
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

import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerConfig;
import org.glassfish.jersey.server.model.AbstractResourceModelVisitor;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.model.RuntimeResource;

/**
 * A filter that handles authentication and authorization.
 */
@Priority(Priorities.AUTHENTICATION)
@ConstrainedTo(RuntimeType.SERVER)
public class SecurityFilter extends SecurityFilterCommon implements ContainerRequestFilter, ContainerResponseFilter {
    private static final Logger LOGGER = Logger.getLogger(SecurityFilter.class.getName());

    private final Map<Class<?>, SecurityDefinition> resourceClassSecurity = new ConcurrentHashMap<>();
    private final Map<Method, SecurityDefinition> resourceMethodSecurity = new ConcurrentHashMap<>();
    private final Map<String, SecurityDefinition> subResourceMethodSecurity = new ConcurrentHashMap<>();

    @Context
    private ServerConfig serverConfig;

    @Context
    private SecurityContext securityContext;

    // The filter is in singleton scope, so caching in an instance field is OK
    private SecurityDefinition appWideSecurity;

    private final List<AnnotationAnalyzer> analyzers = new LinkedList<>();

    /**
     * Default constructor to be used by Jersey when creating an instance of this class.
     */
    public SecurityFilter() {
        loadAnalyzers();
    }

    // due to a bug in Jersey @Context in constructor injection is failing
    // this method is needed for unit tests
    SecurityFilter(FeatureConfig featureConfig,
                   Security security,
                   ServerConfig serverConfig,
                   SecurityContext securityContext) {

        super(security, featureConfig);

        this.serverConfig = serverConfig;
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
        Class<?> appClass = getOriginalApplication().getClass();

        // we must initialize the analyzers before using them in appWideSecurity
        Config analyzersConfig = config("jersey.analyzers");
        analyzers.forEach(analyzer -> analyzer.init(analyzersConfig));

        this.appWideSecurity = securityForClass(appClass, null);
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (featureConfig().shouldUsePrematchingAuthentication() && featureConfig().shouldUsePrematchingAuthorization()) {
            return;
        }
        // only filter when not pre-matching (see SecurityPreMatchingFilter)
        doFilter(request, securityContext);
    }

    @Override
    protected void processSecurity(ContainerRequestContext request,
                                   FilterContext filterContext,
                                   SecurityTracing tracing,
                                   SecurityContext securityContext) {

        // if we use pre-matching authentication, skip this step
        if (!featureConfig().shouldUsePrematchingAuthentication()) {
            /*
             * Authentication
             */
            authenticate(filterContext, securityContext, tracing.atnTracing());
            LOGGER.finest(() -> "Filter after authentication. Should finish: " + filterContext.isShouldFinish());
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

            LOGGER.finest(() -> "Filter completed (after authorization)");
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        javax.ws.rs.core.SecurityContext jSecurityContext = requestContext.getSecurityContext();
        if (null == jSecurityContext) {
            return;
        }

        JerseySecurityContext jerseySecurityContext;
        if (jSecurityContext instanceof JerseySecurityContext) {
            jerseySecurityContext = (JerseySecurityContext) jSecurityContext;
        } else {
            return;
        }

        FilterContext fc = (FilterContext) requestContext.getProperty(PROP_FILTER_CONTEXT);
        SecurityDefinition methodSecurity = jerseySecurityContext.methodSecurity();
        SecurityContext securityContext = jerseySecurityContext.securityContext();

        if (fc.isExplicitAtz() && !securityContext.atzChecked()) {
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
                    responseContext.setEntity("Authorization was marked as explicit, yet it was never called in resource method");
                } else {
                    responseContext.setEntity("");
                }
                responseContext.setStatus(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
                LOGGER.severe("Authorization failure. Request for" + fc.getResourcePath() + " has failed, as it was marked"
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
    protected FilterContext initRequestFiltering(ContainerRequestContext requestContext) {
        FilterContext context = new FilterContext();
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
    protected Logger logger() {
        return LOGGER;
    }

    private SecurityDefinition securityForClass(Class<?> theClass, SecurityDefinition parent) {
        Class<?> realClass = getRealClass(theClass);
        Authenticated atn = realClass.getAnnotation(Authenticated.class);
        Authorized atz = realClass.getAnnotation(Authorized.class);
        Audited audited = realClass.getAnnotation(Audited.class);

        // as sometimes we may want to prevent calls to authorization provider unless
        // explicitly invoked by developer
        SecurityDefinition definition = (
                (null == parent)
                        ? new SecurityDefinition(featureConfig().shouldAuthorizeAnnotatedOnly())
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

        return definition;
    }

    /**
     * Returns the real class of this object, skipping proxies.
     *
     * @param object The object.
     * @return Its class.
     */
    private Class<?> getRealClass(Class<?> object) {
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
            if (subResourceMethodSecurity.containsKey(fullPath)) {
                return subResourceMethodSecurity.get(fullPath);
            }

            // now process each definition method and class
            SecurityDefinition current = appWideSecurity;

            for (Method method : methodsToProcess) {
                Class<?> clazz = method.getDeclaringClass();
                current = securityForClass(clazz, current);
                Authenticated atn = method.getAnnotation(Authenticated.class);
                Authorized atz = method.getAnnotation(Authorized.class);
                Audited audited = method.getAnnotation(Audited.class);

                SecurityDefinition methodDef = current.copyMe();
                methodDef.add(atn);
                methodDef.add(atz);
                methodDef.add(audited);

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

            subResourceMethodSecurity.put(fullPath, current);
            return current;
        }

        if (resourceMethodSecurity.containsKey(definitionMethod)) {
            return resourceMethodSecurity.get(definitionMethod);
        }

        SecurityDefinition definition = this.resourceClassSecurity
                .computeIfAbsent(definitionClass, aClass -> securityForClass(definitionClass, appWideSecurity));

        Authenticated atn = definitionMethod.getAnnotation(Authenticated.class);
        Authorized atz = definitionMethod.getAnnotation(Authorized.class);
        Audited audited = definitionMethod.getAnnotation(Audited.class);

        SecurityDefinition methodDef = definition.copyMe();
        methodDef.add(atn);
        methodDef.add(atz);
        methodDef.add(audited);

        int index = methodDef.getSecurityLevels().size() - 1;
        SecurityLevel currentSecurityLevel = methodDef.getSecurityLevels().get(index);
        Map<Class<? extends Annotation>, List<Annotation>> methodLevelAnnots = new HashMap<>();
        addCustomAnnotations(methodLevelAnnots, definitionMethod);

        methodDef.getSecurityLevels().set(index, SecurityLevel.create(currentSecurityLevel)
                .withMethodName(definitionMethod.getName())
                .withMethodAnnotations(methodLevelAnnots)
                .build());

        resourceMethodSecurity.put(definitionMethod, methodDef);

        for (AnnotationAnalyzer analyzer : analyzers) {
            AnnotationAnalyzer.AnalyzerResponse analyzerResponse = analyzer.analyze(definitionMethod,
                                                                                    definition.analyzerResponse(analyzer));

            methodDef.analyzerResponse(analyzer, analyzerResponse);
        }

        return methodDef;
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

    private Application getOriginalApplication() {
        // Unfortunately the following logic is very "implementation aware". We need the original instance of
        // javax.ws.rs.core.Application to get the @Authenticated annotation instance if present.
        // Jersey server configuration is immutable and a defensive copy is done with every change.
        // However, the original Application instance is always present and hidden deep in the ServerConfig implementation.

        // ResourceConfig is always the implementation of ServerConfig
        if (!(serverConfig instanceof ResourceConfig)) {
            throw new IllegalStateException("Could not get Application instance. Incompatible version of Jersey?");
        }

        ResourceConfig resourceConfig = (ResourceConfig) serverConfig;
        Application application = resourceConfig.getApplication();

        while (application instanceof ResourceConfig) {
            Application wrappedApplication = ((ResourceConfig) application).getApplication();
            //noinspection ObjectEquality
            if (wrappedApplication == application) {
                break;
            }
            application = wrappedApplication;
        }

        return application;
    }

    // unit test method
    List<AnnotationAnalyzer> analyzers() {
        return this.analyzers;
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
