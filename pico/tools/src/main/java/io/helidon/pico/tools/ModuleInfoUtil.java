/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;

final class ModuleInfoUtil {
    private ModuleInfoUtil() {
    }

    /**
     * Takes a builder, and if the target does not yet exist, will add the new module info item from the supplier.
     *
     * @param builder       the fluent builder
     * @param target        the target to check for existence for
     * @param itemSupplier  the item to add which presumably has the same target as above
     * @return true if added
     */
    static boolean addIfAbsent(ModuleInfoDescriptor.Builder builder,
                               String target,
                               Supplier<ModuleInfoItem> itemSupplier) {
        Optional<ModuleInfoItem> existing = builder.first(target);
        if (existing.isEmpty()) {
            ModuleInfoItem item = Objects.requireNonNull(itemSupplier.get());
            assert (target.equals(item.target())) : "target mismatch: " + target + " and " + item.target();
            builder.addItem(item);
            return true;
        }
        return false;
    }

    /**
     * Creates a new item {@code exports} on a package from this module descriptor.
     *
     * @param pkg the package name exported
     * @return the item created
     */
    static ModuleInfoItem exportsPackage(String pkg) {
        return ModuleInfoItem.builder().exports(true).target(pkg).build();
    }

    /**
     * Creates a new item {@code exports} on a package from this module descriptor, along with
     * a {@code 'to'} declaration.
     *
     * @param contract  the contract definition being exported
     * @param to        the to part
     * @return the item created
     */
    static ModuleInfoItem exportsPackage(String contract,
                                         String to) {
        return ModuleInfoItem.builder().exports(true).target(contract).addWithOrTo(to).build();
    }

    /**
     * Creates a new item declaring it to provide some contract from this module definition, along with
     * a {@code 'with'} declaration.
     *
     * @param contract  the contract definition being provided
     * @param with      the with part
     * @return the item created
     */
    static ModuleInfoItem providesContract(String contract,
                                           String with) {
        return ModuleInfoItem.builder().provides(true).target(contract).addWithOrTo(with).build();
    }

    /**
     * Creates a new item declaring a {@code requires} on an external module usage from this module descriptor.
     *
     * @param moduleName the module name to require
     * @return the item created
     */
    static ModuleInfoItem requiresModuleName(String moduleName) {
        return ModuleInfoItem.builder().requires(true).target(moduleName).build();
    }

    /**
     * Creates a new item declaring a {@code requires} on an external module usage from this module descriptor, that is
     * extended to use additional item attributes.
     *
     * @param moduleName    the module name to require
     * @param isTransitive  true if the requires declaration is transitive
     * @param isStatic      true if the requires declaration is static
     * @param comments      any comments to ascribe to the item
     * @return the item created
     */
    static ModuleInfoItem requiresModuleName(String moduleName,
                                             boolean isTransitive,
                                             boolean isStatic,
                                             List<String> comments) {
        return ModuleInfoItem.builder()
                .requires(true)
                .precomments(comments)
                .isTransitiveUsed(isTransitive)
                .isStaticUsed(isStatic)
                .target(moduleName)
                .build();
    }

    /**
     * Creates a new item declaring a {@code uses} external contract definition from this module descriptor.
     *
     * @param externalContract the external contract definition
     * @return the item created
     */
    static ModuleInfoItem usesExternalContract(Class<?> externalContract) {
        return usesExternalContract(TypeName.create(externalContract));
    }

    /**
     * Creates a new item declaring a {@code uses} external contract definition from this module descriptor.
     *
     * @param externalContract the external contract definition
     * @return the item created
     */
    static ModuleInfoItem usesExternalContract(String externalContract) {
        return ModuleInfoItem.builder().uses(true).target(externalContract).build();
    }

    /**
     * Creates a new item declaring a {@code uses} external contract definition from this module descriptor.
     *
     * @param externalContract the external contract definition
     * @return the item created
     */
    static ModuleInfoItem usesExternalContract(TypeName externalContract) {
        return usesExternalContract(externalContract.fqName());
    }
}
