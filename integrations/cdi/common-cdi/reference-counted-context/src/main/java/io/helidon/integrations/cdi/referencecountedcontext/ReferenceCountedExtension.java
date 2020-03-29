/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.referencecountedcontext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.enterprise.inject.spi.ProcessProducer;
import javax.enterprise.inject.spi.Producer;

import io.helidon.integrations.cdi.delegates.DelegatingInjectionTarget;
import io.helidon.integrations.cdi.delegates.DelegatingProducer;

/**
 * An {@link Extension} that installs and manages a {@link
 * ReferenceCountedContext}.
 *
 * @see ReferenceCountedContext
 *
 * @see ReferenceCounted
 */
public class ReferenceCountedExtension implements Extension {


    /*
     * Instance fields.
     */


    private final Set<Bean<?>> referenceCountedBeans;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link ReferenceCountedExtension}.
     */
    public ReferenceCountedExtension() {
        super();
        this.referenceCountedBeans = new HashSet<>();
    }


    /*
     * Observer methods in processing order.
     */


    // Of note: this will not be fired for synthetic beans.
    private <T> void ensureManagedBeanOriginatedDisposalsDecrementReferenceCounts(@Observes final ProcessInjectionTarget<T> event,
                                                                                  final BeanManager beanManager) {
        final InjectionTarget<T> delegate = event.getInjectionTarget();
        event.setInjectionTarget(new DelegatingInjectionTarget<T>(delegate,
                                                                  this.createReferenceCountingProducer(delegate, beanManager)));
    }

    private <T> void trackReferenceCountedTypes(@Observes final ProcessBean<T> event) {
        final Bean<?> bean = event.getBean();
        if (ReferenceCounted.class.isAssignableFrom(bean.getScope())) {
            this.referenceCountedBeans.add(bean);
        }
    }

    // Of note: this will not be fired for synthetic beans.
    private <T, X> void ensureProducerOriginatedDisposalDecrementReferenceCounts(@Observes final ProcessProducer<T, X> event,
                                                                                 final BeanManager beanManager) {
        event.setProducer(this.createReferenceCountingProducer(event.getProducer(), beanManager));
    }

    private void installReferenceCountedContext(@Observes final AfterBeanDiscovery event) {
        event.addContext(new ReferenceCountedContext());
    }


    /*
     * Utility methods.
     */


    private <T> Producer<T> createReferenceCountingProducer(final Producer<T> delegate,
                                                            final BeanManager beanManager) {
        final Producer<T> returnValue = new DelegatingProducer<T>(delegate) {

                private volatile Set<InjectionPoint> referenceCountedInjectionPoints;

                @Override
                public void dispose(final T instance) {
                    Set<InjectionPoint> referenceCountedInjectionPoints = this.referenceCountedInjectionPoints;
                    if (referenceCountedInjectionPoints == null) {
                        final Set<? extends InjectionPoint> injectionPoints = this.getInjectionPoints();
                        if (injectionPoints.isEmpty()) {
                            referenceCountedInjectionPoints = Collections.emptySet();
                        } else {
                            referenceCountedInjectionPoints = new HashSet<>();
                            for (final InjectionPoint injectionPoint : injectionPoints) {
                                final Set<? extends Annotation> qualifiers = injectionPoint.getQualifiers();
                                final Set<Bean<?>> beans;
                                if (qualifiers == null || qualifiers.isEmpty()) {
                                    beans = beanManager.getBeans(injectionPoint.getType());
                                } else {
                                    beans = beanManager.getBeans(injectionPoint.getType(),
                                                                 qualifiers.toArray(new Annotation[qualifiers.size()]));
                                }
                                assert beans != null;
                                final Bean<?> bean = beanManager.resolve(beans);
                                assert bean != null;
                                if (referenceCountedBeans.contains(bean)) {
                                    assert ReferenceCounted.class.equals(bean.getScope())
                                        : "Unexpected scope: " + bean.getScope() + "; bean: " + bean;
                                    referenceCountedInjectionPoints.add(injectionPoint);
                                }
                            }
                        }
                        this.referenceCountedInjectionPoints = referenceCountedInjectionPoints;
                    }
                    for (final InjectionPoint injectionPoint : referenceCountedInjectionPoints) {
                        final Set<? extends Annotation> qualifiers = injectionPoint.getQualifiers();
                        final Set<Bean<?>> beans;
                        if (qualifiers == null || qualifiers.isEmpty()) {
                            beans = beanManager.getBeans(injectionPoint.getType());
                        } else {
                            beans = beanManager.getBeans(injectionPoint.getType(),
                                                         qualifiers.toArray(new Annotation[qualifiers.size()]));
                        }
                        assert beans != null;
                        final Bean<?> bean = beanManager.resolve(beans);
                        assert bean != null;
                        assert ReferenceCounted.class.equals(bean.getScope())
                            : "Unexpected scope: " + bean.getScope() + "; bean: " + bean;
                        final ReferenceCountedContext context = ReferenceCountedContext.getInstanceFrom(beanManager);
                        assert context != null;
                        context.decrementReferenceCount(bean);
                    }
                    super.dispose(instance);
                }
            };
        return returnValue;
    }

}
