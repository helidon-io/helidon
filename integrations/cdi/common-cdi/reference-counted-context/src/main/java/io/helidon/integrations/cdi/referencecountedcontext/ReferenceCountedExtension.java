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
import java.util.HashSet;
import java.util.Objects;
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
     * Observer methods.
     */


    private <T> void ensureManagedBeanDisposalsDecrementReferenceCounts(@Observes final ProcessInjectionTarget<T> event,
                                                                        final BeanManager beanManager) {
        final InjectionTarget<T> delegate = event.getInjectionTarget();
        final Producer<T> referenceCountingProducer = this.createReferenceCountingProducer(delegate, beanManager);
        assert referenceCountingProducer != null;
        event.setInjectionTarget(new DelegatingInjectionTarget<T>(delegate) {
                @Override
                public T produce(final CreationalContext<T> cc) {
                    return referenceCountingProducer.produce(cc);
                }

                @Override
                public void dispose(final T instance) {
                    referenceCountingProducer.dispose(instance);
                }
            });
    }

    private <T> void trackReferenceCountedTypes(@Observes final ProcessBean<T> event) {
        final BeanAttributes<?> bean = event.getBean();
        final Class<? extends Annotation> scope = bean.getScope();
        if (ReferenceCounted.class.isAssignableFrom(scope)) {
            this.referenceCountedBeanTypes.addAll(bean.getTypes());
        }
    }

    private <T, X> void ensureProducersDecrementReferenceCounts(@Observes final ProcessProducer<T, X> event,
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
        Objects.requireNonNull(delegate);
        Objects.requireNonNull(beanManager);

        final Producer<T> returnValue = new DelegatingProducer<T>(delegate) {

                private volatile Set<InjectionPoint> referenceCountedInjectionPoints;

                @Override
                public T produce(final CreationalContext<T> cc) {
                    Set<InjectionPoint> referenceCountedInjectionPoints = this.referenceCountedInjectionPoints;
                    if (referenceCountedInjectionPoints == null) {
                        referenceCountedInjectionPoints = new HashSet<>();
                        final Set<InjectionPoint> delegateInjectionPoints = this.getInjectionPoints();
                        assert delegateInjectionPoints != null;
                        if (!delegateInjectionPoints.isEmpty()) {
                            for (final InjectionPoint delegateInjectionPoint : delegateInjectionPoints) {
                                if (delegateInjectionPoint != null) {
                                    final Type type = delegateInjectionPoint.getType();
                                    assert type != null;
                                    final Set<Annotation> qualifiers = delegateInjectionPoint.getQualifiers();
                                    if (referenceCountedBeanTypes.contains(type)) {
                                        referenceCountedInjectionPoints.add(delegateInjectionPoint);
                                    }
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
                    assert referenceCountedInjectionPoints != null;
                    for (final InjectionPoint injectionPoint : referenceCountedInjectionPoints) {
                        assert injectionPoint != null;
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
