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
package io.helidon.integrations.cdi.jpa;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.literal.InjectLiteral;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.inject.spi.configurator.AnnotatedFieldConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;

import static jakarta.persistence.PersistenceContextType.EXTENDED;
import static jakarta.persistence.SynchronizationType.UNSYNCHRONIZED;

/**
 * An experimental {@link Extension} related to JPA.
 *
 * <p>This class is subject to removal without prior notice at any time.</p>
 */
public final class JpaExtension2 implements Extension {


    /*
     * Static fields.
     */


    /**
     * The name used to designate the only persistence unit in the environment, when there is exactly one persistence
     * unit in the environment, and there is at least one {@link PersistenceContext @PersistenceContext}-annotated
     * injection point that does not specify a value for the {@link PersistenceContext#unitName() unitName} element.
     *
     * <p>In such a case, the injection point will be effectively rewritten such that it will appear to the CDI
     * container as though there <em>were</em> a value specified for the {@link PersistenceContext#unitName() unitName}
     * element&mdash;namely this field's value.  Additionally, a bean identical to the existing solitary {@link
     * PersistenceUnitInfo}-typed bean will be added with this field's value as the {@linkplain Named#value() value of
     * its <code>Named</code> qualifier}, thus serving as a kind of alias for the "real" bean.</p>
     *
     * <p>This is necessary because the empty string ({@code ""}) as the value of the {@link Named#value()} element has
     * special semantics, so cannot be used to designate an unnamed persistence unit.</p>
     *
     * <p>The value of this field is subject to change without prior notice at any point.  In general the mechanics
     * around injection point rewriting are also subject to change without prior notice at any point.</p>
     */
    static final String DEFAULT_PERSISTENCE_UNIT_NAME = "__DEFAULT__";


    /*
     * Instance fields.
     */


    // private boolean defaultPersistenceUnitInEffect;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JpaExtension2}.
     *
     * @deprecated For invocation by CDI only.
     */
    @Deprecated // For invocation by CDI only.
    public JpaExtension2() {
        super();
    }


    /*
     * Instance methods.
     */


    private <T> void rewriteJpaAnnotations(@Observes
                                           @WithAnnotations({PersistenceContext.class, PersistenceUnit.class})
                                           ProcessAnnotatedType<T> event) {
        AnnotatedTypeConfigurator<T> atc = event.configureAnnotatedType();
        atc.filterFields(JpaExtension2::isEligiblePersistenceContextAnnotated)
            .forEach(this::rewritePersistenceContextFieldAnnotations);
        atc.filterFields(JpaExtension2::isEligiblePersistenceUnitAnnotated)
            .forEach(this::rewritePersistenceUnitFieldAnnotations);
        atc.filterMethods(JpaExtension2::isEligiblePersistenceContextAnnotated)
            .forEach(this::rewritePersistenceContextInitializerMethodAnnotations);
        atc.filterMethods(JpaExtension2::isEligiblePersistenceUnitAnnotated)
            .forEach(this::rewritePersistenceUnitInitializerMethodAnnotations);
    }

    /**
     * Reconfigures annotations on an {@linkplain #isEligiblePersistenceContextField(AnnotatedField) eligible
     * <code>PersistenceContext</code>-annotated <code>AnnotatedField</code>} such that the resulting {@link
     * AnnotatedField} is a true CDI injection point representing all the same information.
     *
     * <p>The original {@link PersistenceContext} annotation is removed.</p>
     *
     * @param fc the {@link AnnotatedFieldConfigurator} that allows the field to be re-annotated; must not be {@code
     * null}
     *
     * @exception NullPointerException if {@code fc} is {@code null}
     */
    private <T> void rewritePersistenceContextFieldAnnotations(AnnotatedFieldConfigurator<T> fc) {
        this.rewrite(fc,
                     PersistenceContext.class,
                     EntityManager.class,
                     PersistenceContext::unitName,
                     (f, pc) -> f.add(pc.type() == EXTENDED
                                      ? Extended.Literal.INSTANCE
                                      : JpaTransactionScoped.Literal.INSTANCE)
                                 .add(pc.synchronization() == UNSYNCHRONIZED
                                      ? Unsynchronized.Literal.INSTANCE
                                      : Synchronized.Literal.INSTANCE));
    }

    /**
     * Reconfigures annotations on an {@linkplain #isEligiblePersistenceUnitAnnotated(Annotated) eligible
     * <code>PersistenceUnit</code>-annotated <code>Annotated</code>} such that the resulting {@link Annotated} is a
     * true CDI injection point representing all the same information.
     *
     * <p>The original {@link PersistenceUnit} annotation is removed.</p>
     *
     * @param fc the {@link AnnotatedFieldConfigurator} that allows the field to be re-annotated; must not be {@code
     * null}
     *
     * @exception NullPointerException if {@code fc} is {@code null}
     */
    private <T> void rewritePersistenceUnitFieldAnnotations(AnnotatedFieldConfigurator<T> fc) {
        this.rewrite(fc, PersistenceUnit.class, EntityManagerFactory.class, PersistenceUnit::unitName);
    }

    private <T> void rewritePersistenceContextInitializerMethodAnnotations(AnnotatedMethodConfigurator<T> mc) {
        this.rewrite(mc,
                     PersistenceContext.class,
                     EntityManager.class,
                     PersistenceContext::unitName,
                     (apc, pc) -> apc.add(pc.type() == EXTENDED
                                          ? Extended.Literal.INSTANCE
                                          : JpaTransactionScoped.Literal.INSTANCE)
                                     .add(pc.synchronization() == UNSYNCHRONIZED
                                          ? Unsynchronized.Literal.INSTANCE
                                          : Synchronized.Literal.INSTANCE));
    }

