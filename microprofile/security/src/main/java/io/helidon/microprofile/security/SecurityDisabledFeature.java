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

package io.helidon.microprofile.security;

import io.helidon.security.Security;
import io.helidon.security.SecurityContext;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.GenericType;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.internal.inject.ReferencingFactory;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.process.internal.RequestScoped;

/**
 * Feature which enables injection of empty {@link SecurityContext} when security is disabled.
 */
@ConstrainedTo(RuntimeType.SERVER)
public final class SecurityDisabledFeature implements Feature {

    private final Security security;

    /**
     * Create a new instance of security feature for a security component.
     *
     * @param security Fully configured security component to integrate with Jersey
     */
    public SecurityDisabledFeature(Security security) {
        this.security = security;
    }

    @Override
    public boolean configure(FeatureContext context) {
        RuntimeType runtimeType = context.getConfiguration().getRuntimeType();
        //register server
        if (runtimeType != RuntimeType.SERVER || security.enabled()) {
            return false;
        }

        context.register(new SecurityDisabledFilter(security));

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

            }
        });
        return true;
    }

    private static class SecurityContextRefFactory extends ReferencingFactory<SecurityContext> {
        @Inject
        SecurityContextRefFactory(Provider<Ref<SecurityContext>> referenceFactory) {
            super(referenceFactory);
        }
    }
}
