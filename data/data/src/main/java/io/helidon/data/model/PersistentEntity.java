/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.model;

//import io.micronaut.core.beans.BeanIntrospection;
//import io.micronaut.core.naming.NameUtils;
//import io.micronaut.core.util.ArgumentUtils;
//import io.micronaut.core.util.CollectionUtils;
//import io.micronaut.core.util.StringUtils;
import io.helidon.core.utils.CollectionUtils;
import io.helidon.core.utils.NameUtils;
import io.helidon.data.annotation.Embeddable;
import io.helidon.data.model.naming.NamingStrategy;
import io.helidon.data.model.runtime.RuntimePersistentEntity;


import java.util.*;
import java.util.stream.Collectors;

import static io.helidon.data.model.AssociationUtils.CAMEL_CASE_SPLIT_PATTERN;

/**
 * Models a persistent entity and provides an API that can be used both within the compiler and at runtime. The {@link io.micronaut.core.annotation.AnnotationMetadata} provided is consistent both at runtime and compilation time.
 *
 * @see PersistentProperty
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public interface PersistentEntity extends PersistentElement {

    /**
     * The entity name including any package prefix.
     *
     * @return The entity name
     */
    String getName();

    /**
     * @return A name to use when referring to this element via an alias.
     */
    String getAliasName();

    /**
     * Has composite identity.
     *
     * @return The true if composite identity present
     */
    default boolean hasCompositeIdentity() {
        return getCompositeIdentity() != null;
    }

    /**
     * Has identity.
     *
     * @return The true if identity present
     */
    default boolean hasIdentity() {
        return getIdentity() != null;
    }

    /**
     * The composite id.
     *
     * @return The composite id or null if there isn't one
     */
    PersistentProperty[] getCompositeIdentity();

    /**
     * Returns the identity of the instance.
     *
     * @return The identity or null if there isn't one
     */
    PersistentProperty getIdentity();

    /**
     * Returns the version property.
     *
     * @return the property
     */
    PersistentProperty getVersion();

    /**
     * Is the entity versioned for optimistic locking.
     *
     * @return true if versioned
     */
    default boolean isVersioned() {
        return getVersion() != null;
    }

    /**
     * A list of properties to be persisted.
     * @return A list of PersistentProperty instances
     */
    Collection<? extends PersistentProperty> getPersistentProperties();

    /**
     * A list of the associations for this entity. This is typically
     * a subset of the list returned by {@link #getPersistentProperties()}
     *
     * @return A list of associations
     */
    default Collection<? extends Association> getAssociations() {
        return getPersistentProperties()
                .stream()
                .filter(bp -> bp instanceof Association)
                .map(bp -> (Association) bp)
                .collect(Collectors.toList());
    }

    /**
     * A list of embedded associations for this entity. This is typically
     * a subset of the list returned by {@link #getPersistentProperties()}
     *
     * @return A list of associations
     */
    default Collection<Embedded> getEmbedded() {
        return getPersistentProperties().stream()
                .filter(p -> p instanceof Embedded)
                .map(p -> (Embedded) p)
                .collect(Collectors.toList());
    }

    /**
     * Obtains a PersistentProperty instance by name.
     *
     * @param name The name of the property
     * @return The PersistentProperty or null if it doesn't exist
     */
    PersistentProperty getPropertyByName(String name);

    /**
     * Obtains an identity PersistentProperty instance by name.
     *
     * @param name The name of the identity property
     * @return The PersistentProperty or null if it doesn't exist
     */
    default PersistentProperty getIdentityByName(String name) {
        PersistentProperty identity = getIdentity();
        if (identity != null && identity.getName().equals(name)) {
            return identity;
        }
        PersistentProperty[] compositeIdentities = getCompositeIdentity();
        if (compositeIdentities != null) {
            for (PersistentProperty compositeIdentity : compositeIdentities) {
                if (compositeIdentity.getName().equals(name)) {
                    return compositeIdentity;
                }
            }
        }
        return null;
    }

    /**
     * A list of property names that a persistent.
     * @return A List of strings
     */
    Collection<String> getPersistentPropertyNames();

    /**
     * @return Is the entity embeddable.
     */
    default boolean isEmbeddable() {
        return getAnnotationMetadata().hasAnnotation(Embeddable.class);
    }

    /**
     * @return The simple name without the package of entity
     */
    default String getSimpleName() {
        return NameUtils.getSimpleName(getName());
    }

    /**
     * @return Returns the name of the class decapitalized form
     */
    default String getDecapitalizedName() {
        return NameUtils.decapitalize(getSimpleName());
    }

    /**
     * Returns whether the specified entity asserts ownership over this
     * entity.
     *
     * @param owner The owning entity
     * @return True if it does own this entity
     */
    boolean isOwningEntity(PersistentEntity owner);

    /**
     * Returns the parent entity of this entity.
     * @return The ParentEntity instance
     */
    PersistentEntity getParentEntity();

    /**
     * Computes a dot separated property path for the given camel case path.
     * @param camelCasePath The camel case path
     * @return The dot separated version or null if it cannot be computed
     */
    default Optional<String> getPath(String camelCasePath) {
        List<String> path = Arrays.stream(CAMEL_CASE_SPLIT_PATTERN.split(camelCasePath))
                                  .map(NameUtils::decapitalize)
                                  .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(path)) {
            Iterator<String> i = path.iterator();
            StringBuilder b = new StringBuilder();
            PersistentEntity currentEntity = this;
            String name = null;
            while (i.hasNext()) {
                name = name == null ? i.next() : name + NameUtils.capitalize(i.next());
                PersistentProperty sp = currentEntity.getPropertyByName(name);
                if (sp == null) {
                    PersistentProperty identity = currentEntity.getIdentity();
                    if (identity != null) {
                        if (identity.getName().equals(name)) {
                            sp = identity;
                        } else if (identity instanceof Association) {
                            PersistentEntity idEntity = ((Association) identity).getAssociatedEntity();
                            sp = idEntity.getPropertyByName(name);
                        }
                    }
                }
                if (sp != null) {
                    if (sp instanceof Association) {
                        b.append(name);
                        if (i.hasNext()) {
                            b.append('.');
                        }
                        currentEntity = ((Association) sp).getAssociatedEntity();
                        name = null;
                    } else if (!i.hasNext()) {
                        b.append(name);
                    }
                }
            }

            return b.length() == 0 || b.charAt(b.length() - 1) == '.' ? Optional.empty() : Optional.of(b.toString());

        }
        return Optional.empty();
    }

    /**
     * Obtains the root entity of an inheritance hierarchy.
     * @return The root entity
     */
    default PersistentEntity getRootEntity() {
        return this;
    }

    /**
     * Whether this entity is a root entity.
     * @return True if it is a root entity
     */
    default boolean isRoot() {
        return getRootEntity() == this;
    }

    /**
     * Return a property for a dot separated property path such as {@code foo.bar.prop}
     * .
     * @param path The path
     * @return The property
     */
    default Optional<PersistentProperty> getPropertyByPath(String path) {
        if (path.indexOf('.') == -1) {
            PersistentProperty pp = getPropertyByName(path);
            if (pp == null) {
                PersistentProperty identity = getIdentity();
                if (identity != null) {
                    if (identity.getName().equals(path)) {
                        pp = identity;
                    } else if (identity instanceof Embedded) {
                        PersistentEntity idEntity = ((Embedded) identity).getAssociatedEntity();
                        pp = idEntity.getPropertyByName(path);
                    }
                }
            }
            return Optional.ofNullable(pp);
        } else {
            String[] tokens = path.split("\\.");
            PersistentEntity startingEntity = this;
            PersistentProperty prop = null;
            for (String token : tokens) {
                prop = startingEntity.getPropertyByName(token);
                if (prop == null) {
                    PersistentProperty identity = startingEntity.getIdentity();
                    if (identity != null && identity.getName().equals(token)) {
                        prop = identity;
                    } else {
                        return Optional.empty();
                    }
                }
                if (prop instanceof Association) {
                    startingEntity = ((Association) prop).getAssociatedEntity();
                }
            }
            return Optional.ofNullable(prop);
        }
    }

    /**
     * Return a {@link PersistentPropertyPath} by path such as {@code foo.bar.prop}.
     * .
     * @param path The path
     * @return The properties
     */
    @Nullable
    default PersistentPropertyPath getPropertyPath(String path) {
        if (path.indexOf('.') == -1) {
            return getPropertyPath(new String[] {path});
        }
        return getPropertyPath(StringUtils.splitOmitEmptyStringsList(path, '.').toArray(new String[0]));
    }

    /**
     * Return a {@link PersistentPropertyPath} by path such as {@code foo.bar.prop}.
     * .
     * @param propertyPath The path
     * @return The properties
     */
    @Nullable
    default PersistentPropertyPath getPropertyPath(String[] propertyPath) {
        if (propertyPath.length == 0) {
            return null;
        }
        if (propertyPath.length == 1) {
            String propertyName = propertyPath[0];
            PersistentProperty pp = getPropertyByName(propertyName);
            if (pp == null) {
                PersistentProperty identity = getIdentity();
                if (identity != null) {
                    if (identity.getName().equals(propertyName)) {
                        pp = identity;
                    } else if (identity instanceof Embedded) {
                        PersistentEntity idEntity = ((Embedded) identity).getAssociatedEntity();
                        pp = idEntity.getPropertyByName(propertyName);
                        if (pp != null) {
                            return PersistentPropertyPath.of(Collections.singletonList((Embedded) identity), pp, identity.getName() + "." + pp.getName());
                        }
                    }
                }
                PersistentProperty version = getVersion();
                if (version != null) {
                    if (version.getName().equals(propertyName)) {
                        pp = version;
                    }
                }
            }
            return pp == null ? null : PersistentPropertyPath.of(Collections.emptyList(), pp, propertyName);
        } else {
            List<Association> associations = new ArrayList<>(propertyPath.length - 1);
            PersistentEntity startingEntity = this;
            for (int i = 0; i < propertyPath.length - 1; i++) {
                String propertyName = propertyPath[i];
                PersistentProperty prop = startingEntity.getPropertyByName(propertyName);
                if (prop instanceof Association) {
                    Association association = (Association) prop;
                    startingEntity = association.getAssociatedEntity();
                    associations.add(association);
                } else {
                    if (prop == null) {
                        return null;
                    }
                    throw new IllegalArgumentException("Invalid association path. Property [" + propertyName + "] of [" + startingEntity + "] is not an association in [" + String.join(".", propertyPath) + "]");
                }
            }
            PersistentProperty prop = startingEntity.getPropertyByName(propertyPath[propertyPath.length - 1]);
            if (prop == null) {
                return null;
            }
            return PersistentPropertyPath.of(associations, prop);
        }
    }

    /**
     * Obtain the naming strategy for the entity.
     * @return The naming strategy
     */
    NamingStrategy getNamingStrategy();

    /**
     * Find the naming strategy that is defined for the entity.
     * @return The optional naming strategy
     */
    Optional<NamingStrategy> findNamingStrategy();

    /**
     * Creates a new persistent entity representation of the given type. The type
     * must be annotated with {@code io.micronaut.core.annotation.Introspected}. This method will create a new instance on demand and does not cache.
     *
     * @param type The type
     * @param <T> The generic type
     * @return The entity
     */
    static <T> RuntimePersistentEntity<T> of(Class<T> type) {
//        ArgumentUtils.requireNonNull("type", type);
        return new RuntimePersistentEntity<>(type);
    }

    /**
     * Creates a new persistent entity representation of the given type. The type
     * must be annotated with {@code io.micronaut.core.annotation.Introspected}. This method will create a new instance on demand and does not cache.
     *
     * @param introspection The introspection
     * @param <T> The generic type
     * @return The entity
     */
    static <T> RuntimePersistentEntity<T> of(BeanIntrospection<T> introspection) {
//        ArgumentUtils.requireNonNull("introspection", introspection);
        return new RuntimePersistentEntity<T>(introspection);
    }
}
