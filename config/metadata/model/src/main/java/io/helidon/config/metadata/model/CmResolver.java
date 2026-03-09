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
package io.helidon.config.metadata.model;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.metadata.model.CmModel.CmEnum;
import io.helidon.config.metadata.model.CmModel.CmType;

/**
 * Config metadata resolver.
 * <p>
 * <b>This class is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or deletion without notice.</b>
 * </p>
 */
public interface CmResolver {

    /**
     * Create a new resolver instance.
     *
     * @param metadata metadata
     * @return CmResolver
     */
    static CmResolver create(CmModel metadata) {
        return new CmResolverImpl(metadata);
    }

    /**
     * Find config types that provide the given contract.
     *
     * @param typeName contract type name
     * @return list of tree nodes
     */
    List<CmType> providers(String typeName);

    /**
     * Get the provided contract type names.
     *
     * @return list of type names
     */
    List<String> contracts();

    /**
     * Find a config type by name.
     *
     * @param typeName type name
     * @return config type
     */
    Optional<CmType> type(String typeName);

    /**
     * Find usages of a config type.
     *
     * @param typeName type name
     * @return set of tree nodes (options)
     */
    Set<CmNode> usage(String typeName);

    /**
     * Get the tree view of the config metadata.
     *
     * @return list of root tree nodes
     */
    List<CmNode> roots();

    /**
     * Get all the config types.
     *
     * @return list of config types
     */
    List<CmType> types();

    /**
     * Get all the enum types.
     *
     * @return list of enum types
     */
    List<CmEnum> enums();

    /**
     * Test if the given type is an enum.
     *
     * @param typeName type name
     * @return {@code true} if an enum, {@code false} otherwise
     */
    boolean isEnum(String typeName);
}
