/*
 * Copyright (c) 2017, 2025 Oracle and/or its affiliates.
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

package io.helidon.jersey.webserver;

import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.core.GenericType;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.internal.inject.ReferencingFactory;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.process.internal.RequestScoped;

/**
 * An internal binder to enable WebServer specific types injection.
 * <p>
 * This binder allows to inject underlying WebServer HTTP request and response instances.
 */
class WebServerBinder extends AbstractBinder {
    // Inspired by {@code GrizzlyHttpContainer.GrizzlyBinder} from Jersey to Grizzly integration.

    @Override
    protected void configure() {
        bindFactory(WebServerRequestReferencingFactory.class).to(ServerRequest.class)
                .proxy(true).proxyForSameScope(false)
                .in(RequestScoped.class);
        bindFactory(ReferencingFactory.<ServerRequest>referenceFactory()).to(new GenericType<Ref<ServerRequest>>() { })
                .in(RequestScoped.class);

        bindFactory(WebServerResponseReferencingFactory.class).to(ServerResponse.class)
                .proxy(true).proxyForSameScope(false)
                .in(RequestScoped.class);
        bindFactory(ReferencingFactory.<ServerResponse>referenceFactory()).to(new GenericType<Ref<ServerResponse>>() { })
                .in(RequestScoped.class);
    }

    private static class WebServerRequestReferencingFactory extends ReferencingFactory<ServerRequest> {

        @Inject
        WebServerRequestReferencingFactory(final Provider<Ref<ServerRequest>> referenceFactory) {
            super(referenceFactory);
        }
    }

    private static class WebServerResponseReferencingFactory extends ReferencingFactory<ServerResponse> {

        @Inject
        WebServerResponseReferencingFactory(final Provider<Ref<ServerResponse>> referenceFactory) {
            super(referenceFactory);
        }
    }
}
