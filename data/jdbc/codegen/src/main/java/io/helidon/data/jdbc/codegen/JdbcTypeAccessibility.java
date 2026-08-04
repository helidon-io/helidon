/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.codegen;

import java.util.ArrayList;
import java.util.List;

import io.helidon.codegen.CodegenContext;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

/**
 * Java accessibility checks shared by JDBC generators.
 */
final class JdbcTypeAccessibility {

    private JdbcTypeAccessibility() {
    }

    /**
     * Tests whether generated code in the repository package can name a type.
     * <p>
     * A nested declaration is accessible only when both the declaration and
     * every enclosing declaration are accessible. Checking only the leaf type
     * would allow code generation to succeed and the generated source to fail
     * later during Java compilation.
     *
     * @param context code-generation context
     * @param typeInfo declaration to test
     * @param repositoryPackage generated repository package
     * @return whether the complete declaration chain is accessible
     */
    static boolean accessible(CodegenContext context, TypeInfo typeInfo, String repositoryPackage) {
        TypeName typeName = typeInfo.typeName().genericTypeName();
        List<String> declarationNames = new ArrayList<>(typeName.enclosingNames());
        declarationNames.add(typeName.className());

        for (int index = 0; index < declarationNames.size() - 1; index++) {
            // CodegenContext looks up nested declarations by binary name.
            String binaryName = String.join("$", declarationNames.subList(0, index + 1));
            TypeName enclosingType = TypeName.create(qualifiedName(typeName.packageName(), binaryName));
            boolean enclosingAccessible = context.typeInfo(enclosingType)
                    .map(TypeInfo::accessModifier)
                    .filter(access -> accessible(access, repositoryPackage.equals(enclosingType.packageName())))
                    .isPresent();
            if (!enclosingAccessible) {
                return false;
            }
        }
        return accessible(typeInfo.accessModifier(), repositoryPackage.equals(typeName.packageName()));
    }

    private static String qualifiedName(String packageName, String className) {
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    private static boolean accessible(AccessModifier access, boolean samePackage) {
        return access == AccessModifier.PUBLIC
                || (samePackage && (access == AccessModifier.PACKAGE_PRIVATE || access == AccessModifier.PROTECTED));
    }
}
