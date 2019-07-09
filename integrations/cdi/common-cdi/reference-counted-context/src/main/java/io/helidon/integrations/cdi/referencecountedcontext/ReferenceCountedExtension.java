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
package io.helidon.integrations.cdi.referencecountedcontext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
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


    private final Set<Type> referenceCountedBeanTypes;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link ReferenceCountedExtension}.
     */
    public ReferenceCountedExtension() {
        super();
        this.referenceCountedBeanTypes = new HashSet<>();
    }


    /*
     * Observer methods in processing order.
     */


    private <T> void ensureManagedBeanOriginatedDisposalsDecrementReferenceCounts(@Observes final ProcessInjectionTarget<T> event,
                                                                                  final BeanManager beanManager) {
        final InjectionTarget<T> delegate = event.getInjectionTarget();
        event.setInjectionTarget(new DelegatingInjectionTarget<T>(delegate,
                                                                  this.createReferenceCountingProducer(delegate, beanManager)));
    }

    private <T> void trackReferenceCountedTypes(@Observes final ProcessBean<T> event) {
        final BeanAttributes<?> bean = event.getBean();
        if (ReferenceCounted.class.isAssignableFrom(bean.getScope())) {
            this.referenceCountedBeanTypes.addAll(bean.getTypes());
        }
    }

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
                public T produce(final CreationalContext<T> cc) {
                    Set<InjectionPoint> referenceCountedInjectionPoints = this.referenceCountedInjectionPoints;
                    if (referenceCountedInjectionPoints == null) {
                        final Set<InjectionPoint> delegateInjectionPoints = this.getInjectionPoints();
                        if (delegateInjectionPoints.isEmpty()) {
                            referenceCountedInjectionPoints = Collections.emptySet();
                        } else {
                            referenceCountedInjectionPoints = new HashSet<>();
                            for (final InjectionPoint delegateInjectionPoint : delegateInjectionPoints) {
                                if (referenceCountedBeanTypes.contains(delegateInjectionPoint.getType())) {
                                    referenceCountedInjectionPoints.add(delegateInjectionPoint);
                                }
                            }
                        }
                        this.referenceCountedInjectionPoints = referenceCountedInjectionPoints;
                    }
                    return super.produce(cc);
                }

                @Override
                public void dispose(final T instance) {
                    final Set<InjectionPoint> referenceCountedInjectionPoints = this.referenceCountedInjectionPoints;
                    for (final InjectionPoint injectionPoint : referenceCountedInjectionPoints) {
                        final Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                        final Set<Bean<?>> beans;
                        if (qualifiers == null || qualifiers.isEmpty()) {
                            beans = beanManager.getBeans(injectionPoint.getType());
                        } else {
                            beans = beanManager.getBeans(injectionPoint.getType(),
                                                         qualifiers.toArray(new Annotation[qualifiers.size()]));
                        }
                        // Long before this is ever called the
                        // container will have validated this
                        // injection point, so we know that there will
                        // be exactly one bean, and its scope will be
                        // ReferenceCounted.
                        assert beans != null;
                        assert beans.size() == 1;
                        final Bean<?> bean = beanManager.resolve(beans);
                        assert bean != null;
                        assert ReferenceCounted.class.equals(bean.getScope());
                        final ReferenceCountedContext context =
                            (ReferenceCountedContext) beanManager.getContext(ReferenceCounted.class);
                        assert context != null;
                        context.decrementReferenceCount(bean);
                    }
                    super.dispose(instance);
                }
            };
        return returnValue;
    }

}
