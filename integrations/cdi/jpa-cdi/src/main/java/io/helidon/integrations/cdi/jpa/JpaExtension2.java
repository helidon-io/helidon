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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import io.helidon.integrations.cdi.jpa.PersistenceUnitInfoBean.DataSourceProvider;
import io.helidon.integrations.cdi.jpa.jaxb.Persistence;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.InjectionException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.literal.InjectLiteral;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.inject.spi.configurator.AnnotatedFieldConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.persistence.Converter;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceProperty;
import jakarta.persistence.PersistenceUnit;
import jakarta.persistence.spi.PersistenceProvider;
import jakarta.persistence.spi.PersistenceProviderResolver;
import jakarta.persistence.spi.PersistenceProviderResolverHolder;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_AFTER;
import static jakarta.persistence.PersistenceContextType.EXTENDED;
import static jakarta.persistence.SynchronizationType.SYNCHRONIZED;
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

    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];


    /*
     * Instance fields.
     */


    // private boolean defaultPersistenceUnitInEffect;

    /**
     * A {@link Map} of {@link PersistenceUnitInfoBean} instances that were created by the {@link
     * #gatherImplicitPersistenceUnits(ProcessAnnotatedType, BeanManager)} observer method, indexed by the names of
     * persistence units.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>The contents of this field are used only when no explicit {@link PersistenceUnitInfo} beans are otherwise
     * available in the container.</p>
     *
     * @see #gatherImplicitPersistenceUnits(ProcessAnnotatedType, BeanManager)
     */
    private final Map<String, PersistenceUnitInfoBean> implicitPersistenceUnits;

    /**
     * A {@link Map} of {@link Set}s of {@link Class}es whose keys are persistence unit names and whose values are
     * {@link Set}s of {@link Class}es discovered by CDI (and hence consist of unlisted classes in the sense that they
     * might not be found in any {@link PersistenceUnitInfo}).
     *
     * <p>Such {@link Class}es, of course, might not have been weaved appropriately by the relevant {@link
     * PersistenceProvider}.</p>
     *
     * <p>This field is never {@code null}.</p>
     */
    private final Map<String, Set<Class<?>>> unlistedManagedClassesByPersistenceUnitNames;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers annotating CDI injection points related to JPA.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>These qualifiers are built up as this portable extension {@linkplain ProcessInjectionPoint discovers {@link
     * EntityManager}-typed <code>InjectionPoint</code>s}.</p>
     *
     * @see #saveEntityManagerQualifiers(ProcessInjectionPoint)
     */
    private final Set<Set<Annotation>> persistenceContextQualifiers;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers for which {@link EntityManagerFactory} beans may be created.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>These qualifiers are built up as this portable extension {@linkplain ProcessInjectionPoint discovers {@link
     * EntityManagerFactory}-typed <code>InjectionPoint</code>s}.</p>
     *
     * @see #saveEntityManagerFactoryQualifiers(ProcessInjectionPoint)
     */
    private final Set<Set<Annotation>> persistenceUnitQualifiers;

    /**
     * Indicates whether a bean for the default persistence unit
     * has been added.
     *
     * @see #validate(AfterDeploymentValidation)
     */
    private boolean addedDefaultPersistenceUnit;

    /**
     * Indicates if JTA transactions can be supported.
     *
     * @see #disableTransactionSupport(ProcessAnnotatedType)
     */
    private boolean transactionsSupported;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers that serves as a kind of cache, preventing more than one {@link
     * ContainerManaged}-qualified {@link EntityManagerFactory}-typed bean from being added for the same set of
     * qualifiers.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>This field is {@linkplain Collection#clear() cleared} at the termination of the {@link
     * #addSyntheticBeans(AfterBeanDiscovery, BeanManager)} container lifecycle method.</p>
     *
     * @see #addContainerManagedEntityManagerFactoryBeans(AfterBeanDiscovery, Set, BeanManager)
     */
    private final Set<Set<Annotation>> containerManagedEntityManagerFactoryQualifiers;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers that serves as a kind of cache, preventing more than one {@link
     * NonTransactional}-qualified {@link EntityManager}-typed bean from being added for the same set of qualifiers.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>This field is {@linkplain Collection#clear() cleared} at the termination of the {@link
     * #addSyntheticBeans(AfterBeanDiscovery, BeanManager)} container lifecycle method.</p>
     *
     * @see #addNonTransactionalEntityManagerBeans(AfterBeanDiscovery, Set, BeanManager)
     */
    private final Set<Set<Annotation>> nonTransactionalEntityManagerQualifiers;


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
        this.implicitPersistenceUnits = new HashMap<>();
        this.unlistedManagedClassesByPersistenceUnitNames = new HashMap<>();
        this.persistenceContextQualifiers = new HashSet<>();
        this.persistenceUnitQualifiers = new HashSet<>();
        this.transactionsSupported = true;
        this.containerManagedEntityManagerFactoryQualifiers = new HashSet<>();
        this.nonTransactionalEntityManagerQualifiers = new HashSet<>();
    }


    /*
     * Observer methods.
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
     * Looks for type-level {@link PersistenceContext} annotations that have at least one {@link PersistenceProperty}
     * annotation {@linkplain PersistenceContext#properties() associated with} them and uses them to define persistence
     * units, potentially preventing the need for {@code META-INF/persistence.xml} processing.
     *
     * @param event the {@link ProcessAnnotatedType} event occurring; must not be {@code null}
     *
     * @param bm the {@link BeanManager} in effect; must not be {@code null}
     *
     * @exception NullPointerException if either {@code event} or {@code bm} is {@code null}
     *
     * @see PersistenceContext
     *
     * @see PersistenceProperty
     *
     * @see PersistenceUnitInfoBean
     */
    private void gatherImplicitPersistenceUnits(@Observes
                                                // yes, @PersistenceContext, not @PersistenceUnit
                                                @WithAnnotations(PersistenceContext.class)
                                                ProcessAnnotatedType<?> event,
                                                BeanManager bm) {
        AnnotatedType<?> at = event.getAnnotatedType();
        if (at.isAnnotationPresent(Vetoed.class)) {
            return;
        }
        Set<? extends PersistenceContext> pcs = at.getAnnotations(PersistenceContext.class);
        for (PersistenceContext pc : pcs) {
            PersistenceProperty[] pps = pc.properties();
            if (pps.length > 0) {
                String puName = pc.unitName();
                PersistenceUnitInfoBean pu = this.implicitPersistenceUnits.get(puName);
                if (pu == null) {
                    Properties properties = new Properties();
                    for (PersistenceProperty pp : pps) {
                        String ppName = pp.name();
                        if (!ppName.isEmpty()) {
                            properties.setProperty(ppName, pp.value());
                        }
                    }
                    pu =
                        new PersistenceUnitInfoBean(puName,
                                                    locationOf(at),
                                                    null,
                                                    () -> bm
                                                    .createInstance()
                                                    .select(DataSourceProvider.class)
                                                    .get(),
                                                    properties);
                    this.implicitPersistenceUnits.put(puName, pu);
                }
            }
        }
    }

    /**
     * Tracks {@linkplain Converter converters}, {@linkplain Entity entities}, {@linkplain Embeddable embeddables} and
     * {@linkplain MappedSuperclass mapped superclasses} that were auto-discovered by CDI bean discovery, and makes sure
     * that they are not actually CDI beans, since according to the JPA specification they cannot be.
     *
     * <p>This method also keeps track of these classes as potential "unlisted classes" to be used by a {@linkplain
     * PersistenceUnitInfo persistence unit} if its {@linkplain PersistenceUnitInfo#excludeUnlistedClasses()} method
     * returns {@code false}.</p>
     *
     * @param event the event describing the {@link AnnotatedType} being processed; must not be {@code null}
     *
     * @exception NullPointerException if {@code event} is {@code null}
     *
     * @see Converter
     *
     * @see Embeddable
     *
     * @see Entity
     *
     * @see MappedSuperclass
     *
     * @see PersistenceUnitInfo#excludeUnlistedClasses()
     */
    private void discoverManagedClasses(@Observes
                                        @WithAnnotations({
                                            Converter.class,
                                            Embeddable.class,
                                            Entity.class,
                                            MappedSuperclass.class
                                        })
                                        ProcessAnnotatedType<?> event) {
        AnnotatedType<?> at = event.getAnnotatedType();
        if (at.isAnnotationPresent(Vetoed.class)) {
            return;
        }
        this.assignManagedClassToPersistenceUnit(at.getAnnotations(PersistenceContext.class),
                                                 at.getAnnotations(PersistenceUnit.class),
                                                 at.getJavaClass());
        event.veto(); // managed classes can't be beans
    }

    /**
     * Stores {@link Set}s of qualifiers that annotate {@link EntityManagerFactory}-typed injection points.
     *
     * <p>{@link EntityManagerFactory}-typed beans will be added for each such {@link Set}.</p>
     *
     * @param e a {@link ProcessInjectionPoint} container lifecycle event; must not be {@code null}
     *
     * @exception NullPointerException if {@code event} is {@code null}
     */
    private <T extends EntityManagerFactory> void saveEntityManagerFactoryQualifiers(@Observes ProcessInjectionPoint<?, T> e) {
        this.persistenceUnitQualifiers.add(e.getInjectionPoint().getQualifiers());
    }

    /**
     * Stores {@link Set}s of qualifiers that annotate {@link EntityManager}-typed injection points.
     *
     * <p>{@link EntityManager}-typed beans will be added for each such {@link Set}.</p>
     *
     * @param e a {@link ProcessInjectionPoint} container lifecycle event; must not be {@code null}
     *
     * @exception NullPointerException if {@code e} is {@code null}
     */
    private <T extends EntityManager> void saveEntityManagerQualifiers(@Observes ProcessInjectionPoint<?, T> e) {
        Set<Annotation> qualifiers = e.getInjectionPoint().getQualifiers();
        boolean error = false;
        if (qualifiers.contains(JpaTransactionScoped.Literal.INSTANCE)) {
            if (qualifiers.contains(CdiTransactionScoped.Literal.INSTANCE)
                || qualifiers.contains(Extended.Literal.INSTANCE)
                || qualifiers.contains(NonTransactional.Literal.INSTANCE)) {
                error = true;
            }
        } else if (qualifiers.contains(Extended.Literal.INSTANCE)) {
            if (qualifiers.contains(CdiTransactionScoped.Literal.INSTANCE)
                || qualifiers.contains(NonTransactional.Literal.INSTANCE)) {
                error = true;
            }
        } else if (qualifiers.contains(NonTransactional.Literal.INSTANCE)) {
            if (qualifiers.contains(CdiTransactionScoped.Literal.INSTANCE)) {
                error = true;
            }
        } else if (qualifiers.contains(Synchronized.Literal.INSTANCE)) {
            if (qualifiers.contains(Unsynchronized.Literal.INSTANCE)) {
                error = true;
            }
        }
        if (error) {
            e.addDefinitionError(new InjectionException("Invalid injection point; some qualifiers are mutually exclusive: "
                                                        + qualifiers));
        } else {
            this.persistenceContextQualifiers.add(qualifiers);
        }
    }

    /**
     * Adds various beans that integrate JPA into CDI SE.
     *
     * <p>This method first converts {@code META-INF/persistence.xml} resources into {@link PersistenceUnitInfo} objects
     * and takes into account any other {@link PersistenceUnitInfo} objects that already exist and ensures that all of
     * them are registered as CDI beans.</p>
     *
     * <p>This allows other CDI-provider-specific mechanisms to use these {@link PersistenceUnitInfo} beans as inputs
     * for creating {@link EntityManager} instances.</p>
     *
     * <p>Next, this method adds beans to produce {@link EntityManager}s and {@link EntityManagerFactory} instances in
     * accordance with the JPA specification.</p>
     *
     * @param event the {@link AfterBeanDiscovery} event describing the fact that bean discovery has been performed;
     * must not be {@code null}
     *
     * @param bm the {@link BeanManager} currently in effect; must not be {@code null}
     *
     * @see PersistenceUnitInfo
     */
    private void addSyntheticBeans(@Observes @Priority(LIBRARY_AFTER) AfterBeanDiscovery event, BeanManager bm) {
        Iterable<? extends PersistenceProvider> providers = addPersistenceProviderBeans(event);

        // Should we consider type-level @PersistenceContext definitions of persistence units ("implicits")?
        boolean processImplicits = true;

        // Collect all pre-existing PersistenceUnitInfo beans (i.e. supplied by the end user) and make sure their
        // associated PersistenceProviders are beanified.  (Almost always this Set will be empty.)
        Set<Bean<?>> preexistingPersistenceUnitInfoBeans = bm.getBeans(PersistenceUnitInfo.class, Any.Literal.INSTANCE);
        if (!preexistingPersistenceUnitInfoBeans.isEmpty()) {
            processImplicits = false;
            this.maybeAddPersistenceProviderBeans(event, bm, preexistingPersistenceUnitInfoBeans, providers);
        }

        // Next, and most commonly, load all META-INF/persistence.xml resources with JAXB, and turn them into
        // PersistenceUnitInfo instances, and add beans for all of them as well as their associated PersistenceProviders
        // (if applicable).
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> urls;
        try {
            urls = classLoader.getResources("META-INF/persistence.xml");
        } catch (IOException e) {
            event.addDefinitionError(e);
            processImplicits = false;
            urls = Collections.emptyEnumeration();
        }
        if (urls.hasMoreElements()) {
            processImplicits = false;
            this.processPersistenceXmls(event,
                                        bm,
                                        classLoader,
                                        urls,
                                        providers,
                                        !preexistingPersistenceUnitInfoBeans.isEmpty());
        }

        // If we did not find any PersistenceUnitInfo instances via any other means, only then look at those defined
        // "implicitly", i.e. via type-level @PersistenceContext annotations.
        if (processImplicits) {
            this.processImplicitPersistenceUnits(event, providers);
        }

        // Add beans to support JPA.  In some cases, JTA must be present (see JPA section 7.5, for example: "A
        // container-managed entity manager must be a JTA entity manager.").
        this.addContainerManagedJpaBeans(event, bm);

        // Clear out no-longer-needed-or-used collections to save memory.
        // this.cdiTransactionScopedEntityManagerQualifiers.clear();
        this.containerManagedEntityManagerFactoryQualifiers.clear();
        this.implicitPersistenceUnits.clear();
        this.nonTransactionalEntityManagerQualifiers.clear();
        this.persistenceContextQualifiers.clear();
        this.persistenceUnitQualifiers.clear();
        this.unlistedManagedClassesByPersistenceUnitNames.clear();
    }


    /*
     * Other instance methods.
     */


    /**
     * Reconfigures annotations on an {@linkplain #isEligiblePersistenceContextAnnotated(Annotated) eligible
     * <code>PersistenceContext</code>-annotated <code>Annotated</code>} such that the resulting {@link Annotated} is a
     * true CDI injection point representing all the same information.
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
                     (p, pc) -> p.add(pc.type() == EXTENDED
                                      ? Extended.Literal.INSTANCE
                                      : JpaTransactionScoped.Literal.INSTANCE)
                                 .add(pc.synchronization() == UNSYNCHRONIZED
                                      ? Unsynchronized.Literal.INSTANCE
                                      : Synchronized.Literal.INSTANCE));
    }

    private <T> void rewritePersistenceUnitInitializerMethodAnnotations(AnnotatedMethodConfigurator<T> mc) {
        this.rewrite(mc, PersistenceUnit.class, EntityManagerFactory.class, PersistenceUnit::unitName);
    }

    private <T, A extends Annotation> void rewrite(AnnotatedFieldConfigurator<T> fc,
                                                   Class<A> ac,
                                                   Class<?> c,
                                                   Function<? super A, ? extends String> unitNameFunction) {
        this.rewrite(fc, ac, c, unitNameFunction, JpaExtension2::sink);
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
                adder.accept(fc, a);
                fc.remove(fa -> fa == a);
            }
        }
    }

    private <T, A extends Annotation> void rewrite(AnnotatedMethodConfigurator<T> mc,
                                                   Class<A> ac,
                                                   Class<?> c,
                                                   Function<? super A, ? extends String> unitNameFunction) {
        this.rewrite(mc, ac, c, unitNameFunction, JpaExtension2::sink);
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
                            adder.accept(apc, a);
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

    /**
     * Given {@link Set}s of {@link PersistenceContext} and {@link PersistenceUnit} annotations that will be used for
     * their {@code unitName} elements only, associates the supplied {@link Class} with the persistence units implied by
     * the annotations.
     *
     * @param pcs a {@link Set} of {@link PersistenceContext}s whose {@link
     * PersistenceContext#unitName() unitName} elements identify persistence units; may be {@code null} or {@linkplain
     * Collection#isEmpty() empty}
     *
     * @param pus a {@link Set} of {@link PersistenceUnit}s whose {@link PersistenceUnit#unitName()
     * unitName} elements identify persistence units; may be {@code null} or {@linkplain Collection#isEmpty() empty}
     *
     * @param c the {@link Class} to associate; may be {@code null} in which case no action will be taken
     *
     * @see PersistenceContext
     *
     * @see PersistenceUnit
     */
    private void assignManagedClassToPersistenceUnit(Set<? extends PersistenceContext> pcs,
                                                     Set<? extends PersistenceUnit> pus,
                                                     Class<?> c) {
        boolean processed = false;
        for (PersistenceContext pc : pcs) {
            if (!processed) {
                processed = true;
            }
            String unitName = pc.unitName();
            if (unitName.isEmpty()) {
                unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                // this.defaultPersistenceUnitInEffect = true;
            }
            addUnlistedManagedClass(unitName, c);
        }
        for (PersistenceUnit pu : pus) {
            if (!processed) {
                processed = true;
            }
            String unitName = pu.unitName();
            if (unitName.isEmpty()) {
                unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                // this.defaultPersistenceUnitInEffect = true;
            }
            addUnlistedManagedClass(unitName, c);
        }
        if (!processed) {
            addUnlistedManagedClass(DEFAULT_PERSISTENCE_UNIT_NAME, c);
            // this.defaultPersistenceUnitInEffect = true;
        }
    }

    /**
     * Given a {@link Class} and a name of a persistence unit, associates the {@link Class} with that persistence unit
     * as a member of its list of governed classes.
     *
     * @param puName the name of the persistence unit in question; may be {@code null}
     *
     * @param mc the {@link Class} to associate; may be {@code null} in which case no action will be taken
     *
     * @see PersistenceUnitInfo#getManagedClassNames()
     */
    private void addUnlistedManagedClass(String puName, Class<?> mc) {
        if (puName.isEmpty()) {
            puName = DEFAULT_PERSISTENCE_UNIT_NAME;
            // this.defaultPersistenceUnitInEffect = true;
        }
        Set<Class<?>> unlistedManagedClasses = this.unlistedManagedClassesByPersistenceUnitNames.get(puName);
        if (unlistedManagedClasses == null) {
            unlistedManagedClasses = new HashSet<>();
            this.unlistedManagedClassesByPersistenceUnitNames.put(puName, unlistedManagedClasses);
        }
        unlistedManagedClasses.add(mc);
    }

    private static Iterable<? extends PersistenceProvider> addPersistenceProviderBeans(AfterBeanDiscovery e) {
        PersistenceProviderResolver resolver = PersistenceProviderResolverHolder.getPersistenceProviderResolver();

        // Provide support for, e.g.:
        //   @Inject
        //   private PersistenceProviderResolver ppr;
        e.addBean()
            .addTransitiveTypeClosure(PersistenceProviderResolver.class)
            .scope(Singleton.class)
            .createWith(cc -> resolver);
        Collection<? extends PersistenceProvider> providers = resolver.getPersistenceProviders();
        for (PersistenceProvider provider : providers) {
            // Provide support for, e.g.:
            //   @Inject
            //   private MyPersistenceProviderSubclassMaybeFromPersistenceXml ppr;
            e.addBean()
                .addTransitiveTypeClosure(provider.getClass())
                .scope(Singleton.class)
                .createWith(cc -> provider);
        }
        return providers;
    }

    private void maybeAddPersistenceProviderBeans(AfterBeanDiscovery event,
                                                  BeanManager beanManager,
                                                  Set<Bean<?>> preexistingPersistenceUnitInfoBeans,
                                                  Iterable<? extends PersistenceProvider> providers) {
        for (Bean<?> bean : preexistingPersistenceUnitInfoBeans) {
            @SuppressWarnings("unchecked")
            Bean<PersistenceUnitInfo> preexistingPersistenceUnitInfoBean = (Bean<PersistenceUnitInfo>) bean;
            // We use Contextual#create() directly to create a PersistenceUnitInfo contextual instance (normally for
            // this use case in CDI you would acquire a contextual reference via BeanManager#getReference(), but it is
            // too early in the (spec-defined) lifecycle to do that here).  We also deliberately do not use
            // Context#get(Contextual, CreationalContext), since that might "install" the instance so acquired in
            // whatever Context/scope it is defined in and we just need it transiently.
            //
            // Getting a contextual instance this way, via Contextual#create(), is normally frowned upon, since it
            // bypasses CDI's Context mechansims and proxying and interception features (it is the foundation upon which
            // they are built), but here we need the instance only for the return values of
            // getPersistenceProviderClassName() and getClassLoader().  We then destroy the instance immediately so that
            // everything behaves as though this contextual instance acquired by shady means never existed.
            CreationalContext<PersistenceUnitInfo> cc = beanManager.createCreationalContext(null);
            PersistenceUnitInfo pui = preexistingPersistenceUnitInfoBean.create(cc);
            try {
                this.maybeAddPersistenceProviderBean(event, pui, providers);
            } finally {
                try {
                    preexistingPersistenceUnitInfoBean.destroy(pui, cc);
                } finally {
                    cc.release();
                }
            }
        }
    }

    /**
     * Given a {@link PersistenceUnitInfo} and a {@link Collection} of {@link PersistenceProvider} instances
     * representing already "beanified" {@link PersistenceProvider}s, adds a CDI bean for the {@linkplain
     * PersistenceUnitInfo#getPersistenceProviderClassName() persistence provider referenced by the supplied
     * <code>PersistenceUnitInfo</code>} if the supplied {@link Collection} of {@link PersistenceProvider}s does not
     * contain an instance of it.
     *
     * @param e the {@link AfterBeanDiscovery} event that will do the actual bean addition; must not be {@code null}
     *
     * @param persistenceUnitInfo the {@link PersistenceUnitInfo} whose {@linkplain
     * PersistenceUnitInfo#getPersistenceProviderClassName() associated persistence provider} will be beanified; must
     * not be {@code null}
     *
     * @param providers an {@link Iterable} of {@link PersistenceProvider} instances that represent {@link
     * PersistenceProvider}s that have already had beans added for them; may be {@code null}
     *
     * @exception NullPointerException if {@code e} or {@code persistenceUnitInfo} is {@code null}
     *
     * @exception ReflectiveOperationException if an error occurs during reflection
     */
    private void maybeAddPersistenceProviderBean(AfterBeanDiscovery e,
                                                 PersistenceUnitInfo persistenceUnitInfo,
                                                 Iterable<? extends PersistenceProvider> providers) {
        String providerClassName = persistenceUnitInfo.getPersistenceProviderClassName();
        if (providerClassName == null) {
            return;
        }

        for (final PersistenceProvider provider : providers) {
            if (provider.getClass().getName().equals(providerClassName)) {
                return;
            }
        }

        // The PersistenceProvider class in question is not one we already loaded.  Add a bean for it too.
        String persistenceUnitName = persistenceUnitInfo.getPersistenceUnitName();
        if (persistenceUnitName.isEmpty()) {
            persistenceUnitName = DEFAULT_PERSISTENCE_UNIT_NAME;
            // this.defaultPersistenceUnitInEffect = true;
        }

        // Provide support for, e.g.:
        //   @Inject
        //   @Named("test")
        //   private PersistenceProvider providerProbablyReferencedFromAPersistenceXml;
        e.addBean()
            .addTransitiveTypeClosure(PersistenceProvider.class)
            .scope(Singleton.class)
            .addQualifiers(NamedLiteral.of(persistenceUnitName))
            .createWith(cc -> {
                    try {
                        ClassLoader classLoader = persistenceUnitInfo.getClassLoader();
                        if (classLoader == null) {
                            classLoader = Thread.currentThread().getContextClassLoader();
                        }
                        return Class.forName(providerClassName, true, classLoader).getDeclaredConstructor().newInstance();
                    } catch (final ReflectiveOperationException reflectiveOperationException) {
                        throw new CreationException(reflectiveOperationException.getMessage(),
                                                    reflectiveOperationException);
                    }
                });
    }

    private void processPersistenceXmls(AfterBeanDiscovery event,
                                        BeanManager beanManager,
                                        ClassLoader classLoader,
                                        Enumeration<URL> urls,
                                        Iterable<? extends PersistenceProvider> providers,
                                        boolean userSuppliedPersistenceUnitInfoBeans) {
        if (!urls.hasMoreElements()) {
            return;
        }

        // We use StAX for XML loading because it is the same XML parsing strategy used by all known CDI
        // implementations.  If the end user wants to customize the StAX implementation then we want that customization
        // to apply here as well.
        XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();

        // See
        // https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.md#xmlinputfactory-a-stax-parser
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xmlInputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);

        Unmarshaller unmarshaller;
        try {
            unmarshaller = JAXBContext.newInstance(Persistence.class.getPackage().getName()).createUnmarshaller();
        } catch (JAXBException e) {
            event.addDefinitionError(e);
            return;
        }
        Supplier<? extends DataSourceProvider> dataSourceProviderSupplier =
            () -> beanManager.createInstance().select(DataSourceProvider.class).get();
        PersistenceUnitInfo solePersistenceUnitInfo = null;
        Supplier<? extends ClassLoader> tempClassLoaderSupplier;
        if (classLoader instanceof URLClassLoader ucl) {
            tempClassLoaderSupplier = () -> new URLClassLoader(ucl.getURLs());
        } else {
            tempClassLoaderSupplier = () -> classLoader;
        }
        int persistenceUnitCount = 0;
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            Collection<PersistenceUnitInfo> persistenceUnitInfos = null;
            Persistence persistence = null;
            try (InputStream inputStream = new BufferedInputStream(url.openStream())) {
                XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(inputStream);
                try {
                    persistence = (Persistence) unmarshaller.unmarshal(reader);
                } finally {
                    reader.close();
                }
            } catch (IOException | JAXBException | XMLStreamException e) {
                event.addDefinitionError(e);
                continue;
            }
            Collection<? extends Persistence.PersistenceUnit> persistenceUnits = persistence.getPersistenceUnit();
            if (!persistenceUnits.isEmpty()) {
                persistenceUnitInfos = new ArrayList<>();
                for (Persistence.PersistenceUnit persistenceUnit : persistenceUnits) {
                    try {
                        persistenceUnitInfos
                            .add(PersistenceUnitInfoBean.fromPersistenceUnit(persistenceUnit,
                                                                             classLoader,
                                                                             tempClassLoaderSupplier,
                                                                             new URL(url, ".."), // i.e. META-INF/..
                                                                             unlistedManagedClassesByPersistenceUnitNames,
                                                                             dataSourceProviderSupplier));
                    } catch (MalformedURLException e) {
                        event.addDefinitionError(e);
                    }
                }
            }
            if (persistenceUnitInfos != null) {
                for (PersistenceUnitInfo persistenceUnitInfo : persistenceUnitInfos) {
                    String persistenceUnitName = persistenceUnitInfo.getPersistenceUnitName();
                    if (persistenceUnitName == null || persistenceUnitName.isEmpty()) {
                        persistenceUnitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                        // this.defaultPersistenceUnitInEffect = true;
                    }
                    // Provide support for, e.g.:
                    //   @Inject
                    //   @Named("test")
                    //   private PersistenceUnitInfo persistenceUnitInfo;
                    event.addBean()
                        .beanClass(PersistenceUnitInfoBean.class)
                        .addTransitiveTypeClosure(PersistenceUnitInfoBean.class)
                        .scope(Singleton.class)
                        .addQualifiers(NamedLiteral.of(persistenceUnitName))
                        .createWith(cc -> persistenceUnitInfo);
                    if (persistenceUnitCount == 0) {
                        solePersistenceUnitInfo = persistenceUnitInfo;
                    } else if (solePersistenceUnitInfo != null) {
                        solePersistenceUnitInfo = null;
                    }
                    maybeAddPersistenceProviderBean(event, persistenceUnitInfo, providers);
                    persistenceUnitCount++;
                }
            }
        }
        if (!userSuppliedPersistenceUnitInfoBeans && solePersistenceUnitInfo != null) {
            String name = solePersistenceUnitInfo.getPersistenceUnitName();
            if (name != null && !name.isEmpty() && !name.equals(DEFAULT_PERSISTENCE_UNIT_NAME)) {
                // this.defaultPersistenceUnitInEffect = true;
                this.addedDefaultPersistenceUnit = true;
                PersistenceUnitInfo pu = solePersistenceUnitInfo;
                event.addBean()
                    .beanClass(PersistenceUnitInfoBean.class)
                    .addTransitiveTypeClosure(PersistenceUnitInfoBean.class)
                    .scope(Singleton.class)
                    .addQualifiers(NamedLiteral.of(DEFAULT_PERSISTENCE_UNIT_NAME))
                    .createWith(cc -> pu);
            }
        }
    }

    private void processImplicitPersistenceUnits(AfterBeanDiscovery event,
                                                 Iterable<? extends PersistenceProvider> providers) {
        int persistenceUnitCount = 0;
        PersistenceUnitInfoBean solePersistenceUnitInfoBean = null;
        for (PersistenceUnitInfoBean persistenceUnitInfoBean : this.implicitPersistenceUnits.values()) {
            String persistenceUnitName = persistenceUnitInfoBean.getPersistenceUnitName();
            if (persistenceUnitName == null || persistenceUnitName.isEmpty()) {
                persistenceUnitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                // this.defaultPersistenceUnitInEffect = true;
            }
            if (!persistenceUnitInfoBean.excludeUnlistedClasses()) {
                Collection<? extends Class<?>> unlistedManagedClasses =
                    this.unlistedManagedClassesByPersistenceUnitNames.get(persistenceUnitName);
                if (unlistedManagedClasses != null) {
                    for (Class<?> unlistedManagedClass : unlistedManagedClasses) {
                        persistenceUnitInfoBean.addManagedClassName(unlistedManagedClass.getName());
                    }
                }
            }

            // Provide support for, e.g.:
            //   @Inject
            //   @Named("test")
            //   private PersistenceUnitInfo persistenceUnitInfo;
            event.addBean()
                .beanClass(PersistenceUnitInfoBean.class)
                .addTransitiveTypeClosure(PersistenceUnitInfoBean.class)
                .scope(Singleton.class)
                .addQualifiers(NamedLiteral.of(persistenceUnitName))
                .createWith(cc -> persistenceUnitInfoBean);
            if (persistenceUnitCount == 0) {
                solePersistenceUnitInfoBean = persistenceUnitInfoBean;
            } else if (solePersistenceUnitInfoBean != null) {
                solePersistenceUnitInfoBean = null;
            }
            maybeAddPersistenceProviderBean(event, persistenceUnitInfoBean, providers);
            persistenceUnitCount++;
        }
        if (solePersistenceUnitInfoBean != null) {
            // Add a bean for the DEFAULT_PERSISTENCE_UNIT_NAME qualifier too.
            String name = solePersistenceUnitInfoBean.getPersistenceUnitName();
            if (name != null && !name.isEmpty()) {
                // this.defaultPersistenceUnitInEffect = true;
                this.addedDefaultPersistenceUnit = true;
                PersistenceUnitInfoBean pu = solePersistenceUnitInfoBean;
                event.addBean()
                    .beanClass(PersistenceUnitInfoBean.class)
                    .addTransitiveTypeClosure(PersistenceUnitInfoBean.class)
                    .scope(Singleton.class)
                    .addQualifiers(NamedLiteral.of(DEFAULT_PERSISTENCE_UNIT_NAME))
                    .createWith(cc -> pu);
            }
        }
    }

    /**
     * Adds certain beans to support injection of {@link
     * EntityManagerFactory} and {@link EntityManager} instances
     * according to the JPA specification.
     *
     * @param event an {@link AfterBeanDiscovery} container lifecycle
     * event; must not be {@code null}
     *
     * @param beanManager the current {@link BeanManager}; must not be
     * {@code null}
     *
     * @exception NullPointerException if either {@code event} or
     * {@code beanManager} is {@code null}
     *
     * @see
     * #addContainerManagedEntityManagerFactoryBeans(AfterBeanDiscovery,
     * Set, BeanManager)
     *
     * @see
     * #addCdiTransactionScopedEntityManagerBeans(AfterBeanDiscovery,
     * Set)
     *
     * @see #addExtendedEntityManagerBeans(AfterBeanDiscovery, Set,
     * BeanManager)
     *
     * @see #addNonTransactionalEntityManagerBeans(AfterBeanDiscovery,
     * Set, BeanManager)
     *
     * @see
     * #addJpaTransactionScopedEntityManagerBeans(AfterBeanDiscovery,
     * Set)
     */
    private void addContainerManagedJpaBeans(AfterBeanDiscovery event, BeanManager beanManager) {
        for (Set<Annotation> qualifiers : this.persistenceUnitQualifiers) {
            addContainerManagedEntityManagerFactoryBeans(event, qualifiers, beanManager);
        }
        if (this.transactionsSupported) {
            for (Set<Annotation> qualifiers : this.persistenceContextQualifiers) {
                // Note that each add* method invoked below is responsible for ensuring that it adds beans only once if
                // at all, i.e. for validating and de-duplicating the qualifiers that it is supplied with if necessary.
                addContainerManagedEntityManagerFactoryBeans(event, qualifiers, beanManager);

                // addCdiTransactionScopedEntityManagerBeans(event, qualifiers);
                if (qualifiers.contains(Extended.Literal.INSTANCE)) {
                    addExtendedEntityManagerBeans(event, qualifiers, beanManager);
                } else {
                    assert qualifiers.contains(JpaTransactionScoped.Literal.INSTANCE);
                    addNonTransactionalEntityManagerBeans(event, qualifiers, beanManager);
                    addJpaTransactionScopedEntityManagerBeans(event, qualifiers);
                }
            }
        } else {
            for (Set<Annotation> qualifiers : this.persistenceContextQualifiers) {
                // Note that each add* method invoked below is responsible for ensuring that it adds beans only once if
                // at all, i.e. for validating the qualifiers that it is supplied with.
                addContainerManagedEntityManagerFactoryBeans(event, qualifiers, beanManager);
            }
        }
    }

    private void addContainerManagedEntityManagerFactoryBeans(AfterBeanDiscovery event,
                                                              Set<? extends Annotation> suppliedQualifiers,
                                                              BeanManager beanManager) {
        // Provide support for, e.g.:
        //   @Inject
        //   @ContainerManaged
        //   @Named("test")
        //   private final EntityManagerFactory emf;
        Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        if (this.containerManagedEntityManagerFactoryQualifiers.add(qualifiers)) {
            event.addBean()
                .addTransitiveTypeClosure(EntityManagerFactory.class)
                .scope(Singleton.class)
                .addQualifiers(qualifiers)
                .produceWith(instance -> {
                        // On its own line to ease debugging.
                        return EntityManagerFactories.createContainerManagedEntityManagerFactory(instance,
                                                                                                 qualifiers,
                                                                                                 beanManager);
                    })
                .disposeWith((emf, instance) -> {
                        if (emf.isOpen()) {
                            emf.close();
                        }
                    });
        }
    }

    private void addExtendedEntityManagerBeans(AfterBeanDiscovery event,
                                               Set<Annotation> suppliedQualifiers,
                                               BeanManager beanManager) {
        if (!this.transactionsSupported) {
            throw new IllegalStateException();
        }

        // Provide support for, e.g.:
        //   @Inject
        //   @ContainerManaged
        //   @Extended
        //   @Synchronized // or @Unsynchronized, or none
        //   @Named("test")
        //   private final EntityManager extendedEm;
        /*
        Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        qualifiers.add(Extended.Literal.INSTANCE);
        qualifiers.remove(JpaTransactionScoped.Literal.INSTANCE);
        qualifiers.remove(CdiTransactionScoped.Literal.INSTANCE);
        qualifiers.remove(NonTransactional.Literal.INSTANCE);
        */

        /*
        event.<ExtendedEntityManager>addBean()
            .addTransitiveTypeClosure(ExtendedEntityManager.class)
            .scope(ReferenceCounted.class)
            .qualifiers(qualifiers)
            .produceWith(instance -> {
                    // On its own line to ease debugging.
                    return new ExtendedEntityManager(instance, suppliedQualifiers, beanManager);
                })
            .disposeWith((em, instance) -> em.closeDelegates());
        */
    }

    private void addNonTransactionalEntityManagerBeans(AfterBeanDiscovery event,
                                                       Set<Annotation> suppliedQualifiers,
                                                       BeanManager beanManager) {
        // Provide support for, e.g.:
        //   @Inject
        //   @NonTransactional
        //   @Named("test")
        //   private final EntityManager nonTransactionalEm;
        Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);
        qualifiers.add(NonTransactional.Literal.INSTANCE);
        if (this.nonTransactionalEntityManagerQualifiers.add(qualifiers)) {
            /*
            event.<NonTransactionalEntityManager>addBean()
                .addTransitiveTypeClosure(NonTransactionalEntityManager.class)
                .scope(ReferenceCounted.class)
                .addQualifiers(qualifiers)
                .produceWith(instance -> {
                        // On its own line to ease debugging.
                        return new NonTransactionalEntityManager(instance, suppliedQualifiers);
                    })
                // Revisit: ReferenceCountedContext does not
                // automatically pick up synthetic beans like this
                // one.  So we have to tell it somehow to "work on"
                // this bean.  Right now this bean is in what amounts
                // to a thread-specific singleton scope.  As it
                // happens, this might actually be OK.
                .disposeWith((em, instance) -> {
                        if (em.isOpen()) {
                            em.close();
                        }
                    });
            */
        }
    }

    private void addJpaTransactionScopedEntityManagerBeans(AfterBeanDiscovery event,
                                                           Set<Annotation> suppliedQualifiers) {
        if (!this.transactionsSupported) {
            throw new IllegalStateException();
        }

        // Provide support for, e.g.:
        //   @Inject
        //   @ContainerManaged
        //   @JpaTransactionScoped
        //   @Synchronized // or @Unsynchronized, or none
        //   @Named("test")
        //   private final EntityManager jpaTransactionScopedEm;
        Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        qualifiers.add(JpaTransactionScoped.Literal.INSTANCE);
        qualifiers.remove(CdiTransactionScoped.Literal.INSTANCE);
        qualifiers.remove(Extended.Literal.INSTANCE);
        qualifiers.remove(NonTransactional.Literal.INSTANCE);

        event.addBean()
            .addTransitiveTypeClosure(JtaEntityManager.class)
            .scope(Dependent.class)
            .qualifiers(qualifiers)
            .produceWith(JpaExtension2::createJtaEntityManager)
            .disposeWith((e, i) -> e.delegate().close());

        /*
        event.<JpaTransactionScopedEntityManager>addBean()
            .addTransitiveTypeClosure(JpaTransactionScopedEntityManager.class)
            .scope(Dependent.class)
            .addQualifiers(qualifiers)
            .produceWith(instance -> {
                    // On its own line to ease debugging.
                    return new JpaTransactionScopedEntityManager(instance, suppliedQualifiers);
                })
            .disposeWith(JpaTransactionScopedEntityManager::dispose);
        */
    }

    private static JtaEntityManager createJtaEntityManager(Instance<Object> instance) {
        Set<Annotation> sq = new HashSet<>(instance.select(new TypeLiteral<Bean<JtaEntityManager>>() {}).get().getQualifiers());
        sq.remove(Any.Literal.INSTANCE);
        sq.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);

        Annotation[] sqa = sq.toArray(EMPTY_ANNOTATION_ARRAY);
        Instance<Map<? extends String, ?>> i = instance.select(new TypeLiteral<Map<? extends String, ?>>() {}, sqa);
        return
            new JtaEntityManager(instance.select(EntityManagerFactory.class, sqa).get(),
                                 sq.contains(Unsynchronized.Literal.INSTANCE) ? UNSYNCHRONIZED : SYNCHRONIZED,
                                 i.isUnsatisfied() ? null : Map.copyOf(i.get()),
                                 instance.select(TransactionSynchronizationRegistry.class).get(),
                                 extractNamed(sq).value());
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

    private static URL locationOf(AnnotatedType<?> at) {
        return locationOf(at.getJavaClass());
    }

    private static URL locationOf(Class<?> c) {
        ProtectionDomain pd = c.getProtectionDomain();
        if (pd != null) {
            CodeSource cs = pd.getCodeSource();
            if (cs != null) {
                return cs.getLocation();
            }
        }
        return null;
    }

    private static Named extractNamed(Iterable<? extends Annotation> qualifiers) {
        for (Annotation q : qualifiers) {
            if (q instanceof Named n) {
                return n;
            }
        }
        return null;
    }

    private static void sink(Object o1, Object o2) {

    }

}
