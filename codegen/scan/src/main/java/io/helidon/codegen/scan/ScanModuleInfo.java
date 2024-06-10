/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen.scan;

import java.lang.module.ModuleDescriptor;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.ModuleInfoRequires;
import io.helidon.common.types.TypeName;

import io.github.classgraph.ModuleRef;

/**
 * Module info created from classpath scanning.
 */
public final class ScanModuleInfo {
    private ScanModuleInfo() {
    }

    /**
     * Map a module reference to module descriptor.
     *
     * @param scanModuleInfo module info from classpath scanning
     * @return module info if it is possible to parse it from the module ref
     */
    public static Optional<ModuleInfo> map(ModuleRef scanModuleInfo) {
        Object descriptor = scanModuleInfo.getDescriptor();
        if (!(descriptor instanceof ModuleDescriptor javaDescriptor)) {
            return Optional.empty();
        }
        ModuleInfo.Builder builder = ModuleInfo.builder()
                .name(javaDescriptor.name())
                .isOpen(javaDescriptor.isOpen());

        javaDescriptor.exports()
                .forEach(it -> builder.putExports(it.source(), List.copyOf(it.targets())));

        javaDescriptor.opens()
                .forEach(opens -> builder.putOpen(opens.source(), List.copyOf(opens.targets())));

        javaDescriptor.uses()
                .forEach(uses -> builder.addUse(TypeName.create(uses)));

        javaDescriptor.requires()
                .stream()
                .map(it -> new ModuleInfoRequires(it.name(),
                                                  isTransitive(it.modifiers()),
                                                  isStatic(it.modifiers())))
                .forEach(builder::addRequire);

        javaDescriptor.provides()
                .forEach(it -> builder.putProvide(TypeName.create(it.service()),
                                                  it.providers()
                                                          .stream()
                                                          .map(TypeName::create)
                                                          .toList()));

        return Optional.of(builder.build());
    }

    private static boolean isTransitive(Set<ModuleDescriptor.Requires.Modifier> modifiers) {
        return modifiers.contains(ModuleDescriptor.Requires.Modifier.TRANSITIVE);
    }

    private static boolean isStatic(Set<ModuleDescriptor.Requires.Modifier> modifiers) {
        return modifiers.contains(ModuleDescriptor.Requires.Modifier.STATIC);
    }
}
