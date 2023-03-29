/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tools;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;

/**
 * These are the individual items that compose the {@link ModuleInfoDescriptor} builder.
 * <p>
 * Note that generally speaking the majority of use cases can use methods from {@link ModuleInfoDescriptor}
 * instead of needing to use anything provided here.
 */
@Builder
public interface ModuleInfoItem {

    /**
     * The comments that proceed the item definition.
     *
     * @return pre-comments
     */
    @Singular
    List<String> precomments();

    /**
     * The target class name, package name, or module name this item applies to.
     *
     * @return target
     */
    String target();

    /**
     * Returns true if this is a {@code requires} definition.
     *
     * @return true if this item is using requires
     */
    boolean requires();

    /**
     * Returns true if this is a {@code uses} definition.
     *
     * @return true if this item is using requires
     */
    boolean uses();

    /**
     * Returns true if this is using a {@code transitive} definition.
     *
     * @return true if this item is using transitive
     */
    boolean isTransitiveUsed();

    /**
     * Returns true if this is using a {@code static} definition.
     *
     * @return true if this item is using static
     */
    // see https://github.com/helidon-io/helidon/issues/5440
    boolean isStaticUsed();

    /**
     * Returns true if this is using a {@code exports} definition.
     *
     * @return true if this item is using exports
     */
    boolean exports();

    /**
     * Returns true if this is using a {@code open} definition.
     *
     * @return true if this item is using opens
     */
    boolean opens();

    /**
     * Returns true if this is using a {@code provides} definition.
     *
     * @return true if this item is using provides
     */
    boolean provides();

    /**
     * Applicable if the target is referencing a list of target class or package names.
     *
     * @return ordering
     */
    Optional<ModuleInfoDescriptor.Ordering> ordering();

    /**
     * Any {@code with} or {@code to} definitions.
     *
     * @return the set of with or to definitions
     */
    @Singular
    Set<String> withOrTo();

    /**
     * If there is any {@link #ordering()} establish requiring a sort then a new sorted set will be returned.
     *
     * @return optionally the sorted set, else it is the same as {@link #withOrTo()}
     */
    default Set<String> withOrToSorted() {
        if (ordering().isPresent()
                && ordering().get() == ModuleInfoDescriptor.Ordering.SORTED) {
            return new TreeSet<>(withOrTo());
        }

        return withOrTo();
    }

    /**
     * Provides the content of the description item appropriate to write out.
     *
     * @return The contents (source code body) for this descriptor item.
     */
    default String contents() {
        StringBuilder builder = new StringBuilder();
        boolean handled = false;
        if (uses()) {
            assert (!requires());
            assert (!isTransitiveUsed());
            assert (!isStaticUsed());
            assert (!opens());
            assert (!exports());
            builder.append("uses ");
            handled = true;
        }
        if (provides()) {
            assert (!requires());
            assert (!isTransitiveUsed());
            assert (!isStaticUsed());
            assert (!opens());
            assert (!exports());
            if (builder.length() > 0) {
                builder.append(target()).append(";\n    ");
            }
            builder.append("provides ");
            if (!withOrTo().isEmpty()) {
                builder.append(Objects.requireNonNull(target()));
                builder.append(" with ").append(String.join(",\n\t\t\t", withOrToSorted()));
                return builder.toString();
            }
            handled = true;
        }
        if (opens()) {
            assert (!requires());
            assert (!isTransitiveUsed());
            assert (!isStaticUsed());
            assert (!exports());
            builder.append("opens ");
            handled = true;
        }
        if (requires()) {
            assert (!exports());
            assert (!(isTransitiveUsed() && isStaticUsed()));
            builder.append("requires ");
            if (isTransitiveUsed()) {
                builder.append("transitive ");
            } else if (isStaticUsed()) {
                builder.append("static ");
            }
            handled = true;
        }
        if (exports()) {
            assert (!isTransitiveUsed());
            assert (!isStaticUsed());
            builder.append("exports ");
            if (!withOrTo().isEmpty()) {
                builder.append(Objects.requireNonNull(target()));
                builder.append(" to ").append(String.join(",\n\t\t\t", withOrToSorted()));
                return builder.toString();
            }
            handled = true;
        }
        assert (handled) : target();
        builder.append(target());
        return builder.toString();
    }

    /**
     * Provides the ability to create a new merged descriptor item using this as the basis, and then combining another into it
     * in order to create a new descriptor item.
     *
     * @param another the other descriptor item to merge
     * @return the merged descriptor
     */
    default ModuleInfoItem mergeCreate(
            ModuleInfoItem another) {
        if (another == this) {
            return this;
        }

        if (!Objects.equals(target(), another.target())) {
            throw new IllegalArgumentException();
        }

        DefaultModuleInfoItem.Builder newOne = DefaultModuleInfoItem.toBuilder(another);
        another.precomments().forEach(newOne::addPrecomment);
        newOne.requires(requires() || another.requires());
        newOne.transitiveUsed(isTransitiveUsed() || another.isTransitiveUsed());
        newOne.staticUsed(isStaticUsed() || another.isStaticUsed());
        newOne.requires(exports() || another.exports());
        newOne.opens(opens() || another.opens());
        newOne.provides(opens() || another.provides());
        another.withOrTo().forEach(newOne::addWithOrTo);
        return newOne.build();
    }

}
