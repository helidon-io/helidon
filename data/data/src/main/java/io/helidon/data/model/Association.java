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

//import io.micronaut.core.util.ArrayUtils;
import io.helidon.data.annotation.Relation;
import io.helidon.data.model.naming.NamingStrategy;

import java.util.Optional;

/**
 * A property that represents an association.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface Association extends PersistentProperty {

    /**
     * @return The alias name representation.
     */
    default String getAliasName() {
        return NamingStrategy.DEFAULT.mappedName(getName()) + "_";
    }

    /**
     * The associated entity if any.
     * @return The associated entity
     */
    PersistentEntity getAssociatedEntity();

    /**
     * Retrieves the inverse side of the association. If there is one.
     *
     * @return The association.
     */
    default Optional<? extends Association> getInverseSide() {
        return getAnnotationMetadata()
                .stringValue(Relation.class, "mappedBy")
                .flatMap(s -> {
                    final PersistentProperty persistentProperty = getAssociatedEntity().getPropertyByPath(s).orElse(null);
                    if (persistentProperty instanceof Association) {
                        return Optional.of((Association) persistentProperty);
                    }
                    return Optional.empty();
                });
    }

    /**
     * Retrieves the inverse side path of the association. If there is one.
     *
     * @return The association.
     */
    default Optional<PersistentAssociationPath> getInversePathSide() {
        return getAnnotationMetadata()
                .stringValue(Relation.class, "mappedBy")
                .flatMap(s -> {
                    final PersistentPropertyPath persistentPropertyPath = getAssociatedEntity().getPropertyPath(s);
                    if (persistentPropertyPath instanceof PersistentAssociationPath) {
                        return Optional.of((PersistentAssociationPath) persistentPropertyPath);
                    }
                    return Optional.empty();
                });
    }

    /**
     * Whether the relationship is bidirectional.
     * @return True if it is bidirectional.
     */
    default boolean isBidirectional() {
        return getInverseSide().isPresent();
    }

    /**
     * @return The relationship kind
     */
    default @NonNull Relation.Kind getKind() {
        return findAnnotation(Relation.class)
                .flatMap(av -> av.enumValue(Relation.Kind.class))
                .orElse(Relation.Kind.ONE_TO_ONE);
    }

    /**
     * @return Whether the association is a foreign key association
     */
    default boolean isForeignKey() {
        Relation.Kind kind = getKind();
        return kind == Relation.Kind.ONE_TO_MANY || kind == Relation.Kind.MANY_TO_MANY || (kind == Relation.Kind.ONE_TO_ONE && getAnnotationMetadata().stringValue(Relation.class, "mappedBy").isPresent());
    }

    /**
     * Whether this association cascades the given types.
     * @param types The types
     * @return True if it does, false otherwise.
     */
    default boolean doesCascade(Relation.Cascade... types) {
        if (ArrayUtils.isNotEmpty(types)) {
            final String[] cascades = getAnnotationMetadata().stringValues(Relation.class, "cascade");
            for (String cascade : cascades) {
                if (cascade.equals("ALL")) {
                    return true;
                }
                for (Relation.Cascade type : types) {
                    final String n = type.name();
                    if (n.equals(cascade)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
