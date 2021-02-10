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

import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.core.GenericType;

import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.annotations.Authorized;

import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.internal.inject.ReferencingFactory;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.process.internal.RequestScoped;

/**
 * Integration of Security module with Jersey.
 * <p>
 * Register this as you would any other feature, e.g.:
 * <pre>
 * ResourceConfig resourceConfig = new ResourceConfig();
 * // register JAX-RS resource
 * resourceConfig.register(MyResource.class);
 * // integrate security
 * resourceConfig.register(new SecurityFeature(buildSecurity()));
 * </pre>
 */
@ConstrainedTo(RuntimeType.SERVER)
public final class SecurityFeature implements Feature {
    private final Security security;
    private final FeatureConfig featureConfig;

    /**
     * Create a new instance of security feature for a security component.
     *
     * This constructor is workaround solution for Jersey instantiation problem.
     */
    public SecurityFeature() {
        this.security = null;
        this.featureConfig = null;
    }

    /**
     * Create a new instance of security feature for a security component.
     *
     * @param security Fully configured security component to integrate with Jersey
     */
    public SecurityFeature(Security security) {
        this.security = security;
        this.featureConfig = new FeatureConfig(builder(security).config(security.configFor("jersey")));
    }

    private SecurityFeature(Builder builder) {
        this.security = builder.security;
        this.featureConfig = new FeatureConfig(builder);
    }

    /**
     * Builder for {@link SecurityFeature}.
     *
     * @param security Security instance to create this feature for (cannot build a feature without security instance)
     * @return Builder to configure feature
     */
    public static Builder builder(Security security) {
        return new Builder(security);
    }

