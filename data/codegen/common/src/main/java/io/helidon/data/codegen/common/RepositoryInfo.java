/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.codegen.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

/**
 * Data repository interface descriptor (info).
 *
 * @param interfaceInfo  type info
 * @param interfacesInfo implemented interfaces
 * @param entityInfo     type of the entity
 * @param id             type of the ID
 */
public record RepositoryInfo(TypeInfo interfaceInfo,
                             Map<TypeName, RepositoryInterfaceInfo> interfacesInfo,
                             TypeInfo entityInfo,
                             TypeName id) {

    /**
     * Extract entity alias from class name.
     * Returns 1st lowercase letter from {@code className} or {@code "e"} when no letter was found.
     * Class names like {@code __} are valid so there must be a fallback option.
     *
     * @param className entity simple class name (without package)
     * @return entity alias
     */
    public static String entityAlias(String className) {
        int pos = 0;
        char alias = className.charAt(pos);
        // The most common case shortcut
        if (Character.isLetter(alias)) {
            return Character.toString(Character.toLowerCase(alias));
        }
        while (!Character.isLetter(alias) && ++pos < className.length()) {
            alias = className.charAt(pos);
        }
        return Character.isLetter(alias)
                ? Character.toString(Character.toLowerCase(alias))
                : "e";
    }

    /**
     * Extract entity alias from class name.
     * Returns 1st lowercase letter from {@code typeName} or {@code "e"} when no letter was found.
     * Class names like {@code __} are valid so there must be a fallback option.
     *
     * @param typeName entity type name
     * @return entity alias
     */
    public static String entityAlias(TypeName typeName) {
        return entityAlias(typeName.className());
    }

    /**
     * Entity {@link TypeName}.
     *
     * @return type name of the entity
     */
    public TypeName entity() {
        return entityInfo.typeName();
    }

    /**
     * Entity ID attribute.
     *
     * @return name of the Entity ID attribute
     */
    public String idName() {
        // NEXT VERSION: Implement
        //        Requires ORM model support on codegen level
        return "id";
    }

    /**
     * {@link Set} of implemented interfaces type names.
     *
     * @return implemented interfaces type names
     */
    public Set<TypeName> interfaceNames() {
        return interfacesInfo.keySet();
    }

    /**
     * Data repository interface descriptor {@link io.helidon.common.Builder}.
     */
    public abstract static class Builder implements io.helidon.common.Builder<Builder, RepositoryInfo> {

        private final CodegenContext codegenContext;
        private final Map<TypeName, RepositoryInterfaceInfo> interfaces;
        private TypeInfo interfaceInfo;

        /**
         * Creates an instance of data repository interface descriptor {@link Builder}.
         *
         * @param codegenContext code processing and generation context
         */
        protected Builder(CodegenContext codegenContext) {
            this.codegenContext = codegenContext;
            this.interfaces = new HashMap<>();
            this.interfaceInfo = null;
        }

        /**
         * Add data repository interface type info.
         *
         * @param interfaceInfo interface type info
         * @return this builder
         */
        public Builder interfaceInfo(TypeInfo interfaceInfo) {
            this.interfaceInfo = interfaceInfo;
            return this;
        }

        /**
         * Add implemented interface.
         *
         * @param name implemented interface type name
         * @param info implemented interface info
         * @return this builder
         */
        public Builder addInterface(TypeName name, RepositoryInterfaceInfo info) {
            interfaces.put(name, info);
            return this;
        }

        /**
         * Implemented interfaces.
         *
         * @return interfaces {@link Map}
         */
        protected Map<TypeName, RepositoryInterfaceInfo> interfaces() {
            return interfaces;
        }

        /**
         * Data repository interface type info.
         *
         * @return interface type info
         */
        protected TypeInfo interfaceInfo() {
            return interfaceInfo;
        }

        /**
         * Code processing and generation context.
         *
         * @return codegen context
         */
        protected CodegenContext codegenContext() {
            return codegenContext;
        }

    }

}
