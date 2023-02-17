/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tools;

/**
 * Type name constants.
 * <p>
 * This should always be used instead of dependency on the annotation type.
 */
public final class TypeNames {
    /**
     * Package prefix {@value}.
     */
    public static final String PREFIX_JAKARTA = "jakarta.";
    /**
     * Package prefix {@value}.
     */
    public static final String PREFIX_JAVAX = "javax.";

    /**
     * Pico {@value} annotation.
     */
    public static final String PICO_APPLICATION = "io.helidon.pico.Application";

    /**
     * Pico {@value} annotation.
     */
    public static final String PICO_CONFIGURED_BY = "io.helidon.pico.config.api.ConfiguredBy";
    /**
     * Pico {@value} annotation.
     */
    public static final String PICO_CONTRACT = "io.helidon.pico.Contract";
    /**
     * Pico {@value} annotation.
     */
    public static final String PICO_EXTERNAL_CONTRACTS = "io.helidon.pico.ExternalContracts";
    /**
     * Pico {@value} annotation.
     */
    public static final String PICO_INTERCEPTED = "io.helidon.pico.Intercepted";
    /**
     * Pico {@value} annotation.
     */
    public static final String PICO_MODULE = "io.helidon.pico.Module";

    /**
     * Jakarta {@value} annotation.
     */
    public static final String JAKARTA_APPLICATION_SCOPED = "jakarta.enterprise.context.ApplicationScoped";
    /**
     * Jakarta {@value} annotation.
     */
    public static final String JAKARTA_INJECT = "jakarta.inject.Inject";
    /**
     * Jakarta {@value} annotation.
     */
    public static final String JAKARTA_MANAGED_BEAN = "jakarta.annotation.ManagedBean";
    /**
     * Jakarta {@value} annotation.
     */
    public static final String JAKARTA_POST_CONSTRUCT = "jakarta.annotation.PostConstruct";
    /**
     * Jakarta {@value} annotation.
     */
    public static final String JAKARTA_PRE_DESTROY = "jakarta.annotation.PreDestroy";
    /**
     * Jakarta {@value} annotation.
     */
    public static final String JAKARTA_PRIORITY = "jakarta.annotation.Priority";
    /**
     * Jakarta {@value} type.
     */
    public static final String JAKARTA_PROVIDER = "jakarta.inject.Provider";
    /**
     * Jakarta {@value} annotation.
     */
    public static final String JAKARTA_QUALIFIER = "jakarta.inject.Qualifier";
    /**
     * Jakarta {@value} annotation.
     */
    public static final String JAKARTA_RESOURCE = "jakarta.annotation.Resource";
    /**
     * Jakarta {@value} annotation.
     */
    public static final String JAKARTA_RESOURCES = "jakarta.annotation.Resources";
    /**
     * Jakarta {@value} annotation.
     */
    public static final String JAKARTA_SCOPE = "jakarta.inject.Scope";
    /**
     * Jakarta {@value} annotation.
     */
    public static final String JAKARTA_SINGLETON = "jakarta.inject.Singleton";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_ACTIVATE_REQUEST_CONTEXT = "jakarta.enterprise.context.control.ActivateRequestContext";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_ALTERNATIVE = "jakarta.enterprise.inject.Alternative";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_BEFORE_DESTROYED = "jakarta.enterprise.context.BeforeDestroyed";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_CONVERSATION_SCOPED = "jakarta.enterprise.context.ConversationScoped";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_DEPENDENT = "jakarta.enterprise.context.Dependent";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_DESTROYED = "jakarta.enterprise.context.Destroyed";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_DISPOSES = "jakarta.enterprise.inject.Disposes";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_INITIALIZED = "jakarta.enterprise.context.Initialized";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_INTERCEPTED = "jakarta.enterprise.inject.Intercepted";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_MODEL = "jakarta.enterprise.inject.Model";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_NONBINDING = "jakarta.enterprise.util.Nonbinding";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_NORMAL_SCOPE = "jakarta.enterprise.context.NormalScope";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_OBSERVES = "jakarta.enterprise.event.Observes";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_OBSERVES_ASYNC = "jakarta.enterprise.event.ObservesAsync";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_PRODUCES = "jakarta.enterprise.inject.Produces";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_REQUEST_SCOPED = "jakarta.enterprise.context.RequestScoped";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_SESSION_SCOPED = "jakarta.enterprise.context.SessionScoped";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_SPECIALIZES = "jakarta.enterprise.inject.Specializes";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_STEREOTYPE = "jakarta.enterprise.inject.Stereotype";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_TRANSIENT_REFERENCE = "jakarta.enterprise.inject.TransientReference";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_TYPED = "jakarta.enterprise.inject.Typed";
    /**
     * Jakarta CDI {@value} annotation.
     */
    public static final String JAKARTA_CDI_VETOED = "jakarta.enterprise.inject.Vetoed";
    /**
     * Jakarta legacy {@value} annotation.
     */
    public static final String JAVAX_APPLICATION_SCOPED = "javax.enterprise.context.ApplicationScoped";
    /**
     * Jakarta legacy {@value} annotation.
     */
    public static final String JAVAX_INJECT = "javax.inject.Inject";
    /**
     * Jakarta legacy {@value} annotation.
     */
    public static final String JAVAX_POST_CONSTRUCT = "javax.annotation.PostConstruct";
    /**
     * Jakarta legacy {@value} annotation.
     */
    public static final String JAVAX_PRE_DESTROY = "javax.annotation.PreDestroy";
    /**
     * Jakarta legacy {@value} annotation.
     */
    public static final String JAVAX_QUALIFIER = "javax.inject.Qualifier";
    /**
     * Jakarta legacy {@value} annotation.
     */
    public static final String JAVAX_PRIORITY = "javax.annotation.Priority";
    /**
     * Jakarta legacy {@value} type.
     */
    public static final String JAVAX_PROVIDER = "javax.inject.Provider";
    /**
     * Jakarta legacy {@value} annotation.
     */
    public static final String JAVAX_SINGLETON = "javax.inject.Singleton";

    private TypeNames() {
    }
}
