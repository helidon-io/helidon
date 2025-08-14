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
package io.helidon.docs.includes.data;

import java.util.List;
import java.util.Optional;

import io.helidon.data.Data;
import io.helidon.data.PageRequest;
import io.helidon.data.Slice;
import io.helidon.data.Sort;

/**
 * Pet data repository interface with additional snippets.
 */
@Data.Repository
public interface PetRepositorySnippets extends Data.GenericRepository<Pet, Integer> {

    // tag::qbmn_method[]
    Number countByName(String name);
    long longCountByName(String name);
    Long boxedLongCountByName(String name);
    int intCountByName(String name);
    Integer boxedIntCountByName(String name);
    short shortCountByName(String name);
    Short boxedShortCountByName(String name);
    // end::qbmn_method[]

    // tag::qbmn_sort_method[]
    List<Keeper> findAllOrderByAgeAscName();
    // end::qbmn_sort_method[]

    // tag::qbmn_slice_method[]
    Slice<Keeper> listOrderByName(PageRequest pageRequest);
    // end::qbmn_slice_method[]

    // tag::qbmn_dynamic_sort_method[]
    List<Keeper> findByAgeBetween(int min, int max, Sort sort);
    // end::qbmn_dynamic_sort_method[]

    // tag::qbmn_dual_sort_method[]
    List<Keeper> findByAgeBetweenOrderByAge(int min, int max, Sort sort);
    // end::qbmn_dual_sort_method[]

    // tag::selectKeeper_method[]
    @Data.Query("SELECT p.keeper FROM Pet p WHERE k.name = $1 AND p.category.name = $2")
    Optional<Keeper> selectKeeper(String name, String category);
    // end::selectKeeper_method[]

}
