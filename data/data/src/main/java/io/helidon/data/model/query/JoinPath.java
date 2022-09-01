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
package io.helidon.data.model.query;


//import io.micronaut.core.util.ArrayUtils;
import io.helidon.data.annotation.Join;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentProperty;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A join path represents a path, association and join type for performing a join with a query.
 *
 * @author graemerocher
 * @see QueryModel
 * @since 1.0.0
 */
public class JoinPath {

    private final String path;
    private final Association[] associationPath;
    private final Join.Type joinType;
    private final String alias;

    /**
     * Default constructor.
     *
     * @param path            The path
     * @param associationPath The association
     * @param joinType        The join type
     * @param alias           The alias
     */
    public JoinPath(@NonNull String path, @NonNull Association[] associationPath, @NonNull Join.Type joinType, @Nullable String alias) {
        this.path = path;
        this.associationPath = associationPath;
        this.joinType = joinType;
        this.alias = alias;
    }

    /**
     * The alias for the join path.
     *
     * @return The optional alias
     */
    public Optional<String> getAlias() {
        return Optional.ofNullable(alias);
    }

    @Override
    public String toString() {
        return path;
    }

    /**
     * @return The association
     */
    public @NonNull
    Association getAssociation() {
        return associationPath[associationPath.length - 1];
    }

    /**
     * @return The association path.
     */
    public Association[] getAssociationPath() {
        return associationPath;
    }

    /**
     * @return The association path
     */
    public @NonNull
    String getPath() {
        return path;
    }

    /**
     * @return The join type
     */
    public @NonNull
    Join.Type getJoinType() {
        return joinType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JoinPath joinPath = (JoinPath) o;
        return path.equals(joinPath.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    /**
     * Create a join path from the association path.
     *
     * @param associationPath The association path
     * @return The join path
     */
    public static JoinPath of(Association... associationPath) {
        if (ArrayUtils.isEmpty(associationPath)) {
            throw new IllegalArgumentException("Association path cannot be empty");
        }

        String path = Arrays.stream(associationPath)
                .map(PersistentProperty::getName)
                .collect(Collectors.joining("."));
        return new JoinPath(path, associationPath, Join.Type.DEFAULT, null);
    }

    /**
     * Create a join path from the association path.
     *
     * @param alias           The alias to use
     * @param associationPath The association path
     * @return The join path
     */
    public static JoinPath of(String alias, Association... associationPath) {
        if (ArrayUtils.isEmpty(associationPath)) {
            throw new IllegalArgumentException("Association path cannot be empty");
        }

        String path = Arrays.stream(associationPath)
                .map(PersistentProperty::getName)
                .collect(Collectors.joining("."));
        return new JoinPath(path, associationPath, Join.Type.DEFAULT, alias);
    }
}
