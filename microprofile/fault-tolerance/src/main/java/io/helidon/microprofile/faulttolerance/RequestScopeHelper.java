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

import java.util.concurrent.Callable;

import javax.enterprise.context.control.RequestContextController;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;

import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.process.internal.RequestContext;
import org.glassfish.jersey.process.internal.RequestScope;
import org.glassfish.jersey.weld.se.WeldRequestScope;

class RequestScopeHelper {

    enum State {
        CLEARED,
        STORED
    }

    private State state = State.CLEARED;

    /**
     * CDI's request scope controller used for activation/deactivation.
     */
    private RequestContextController requestController;

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
     * Store request context information from the current thread. State
     * related to Jersey and CDI to handle {@code @Context} and {@code @Inject}
     * injections.
     */
    void saveScope() {
        if (state == State.STORED) {
            throw new IllegalStateException("Request scope state already stored");
        }
        // CDI scope
        Instance<RequestContextController> rcc = CDI.current().select(RequestContextController.class);
        if (rcc.isResolvable()) {
            requestController = rcc.get();
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
                        try {
                            requestController.activate();
                            WeldRequestScope.actualInjectorManager.set(injectionManager);
                            return supplier.get();
                        } catch (Throwable t) {
                            throw t instanceof Exception ? ((Exception) t) : new RuntimeException(t);
                        } finally {
                            requestController.deactivate();
                            WeldRequestScope.actualInjectorManager.set(old);
                        }
                    }));
        } else if (requestController != null) {         // CDI only
            return () -> {
                try {
                    requestController.activate();
                    return supplier.get();
                } finally {
                    requestController.deactivate();
                }
            };
        } else {
            return supplier;
        }
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
        requestController = null;
        injectionManager = null;
        state = State.CLEARED;
    }
}