    @Override
    public boolean configure(FeatureContext context) {
        RuntimeType runtimeType = context.getConfiguration().getRuntimeType();
        //register server
        if (runtimeType != RuntimeType.SERVER) {
            return false;
        }

        context.register(SecurityPreMatchingFilter.class);
        context.register(SecurityFilter.class);

        //allow injection of security context (our, not Jersey)
        context.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bindFactory(SecurityContextRefFactory.class)
                        .to(SecurityContext.class)
                        .proxy(true)
                        .proxyForSameScope(false)
                        .in(RequestScoped.class);

                bindFactory(ReferencingFactory.<SecurityContext>referenceFactory())
                        .to(new GenericType<Ref<SecurityContext>>() { })
                        .in(RequestScoped.class);

                bind(security).to(Security.class);
                bind(featureConfig).to(FeatureConfig.class);
            }
        });

        return true;
    }

    FeatureConfig featureConfig() {
        return featureConfig;
    }

    /**
     * {@link SecurityFeature} fluent API builder.
     */
    public static final class Builder implements io.helidon.common.Builder<SecurityFeature> {
        private final Security security;
        private final List<QueryParamHandler> queryParamHandlers = new LinkedList<>();
        private boolean authorizeAnnotatedOnly = FeatureConfig.DEFAULT_ATZ_ANNOTATED_ONLY;
        private boolean authenticateAnnotatedOnly = FeatureConfig.DEFAULT_ATN_ANNOTATED_ONLY;
        private boolean debug = FeatureConfig.DEFAULT_DEBUG;
        private boolean prematchingAuthorization = FeatureConfig.DEFAULT_PREMATCHING_ATZ;
        private boolean prematchingAuthentication = FeatureConfig.DEFAULT_PREMATCHING_ATN;
        private boolean useAbortWith = FeatureConfig.DEFAULT_USE_ABORT_WITH;
        private boolean authenticationRequiredIfPresent = FeatureConfig.DEFAULT_ATN_REQ_IF_PRESENT;

        private Builder(Security security) {
            this.security = security;
        }

        /**
         * Whether to authorize only annotated methods (with {@link Authorized} annotation) or all.
         * When using {@link #usePrematchingAuthorization(boolean)}
         * this method is ignored.
         *
         * @param authzOnly if set to true, authorization will be performed on annotated methods only, defaults to false
         * @return updated builder instance
         */
        public Builder authorizeAnnotatedOnly(boolean authzOnly) {
            this.authorizeAnnotatedOnly = authzOnly;
            return this;
        }

        /**
         * Whether to authorize only annotated methods (with {@link io.helidon.security.annotations.Authenticated} annotation or all.
         * When using {@link #usePrematchingAuthentication(boolean)} this method is ignored.
         * By default only annotated methods (annotation may be also on Application class or resource class) are authenticated.
         *
         * @param authnOnly if set to false, authentication will be performed for all requests, defaults to true
         * @return updated builder instance
         */
        public Builder authenticateAnnotatedOnly(boolean authnOnly) {
            this.authenticateAnnotatedOnly = authnOnly;
            return this;
        }

        /**
         * Add a new handler to extract query parameter and store it in security request header.
         *
         * @param handler handler to extract data
         * @return updated builder instance
         */
        public Builder addQueryParamHandler(QueryParamHandler handler) {
            this.queryParamHandlers.add(handler);
            return this;
        }

        /**
         * Add handlers to extract query parameters and store them in security request header.
         *
         * @param handlers handlers to extract data
         * @return updated builder instance
         */
        public Builder addQueryParamHandlers(Iterable<QueryParamHandler> handlers) {
            handlers.forEach(this::addQueryParamHandler);
            return this;
        }

        /**
         * Configure whether pre-matching or post-matching filter is used to authenticate requests.
         * Defaults to post-matching, as we have access to information about resource class and method that is
         * invoked, allowing us to use annotations defined on these.
         * When switched to prematching, the security is an on/off switch - all resources are protected the
         * same way.
         *
         * @param usePrematching whether to use pre-matching filter instead of post-matching
         * @return updated builder instance
         */
        public Builder usePrematchingAuthentication(boolean usePrematching) {
            this.prematchingAuthentication = usePrematching;
            return this;
        }

        /**
         * Configure whether pre-matching or post-matching filter is used to authorize requests.
         * Defaults to post-matching, as we have access to information about resource class and method that is
         * invoked, allowing us to use annotations defined on these.
         * When switched to prematching, the security is an on/off switch - all resources are protected the
         * same way.
         *
         * When set to true, authentication will be prematching as well.
         *
         * @param usePrematching whether to use pre-matching filter instead of post-matching
         * @return updated builder instance
         */
        public Builder usePrematchingAuthorization(boolean usePrematching) {
            this.prematchingAuthorization = usePrematching;
            return this;
        }

        /**
         *
         *
         * @param atnReqIfPresent
         * @return
         */
        private Builder authenticationRequiredIfPresent(Boolean atnReqIfPresent) {
            this.authenticationRequiredIfPresent = atnReqIfPresent;
            return this;
        }

        /**
         * Set debugging on.
         * Will return description from response in entity.
         *
         * @return updated builder instance
         */
        public Builder debug() {
            this.debug = true;
            return this;
        }

        /**
         * When set to {@code true} (which is the default behavior, the security filter
         * would use {@link org.glassfish.jersey.server.ContainerRequest#abortWith(javax.ws.rs.core.Response)} to
         * abort request and configure a security response.
         * <p>
         * When set to {@code false}, the security filter would throw an {@link javax.ws.rs.WebApplicationException} instead.
         * Such an exception can be handled by a custom error handler.
         *
         * @param useAbortWith set to {@code false} to use exceptions, by default uses abortWith on request
         * @return updated builder instance
         */
        public Builder useAbortWith(boolean useAbortWith) {
            this.useAbortWith = useAbortWith;
            return this;
        }

        /**
         * Update this builder from configuration.
         * Expects:
         * <ul>
         * <li>authorize-annotated-only: see {@link #authorizeAnnotatedOnly(boolean)}</li>
         * <li>query-params: see {@link #addQueryParamHandler(QueryParamHandler)}</li>
         * </ul>
         * Example:
         * <pre>
         *  security:
         *    jersey:
         *      defaults:
         *      # If set to true, only annotated (@Authenticated) resources will be authorized
         *      # By default, every request is sent to authorization provider
         *      authorize-annotated-only: false
         *      # query parameters will be extracted from request
         *      # and sent to authentication and authorization providers
         *      # as headers. These will NOT be available to application
         *      # as headers.
         *      query-params:
         *        - name: "basicAuth"
         *          header: "Authorization"
         * </pre>
         *
         * @param config configuration set to key "jersey" (see example above)
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("prematching-authentication").asBoolean().ifPresent(this::usePrematchingAuthentication);
            config.get("prematching-authorization").asBoolean().ifPresent(this::usePrematchingAuthorization);
            config.get("use-abort-with").asBoolean().ifPresent(this::useAbortWith);
            config.get("atn-required-if-present").asBoolean().ifPresent(this::authenticationRequiredIfPresent);

            Config myConfig = config.get("defaults");
            myConfig.get("authorize-annotated-only").asBoolean().ifPresent(this::authorizeAnnotatedOnly);
            myConfig.get("authenticate-annotated-only").asBoolean().ifPresent(this::authenticateAnnotatedOnly);
            myConfig.get("query-params").asList(QueryParamHandler.class).ifPresent(this::addQueryParamHandlers);
            myConfig.get("debug").asBoolean().filter(bool -> bool).ifPresent(bool -> this.debug());

            return this;
        }

        /**
         * Build this configuration into an instance.
         *
         * @return feature to register with Jersey
         */
        @Override
        public SecurityFeature build() {
            return new SecurityFeature(this);
        }

        Security security() {
            return security;
        }

        List<QueryParamHandler> queryParamHandlers() {
            return queryParamHandlers;
        }

        boolean isAuthorizeAnnotatedOnly() {
            return authorizeAnnotatedOnly;
        }

        boolean isAuthenticateAnnotatedOnly() {
            return authenticateAnnotatedOnly;
        }

        boolean isDebug() {
            return debug;
        }

        boolean isPrematchingAuthorization() {
            return prematchingAuthorization;
        }

        boolean isPrematchingAuthentication() {
            return prematchingAuthentication;
        }

        boolean useAbortWith() {
            return useAbortWith;
        }

        boolean authenticationRequiredIfPresent() {
            return authenticationRequiredIfPresent;
        }
    }

    private static class SecurityContextRefFactory extends ReferencingFactory<SecurityContext> {
        @Inject
        SecurityContextRefFactory(Provider<Ref<SecurityContext>> referenceFactory) {
            super(referenceFactory);
        }
    }
}