    private <T> void rewritePersistenceUnitInitializerMethodAnnotations(final AnnotatedMethodConfigurator<T> mc) {
        this.rewrite(mc, PersistenceUnit.class, EntityManagerFactory.class, PersistenceUnit::unitName);
    }

    private <T, A extends Annotation> void rewrite(AnnotatedFieldConfigurator<T> fc,
                                                   Class<A> ac,
                                                   Class<?> c,
                                                   Function<? super A, ? extends String> unitNameFunction) {
        this.rewrite(fc, ac, c, unitNameFunction, null);
    }

    private <T, A extends Annotation> void rewrite(AnnotatedFieldConfigurator<T> fc,
                                                   Class<A> ac,
                                                   Class<?> c,
                                                   Function<? super A, ? extends String> unitNameFunction,
                                                   BiConsumer<? super AnnotatedFieldConfigurator<T>, ? super A> adder) {
        Annotated f = fc.getAnnotated();
        if (!f.isAnnotationPresent(Inject.class) && f.getBaseType() instanceof Class<?> c2 && c.isAssignableFrom(c2)) {
            A a = fc.getAnnotated().getAnnotation(ac);
            if (a != null) {
                fc.add(InjectLiteral.INSTANCE);
                fc.add(ContainerManaged.Literal.INSTANCE);
                String unitName = unitNameFunction.apply(a);
                if (unitName != null) {
                    unitName = unitName.trim();
                    if (unitName.isEmpty()) {
                        unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                        // this.defaultPersistenceUnitInEffect = true;
                    }
                } else {
                    unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                    // this.defaultPersistenceUnitInEffect = true;
                }
                fc.add(NamedLiteral.of(unitName));
                if (adder != null) {
                    adder.accept(fc, a);
                }
                fc.remove(fa -> fa == a);
            }
        }
    }

    private <T, A extends Annotation> void rewrite(AnnotatedMethodConfigurator<T> mc,
                                                   Class<A> ac,
                                                   Class<?> c,
                                                   Function<? super A, ? extends String> unitNameFunction) {
        this.rewrite(mc, ac, c, unitNameFunction, null);
    }

    private <T, A extends Annotation> void rewrite(AnnotatedMethodConfigurator<T> mc,
                                                   Class<A> ac,
                                                   Class<?> c,
                                                   Function<? super A, ? extends String> unitNameFunction,
                                                   BiConsumer<? super AnnotatedParameterConfigurator<T>, ? super A> adder) {
        Annotated m = mc.getAnnotated();
        if (!m.isAnnotationPresent(Inject.class)) {
            A a = m.getAnnotation(ac);
            if (a != null) {
                boolean observerMethod = false;
                List<AnnotatedParameterConfigurator<T>> apcs = mc.params();
                if (!apcs.isEmpty()) {
                    for (AnnotatedParameterConfigurator<T> apc : apcs) {
                        Annotated p = apc.getAnnotated();
                        if (p.isAnnotationPresent(Observes.class)) {
                            if (!observerMethod) {
                                observerMethod = true;
                            }
                        } else if (p.getBaseType() instanceof Class<?> pc && c.isAssignableFrom(pc)) {
                            apc.add(ContainerManaged.Literal.INSTANCE);
                            String unitName = unitNameFunction.apply(a);
                            if (unitName != null) {
                                unitName = unitName.trim();
                                if (unitName.isEmpty()) {
                                    unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                                    // this.defaultPersistenceUnitInEffect = true;
                                }
                            } else {
                                unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                                // this.defaultPersistenceUnitInEffect = true;
                            }
                            apc.add(NamedLiteral.of(unitName));
                            if (adder != null) {
                                adder.accept(apc, a);
                            }
                        }
                    }
                    mc.remove(ma -> ma == a);
                    if (!observerMethod) {
                        mc.add(InjectLiteral.INSTANCE);
                    }
                }
            }
        }
    }


    /*
     * Static methods.
     */


    /**
     * Returns {@code true} if the supplied {@link Annotated} is annotated with {@link PersistenceContext}, is not
     * annotated with {@link Inject} and has a type assignable to {@link EntityManager}.
     *
     * @param a the {@link Annotated} in question; must not be {@code null}
     *
     * @return {@code true} if the supplied {@link Annotated} is annotated with {@link PersistenceContext}, is not
     * annotated with {@link Inject} and has a type assignable to {@link EntityManager}; {@code false} in all other
     * cases
     *
     * @exception NullPointerException if {@code a} is {@code null}
     */
    private static boolean isEligiblePersistenceContextAnnotated(Annotated a) {
        return isRewriteEligible(a, PersistenceContext.class, EntityManager.class);
    }

    /**
     * Returns {@code true} if the supplied {@link Annotated} is annotated with {@link PersistenceUnit}, is not
     * annotated with {@link Inject} and has a type assignable to {@link EntityManagerFactory}.
     *
     * @param a the {@link Annotated} in question; must not be {@code null}
     *
     * @return {@code true} if the supplied {@link Annotated} is annotated with {@link PersistenceUnit}, is not
     * annotated with {@link Inject} and has a type assignable to {@link EntityManagerFactory}; {@code false} in all
     * other cases
     *
     * @exception NullPointerException if {@code a} is {@code null}
     */
    private static boolean isEligiblePersistenceUnitAnnotated(Annotated a) {
        return isRewriteEligible(a, PersistenceUnit.class, EntityManagerFactory.class);
    }

    private static <A extends Annotation> boolean isRewriteEligible(Annotated a, Class<? extends A> ac, Class<?> c) {
        return
            a.isAnnotationPresent(ac)
            && !a.isAnnotationPresent(Inject.class)
            && a.getBaseType() instanceof Class<?> c2
            && c.isAssignableFrom(c2);
    }

}
