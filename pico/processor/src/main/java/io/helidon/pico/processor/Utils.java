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

package io.helidon.pico.processor;

import java.util.Collection;
import java.util.List;

class Utils {

    static final String PICO_CONFIGURED_BY = "io.helidon.pico.config.api.ConfiguredBy";
    static final String PICO_CONTRACT = "io.helidon.pico.Contract";
    static final String PICO_EXTERNAL_CONTRACTS = "io.helidon.pico.ExternalContracts";
    static final String PICO_INTERCEPTED = "io.helidon.pico.Intercepted";

    static final String JAKARTA_APPLICATION_SCOPED = "jakarta.enterprise.context.ApplicationScoped";
    static final String JAKARTA_INJECT = "jakarta.inject.Inject";
    static final String JAKARTA_POST_CONSTRUCT = "jakarta.annotation.PostConstruct";
    static final String JAKARTA_PRE_DESTROY = "jakarta.annotation.PreDestroy";
    static final String JAKARTA_PRIORITY = "jakarta.annotation.Priority";
    static final String JAKARTA_SCOPE = "jakarta.inject.Scope";
    static final String JAKARTA_SINGLETON = "jakarta.inject.Singleton";

    static final String JAKARTA_CDI_ACTIVATE_REQUEST_CONTEXT = "jakarta.enterprise.context.control.ActivateRequestContext";
    static final String JAKARTA_CDI_ALTERNATIVE = "jakarta.enterprise.inject.Alternative";
    static final String JAKARTA_CDI_BEFORE_DESTROYED = "jakarta.enterprise.context.BeforeDestroyed";
    static final String JAKARTA_CDI_CONVERSATION_SCOPED = "jakarta.enterprise.context.ConversationScoped";
    static final String JAKARTA_CDI_DEPENDENT = "jakarta.enterprise.context.Dependent";
    static final String JAKARTA_CDI_DESTROYED = "jakarta.enterprise.context.Destroyed";
    static final String JAKARTA_CDI_DISPOSES = "jakarta.enterprise.inject.Disposes";
    static final String JAKARTA_CDI_INITIALIZED = "jakarta.enterprise.context.Initialized";
    static final String JAKARTA_CDI_INTERCEPTED = "jakarta.enterprise.inject.Intercepted";
    static final String JAKARTA_CDI_MODEL = "jakarta.enterprise.inject.Model";
    static final String JAKARTA_CDI_NONBINDING = "jakarta.enterprise.util.Nonbinding";
    static final String JAKARTA_CDI_NORMAL_SCOPE = "jakarta.enterprise.context.NormalScope";
    static final String JAKARTA_CDI_OBSERVES = "jakarta.enterprise.event.Observes";
    static final String JAKARTA_CDI_OBSERVES_ASYNC = "jakarta.enterprise.event.ObservesAsync";
    static final String JAKARTA_CDI_PRODUCES = "jakarta.enterprise.inject.Produces";
    static final String JAKARTA_CDI_REQUEST_SCOPED = "jakarta.enterprise.context.RequestScoped";
    static final String JAKARTA_CDI_SESSION_SCOPED = "jakarta.enterprise.context.SessionScoped";
    static final String JAKARTA_CDI_SPECIALIZES = "jakarta.enterprise.inject.Specializes";
    static final String JAKARTA_CDI_STEREOTYPE = "jakarta.enterprise.inject.Stereotype";
    static final String JAKARTA_CDI_TRANSIENT_REFERENCE = "jakarta.enterprise.inject.TransientReference";
    static final String JAKARTA_CDI_TYPED = "jakarta.enterprise.inject.Typed";
    static final String JAKARTA_CDI_VETOED = "jakarta.enterprise.inject.Vetoed";

    static final String JAVAX_APPLICATION_SCOPED = "javax.enterprise.context.ApplicationScoped";
    static final String JAVAX_INJECT = "javax.inject.Inject";
    static final String JAVAX_POST_CONSTRUCT = "javax.annotation.PostConstruct";
    static final String JAVAX_PRE_DESTROY = "javax.annotation.PreDestroy";
    static final String JAVAX_PRIORITY = "javax.annotation.Priority";
    static final String JAVAX_SINGLETON = "javax.inject.Singleton";

    private Utils() {
    }

    static <T> Collection<T> nonNull(Collection<T> coll) {
        return (coll == null) ? List.of() : coll;
    }
}
