/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.cdi.jpa.weld;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.inject.Singleton;

/**
 * An {@link Extension} that exists solely to make the {@link
 * WeldJpaInjectionServices} class become a bean in {@link Singleton}
 * scope.
 *
 * @author <a href="mailto:laird.nelson@oracle.com">Laird Nelson</a>
 *
 * @see WeldJpaInjectionServices
 *
 * @see TransactionObserver
 */
final class WeldJpaInjectionServicesExtension implements Extension {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link WeldJpaInjectionServicesExtension}.
     *
     * <p>Oddly, the fact that this constructor is {@code private}
     * does not prevent Weld from loading it as a service.  This is an
     * unexpected bonus as nothing about this class should be {@code
     * public}.</p>
     */
    private WeldJpaInjectionServicesExtension() {
        super();
    }


    /*
     * Instance methods.
     */


    /**
     * Creates a bean deliberately in {@link Singleton} scope to
     * represent the {@link WeldJpaInjectionServices} class.
     *
     * <p>Weld often creates multiple copies of {@link
     * WeldJpaInjectionServices} by virtue of the way it loads its
     * bootstrap services.  We want to ensure there's just one that
     * can be injected into observer methods.  See the {@link
     * TransactionObserver} class, which houses one such observer
     * method.</p>
     *
     * @param event the {@link AfterBeanDiscovery} event; may be
     * {@code null} in which case no action will be taken
     *
     * @see WeldJpaInjectionServices
     *
     * @see TransactionObserver
     */
    private void afterBeanDiscovery(@Observes final AfterBeanDiscovery event) {
        if (event != null) {
            event.addBean()
                 .addTransitiveTypeClosure(WeldJpaInjectionServices.class)
                 .scope(Singleton.class)
                 .createWith(ignored -> {
                     return WeldJpaInjectionServices.getInstance();
                  });
        }
    }

}
