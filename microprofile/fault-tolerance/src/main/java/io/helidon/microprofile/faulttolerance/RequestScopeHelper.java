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
 */
package io.helidon.microprofile.faulttolerance;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.spi.CDI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.process.internal.RequestContext;
import org.glassfish.jersey.process.internal.RequestScope;
import org.glassfish.jersey.weld.se.WeldRequestScope;

import org.jboss.weld.context.WeldAlterableContext;
import org.jboss.weld.context.api.ContextualInstance;
import org.jboss.weld.context.bound.BoundLiteral;
import org.jboss.weld.context.bound.BoundRequestContext;
import org.jboss.weld.manager.api.WeldManager;

class RequestScopeHelper {

    enum State {
        CLEARED,
        STORED
    }

    private State state = State.CLEARED;

    /**
     * Jersey's request scope object. Will be non-null if request scope is active.
     */
    private RequestScope requestScope;

    /**
     * Jersey's request scope object.
     */
    private RequestContext requestContext;

    /**
     * Jersey's injection manager.
     */
    private InjectionManager injectionManager;

    /**
     * Store access to {@code WeldManager} for instance migration.
     */
    private WeldManager weldManager;

    /**
     * Collection of instances in request scope.
     */
    private Collection<ContextualInstance<?>> requestScopeInstances;

    /**
     * Store request context information from the current thread. State
     * related to Jersey and CDI to handle {@code @Context} and {@code @Inject}
     * injections.
     */
    void saveScope() {
        if (state == State.STORED) {
            throw new IllegalStateException("Request scope state already stored");
        }

        // Collect instances for request scope only
        weldManager = CDI.current().select(WeldManager.class).get();
        if (weldManager != null) {
            for (WeldAlterableContext context : weldManager.getActiveWeldAlterableContexts()) {
                if (context.getScope() == RequestScoped.class) {
                    requestScopeInstances = context.getAllContextualInstances();
                }
            }
        }

        // Jersey scope
        injectionManager = WeldRequestScope.actualInjectorManager.get();        // thread local
        try {
            requestScope = CDI.current().select(RequestScope.class).get();
            requestContext = requestScope.referenceCurrent();
        } catch (Exception e) {
            // Ignored, Jersey request scope not active
        } finally {
            state = State.STORED;
        }
    }

    /**
     * Wraps a supplier into another supplier that actives the request scope
     * before calling it.
     *
     * @param supplier supplier to wrap
     * @return wrapped supplier
     */
    FtSupplier<Object> wrapInScope(FtSupplier<Object> supplier) {
        if (state != State.STORED) {
            throw new IllegalStateException("Request scope state never stored");
        }
        if (requestScope != null && requestContext != null) {       // Jersey and CDI
            return () -> requestScope.runInScope(requestContext,
                    (Callable<?>) (() -> {
                        InjectionManager old = WeldRequestScope.actualInjectorManager.get();
                        BoundRequestContext boundRequestContext = null;
                        try {
                            // requestController.activate();
                            boundRequestContext = migrateRequestContext();
                            WeldRequestScope.actualInjectorManager.set(injectionManager);
                            return supplier.get();
                        } catch (Throwable t) {
                            throw t instanceof Exception ? ((Exception) t) : new RuntimeException(t);
                        } finally {
                            // requestController.deactivate();
                            if (boundRequestContext != null) {
                                boundRequestContext.deactivate();
                            }
                            WeldRequestScope.actualInjectorManager.set(old);
                        }
                    }));
        } else if (weldManager != null) {         // CDI only
            return () -> {
                BoundRequestContext boundRequestContext = null;
                try {
                    boundRequestContext = migrateRequestContext();
                    return supplier.get();
                } finally {
                    if (boundRequestContext != null) {
                        boundRequestContext.deactivate();
                    }
                }
            };
        } else {
            return supplier;
        }
    }

    /**
     * Migrates a CDI request context into the new thread. This method will actually
     * set the instances from the original context into the new context so that code
     * running in the new thread can continue to access request scope beans. Note that
     * if a request scope bean in the original context was not accessed/proxied, it
     * will not be carried over.
     *
     * @return the request context
     */
    private BoundRequestContext migrateRequestContext() {
        if (requestScopeInstances != null) {
            BoundRequestContext requestContext = weldManager.instance()
                    .select(BoundRequestContext.class, BoundLiteral.INSTANCE).get();
            Map<String, Object> requestMap = new HashMap<>();
            requestContext.associate(requestMap);
            requestContext.activate();
            requestContext.clearAndSet(requestScopeInstances);
            return requestContext;
        }
        return null;
    }

    /**
     * Clears internal state saved by calling {@link #saveScope()}.
     */
    void clearScope() {
        if (requestContext != null) {
            requestContext.release();
            requestContext = null;
        }
        if (requestScope != null) {
            CDI.current().destroy(requestScope);
            requestScope = null;
        }
        injectionManager = null;
        if (requestScopeInstances != null) {
            requestScopeInstances.clear();
            requestScopeInstances = null;
        }
        weldManager = null;
        state = State.CLEARED;
    }
}
