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
package io.helidon.data.tests.codegen.repository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.data.api.Data;
import io.helidon.data.api.JpaData;
import io.helidon.data.api.Query;
import io.helidon.data.api.Sort;
import io.helidon.data.tests.codegen.model.Pokemon;

import jakarta.persistence.EntityManager;

@Data.Repository
public interface PokemonRepository
        extends Data.CrudRepository<Pokemon, Integer>, Data.SessionRepository<EntityManager> {

    // Simple (JPQL) projection get
    Pokemon getByName(String name);
    Optional<Pokemon> optionalGetByName(String name);

    // Dynamic (criteria API) projection get
    Pokemon getByName(String name, Sort sort);
    Optional<Pokemon> optionalGetByName(String name, Sort sort);

    // Simple (JPQL) projection find
    List<Pokemon> find();
    Collection<Pokemon> collectonFindAll();
    Stream<Pokemon> streamFindAll();

    // Dynamic (criteria API) projection find
    List<Pokemon> find(Sort sort);
    Collection<Pokemon> collectonFindAll(Sort sort);
    Stream<Pokemon> streamFindAll(Sort sort);

    // Simple (JPQL) projection list and stream
    Stream<Pokemon> stream();
    List<Pokemon> list();
    Stream<Pokemon> streamAll();
    List<Pokemon> listAll();

    // Dynamic (criteria API) projection list and stream
    Stream<Pokemon> stream(Sort sort);
    List<Pokemon> list(Sort sort);
    Stream<Pokemon> streamAll(Sort sort);
    List<Pokemon> listAll(Sort sort);

    // Simple (JPQL) projection exists projection
    boolean existsByName(String name);
    Boolean boxedExistsByName(String name);

    // Dynamic (criteria API) projection exists projection
    boolean existsByName(String name, Sort sort);
    Boolean boxedExistsByName(String name, Sort sort);

    // Simple (JPQL) projection count
    Number countByName(String name);
    long longCountByName(String name);
    Long boxedLongCountByName(String name);
    int intCountByName(String name);
    Integer boxedIntCountByName(String name);
    short shortCountByName(String name);
    Short boxedShortCountByName(String name);
    byte byteCountByName(String name);
    Byte boxedByteCountByName(String name);
    float floatCountByName(String name);
    Float boxedFloatCountByName(String name);
    double doubleCountByName(String name);
    Double boxedDoubleCountByName(String name);
    BigInteger bigIntegerCountByName(String name);
    BigDecimal bigDecimalCountByName(String name);

    // Dynamic (criteria API) projection count
    Number countByName(String name, Sort sort);
    long longCountByName(String name, Sort sort);
    Long boxedLongCountByName(String name, Sort sort);
    int intCountByName(String name, Sort sort);
    Integer boxedIntCountByName(String name, Sort sort);
    short shortCountByName(String name, Sort sort);
    Short boxedShortCountByName(String name, Sort sort);
    byte byteCountByName(String name, Sort sort);
    Byte boxedByteCountByName(String name, Sort sort);
    float floatCountByName(String name, Sort sort);
    Float boxedFloatCountByName(String name, Sort sort);
    double doubleCountByName(String name, Sort sort);
    Double boxedDoubleCountByName(String name, Sort sort);
    BigInteger bigIntegerCountByName(String name, Sort sort);
    BigDecimal bigDecimalCountByName(String name, Sort sort);

    // Simple (JPQL) projection max/min/avg/sum
    long longGetMaxHpByIdLessThan(int id);
    Long longGetMinHpByIdLessThan(int id);
    int intGetSumHpByIdLessThan(int id);
    Integer intGetMaxHpByIdLessThan(int id);
    short shortGetMinHpByIdLessThan(int id);
    Short shortGetMaxHpByIdLessThan(int id);
    short shortGetSumHpByIdLessThan(int id);
    byte byteGetMinHpByIdLessThan(int id);
    Byte boxedByteGetMinHpByIdLessThan(int id);
    float floatGetMaxHpByIdLessThan(int id);
    Float floatGetMinHpByIdLessThan(int id);
    float floatGetAvgHpByIdLessThan(int id);
    Float floatGetSumHpByIdLessThan(int id);
    double doubleGetMinHpByIdLessThan(int id);
    Double doubleGetMaxHpByIdLessThan(int id);
    double doubleGetAvgHpByIdLessThan(int id);
    Double doubleGetSumHpByIdLessThan(int id);
    BigInteger bigIntGetMaxHpByIdLessThan(int id);
    BigDecimal bigDecGetMinHpByIdLessThan(int id);

    // Dynamic (criteria API) projection max/min/avg/sum
    long longGetMaxHpByIdLessThan(int id, Sort sort);
    Long longGetMinHpByIdLessThan(int id, Sort sort);
    int intGetSumHpByIdLessThan(int id, Sort sort);
    Integer intGetMaxHpByIdLessThan(int id, Sort sort);
    short shortGetMinHpByIdLessThan(int id, Sort sort);
    Short shortGetMaxHpByIdLessThan(int id, Sort sort);
    short shortGetSumHpByIdLessThan(int id, Sort sort);
    byte byteGetMinHpByIdLessThan(int id, Sort sort);
    Byte boxedByteGetMinHpByIdLessThan(int id, Sort sort);
    float floatGetMaxHpByIdLessThan(int id, Sort sort);
    Float floatGetMinHpByIdLessThan(int id, Sort sort);
    float floatGetAvgHpByIdLessThan(int id, Sort sort);
    Float floatGetSumHpByIdLessThan(int id, Sort sort);
    double doubleGetMinHpByIdLessThan(int id, Sort sort);
    Double doubleGetMaxHpByIdLessThan(int id, Sort sort);
    double doubleGetAvgHpByIdLessThan(int id, Sort sort);
    Double doubleGetSumHpByIdLessThan(int id, Sort sort);
    BigInteger bigIntGetMaxHpByIdLessThan(int id, Sort sort);
    BigDecimal bigDecGetMinHpByIdLessThan(int id, Sort sort);

    // Simple (JPQL) criteria After
    List<Pokemon> findByNameAfter(String name);
    List<Pokemon> findByIdAfter(int id);
    List<Pokemon> findByNameNotAfter(String pattern);
    List<Pokemon> findByIdNotAfter(int id);
    //TODO: IgnoreCase for String

    // Dynamic (criteria API) criteria After
    List<Pokemon> findByNameAfter(String name, Sort sort);
    List<Pokemon> findByIdAfter(int id, Sort sort);
    List<Pokemon> findByNameNotAfter(String pattern, Sort sort);
    List<Pokemon> findByIdNotAfter(int id, Sort sort);
    //TODO: IgnoreCase for String

    // Simple (JPQL) criteria Before
    List<Pokemon> findByNameBefore(String name);
    List<Pokemon> findByIdBefore(int id);
    List<Pokemon> findByNameNotBefore(String pattern);
    List<Pokemon> findByIdNotBefore(int id);
    //TODO: IgnoreCase for String

    // Dynamic (criteria API) criteria Before
    List<Pokemon> findByNameBefore(String name, Sort sort);
    List<Pokemon> findByIdBefore(int id, Sort sort);
    List<Pokemon> findByNameNotBefore(String pattern, Sort sort);
    List<Pokemon> findByIdNotBefore(int id, Sort sort);
    //TODO: IgnoreCase for String

    // Simple (JPQL) criteria Contains, String only
    List<Pokemon> findByNameContains(String pattern);
    List<Pokemon> findByNameNotContains(String pattern);
    List<Pokemon> findByNameIgnoreCaseContains(String pattern);
    List<Pokemon> findByNameIgnoreCaseNotContains(String pattern);

    // Dynamic (criteria API) criteria Contains, String only
    List<Pokemon> findByNameContains(String pattern, Sort sort);
    List<Pokemon> findByNameNotContains(String pattern, Sort sort);
    List<Pokemon> findByNameIgnoreCaseContains(String pattern, Sort sort);
    List<Pokemon> findByNameIgnoreCaseNotContains(String pattern, Sort sort);

    // Simple (JPQL) criteria EndsWith, String only
    List<Pokemon> findByNameEndsWith(String pattern);
    List<Pokemon> findByNameNotEndsWith(String pattern);
    List<Pokemon> findByNameIgnoreCaseEndsWith(String pattern);
    List<Pokemon> findByNameIgnoreCaseNotEndsWith(String pattern);

    // Dynamic (criteria API) criteria EndsWith, String only
    List<Pokemon> findByNameEndsWith(String pattern, Sort sort);
    List<Pokemon> findByNameNotEndsWith(String pattern, Sort sort);
    List<Pokemon> findByNameIgnoreCaseEndsWith(String pattern, Sort sort);
    List<Pokemon> findByNameIgnoreCaseNotEndsWith(String pattern, Sort sort);

    // Simple (JPQL) criteria StartsWith, String only
    List<Pokemon> findByNameStartsWith(String pattern);
    List<Pokemon> findByNameNotStartsWith(String pattern);
    List<Pokemon> findByNameIgnoreCaseStartsWith(String pattern);
    List<Pokemon> findByNameIgnoreCaseNotStartsWith(String pattern);

    // Dynamic (criteria API) criteria StartsWith, String only
    List<Pokemon> findByNameStartsWith(String pattern, Sort sort);
    List<Pokemon> findByNameNotStartsWith(String pattern, Sort sort);
    List<Pokemon> findByNameIgnoreCaseStartsWith(String pattern, Sort sort);
    List<Pokemon> findByNameIgnoreCaseNotStartsWith(String pattern, Sort sort);

    // Simple (JPQL) criteria LessThan, numbers
    List<Pokemon> findByHpLessThan(int hp);
    List<Pokemon> findByHpNotLessThan(int hp);

    // Dynamic (criteria API) criteria LessThan, numbers
    List<Pokemon> findByHpLessThan(int hp, Sort sort);
    List<Pokemon> findByHpNotLessThan(int hp, Sort sort);

    // Simple (JPQL) criteria LessThanEqual, numbers
    List<Pokemon> findByHpLessThanEqual(int hp);
    List<Pokemon> findByHpNotLessThanEqual(int hp);

    // Dynamic (criteria API) criteria LessThanEqual, numbers
    List<Pokemon> findByHpLessThanEqual(int hp, Sort sort);
    List<Pokemon> findByHpNotLessThanEqual(int hp, Sort sort);

    // Simple (JPQL) criteria GreaterThan, numbers
    List<Pokemon> findByHpGreaterThan(int hp);
    List<Pokemon> findByHpNotGreaterThan(int hp);

    // Dynamic (criteria API) criteria GreaterThan, numbers
    List<Pokemon> findByHpGreaterThan(int hp, Sort sort);
    List<Pokemon> findByHpNotGreaterThan(int hp, Sort sort);

    // Simple (JPQL) criteria GreaterThanEqual, numbers
    List<Pokemon> findByHpGreaterThanEqual(int hp);
    List<Pokemon> findByHpNotGreaterThanEqual(int hp);

    // Dynamic (criteria API) criteria GreaterThanEqual, numbers
    List<Pokemon> findByHpGreaterThanEqual(int hp, Sort sort);
    List<Pokemon> findByHpNotGreaterThanEqual(int hp, Sort sort);

    // Simple (JPQL) criteria Between, numbers
    List<Pokemon> findByHpBetween(int min, int max);
    List<Pokemon> findByHpNotBetween(int min, int max);

    // Dynamic (criteria API) criteria Between, numbers
    List<Pokemon> findByHpBetween(int min, int max, Sort sort);
    List<Pokemon> findByHpNotBetween(int min, int max, Sort sort);

    // Simple (JPQL) criteria Like, String only
    List<Pokemon> findByNameLike(String pattern);
    List<Pokemon> findByNameNotLike(String pattern);
    List<Pokemon> findByNameIgnoreCaseLike(String pattern);
    List<Pokemon> findByNameIgnoreCaseNotLike(String pattern);

    // Dynamic (criteria API) criteria Like, String only
    List<Pokemon> findByNameLike(String pattern, Sort sort);
    List<Pokemon> findByNameNotLike(String pattern, Sort sort);
    List<Pokemon> findByNameIgnoreCaseLike(String pattern, Sort sort);
    List<Pokemon> findByNameIgnoreCaseNotLike(String pattern, Sort sort);

    // Simple (JPQL) criteria In, String
    // IgnoreCase is not supported in combination with Collection argument of IN
    List<Pokemon> findByNameIn(List<String> names);
    List<Pokemon> findByNameNotIn(List<String> names);

    // Simple (JPQL) criteria In, Integer
    List<Pokemon> findByHpIn(List<Integer> names);
    List<Pokemon> findByHpNotIn(List<Integer> names);

    // Dynamic (criteria API) criteria In, String
    // IgnoreCase is not supported in combination with Collection argument of IN
    List<Pokemon> findByNameIn(List<String> names, Sort sort);
    List<Pokemon> findByNameNotIn(List<String> names, Sort sort);

    // Dynamic (criteria API) criteria In, Integer
    List<Pokemon> findByHpIn(List<Integer> names, Sort sort);
    List<Pokemon> findByHpNotIn(List<Integer> names, Sort sort);

    // Simple (JPQL) criteria Empty
    List<Pokemon> findByTypesEmpty();
    List<Pokemon> findByTypesNotEmpty();

    // Dynamic (criteria API) criteria Empty
    List<Pokemon> findByTypesEmpty(Sort sort);
    List<Pokemon> findByTypesNotEmpty(Sort sort);

    // Simple (JPQL) criteria Null
    List<Pokemon> findByTrainerNull();
    List<Pokemon> findByTrainerNotNull();

    // Dynamic (criteria API) criteria Null
    List<Pokemon> findByTrainerNull(Sort sort);
    List<Pokemon> findByTrainerNotNull(Sort sort);

    // Simple (JPQL) criteria True
    List<Pokemon> findByAliveTrue();
    List<Pokemon> findByAliveNotTrue();

    // Dynamic (criteria API) criteria True
    List<Pokemon> findByAliveTrue(Sort sort);
    List<Pokemon> findByAliveNotTrue(Sort sort);

    // Simple (JPQL) criteria False
    List<Pokemon> findByAliveFalse();
    List<Pokemon> findByAliveNotFalse();

    // Dynamic (criteria API) criteria False
    List<Pokemon> findByAliveFalse(Sort sort);
    List<Pokemon> findByAliveNotFalse(Sort sort);

    // DML simple delete (JPQL)
    void deleteByName(String name);
    Void boxedVoidDeleteByName(String name);
    boolean booleanDeleteByName(String name);
    Boolean boxedBooleanDeleteByName(String name);
    long longDeleteByName(String name);
    Long boxedLongDeleteByName(String name);
    int intDeleteByName(String name);
    Integer boxedIntDeleteByName(String name);
    short shortDeleteByName(String name);
    Short boxedShortDeleteByName(String name);
    byte byteDeleteByName(String name);
    Byte boxedByteDeleteByName(String name);

    // DML dynamic delete (criteria API)
    // Sort argument in DML methods causes an exception in codegen so tests are not possible

    // Simple (JPQL) criteria AND/OR
    Optional<Pokemon> getByNameAndHp(String name, int hp);
    List<Pokemon> findByNameOrHp(String name, int hp);
    List<Pokemon> findByNameOrHpOrId(String name, int hp, int id);
    List<Pokemon> findByNameAndHpOrId(String name, int hp, int id);
    List<Pokemon> findByIdOrHpAndName(int id, int hp, String name);

    // Dynamic (criteria API) criteria AND/OR
    Optional<Pokemon> getByNameAndHp(String name, int hp, Sort sort);
    List<Pokemon> findByNameOrHp(String name, int hp, Sort sort);
    List<Pokemon> findByNameOrHpOrId(String name, int hp, int id, Sort sort);
    List<Pokemon> findByNameAndHpOrId(String name, int hp, int id, Sort sort);
    List<Pokemon> findByIdOrHpAndName(int id, int hp, String name, Sort sort);

    // Static ordering (JPQL)
    List<Pokemon> findAllOrderByHpAscName();
    // Dynamic ordering (criteria API)
    List<Pokemon> findAllOrderByHp(Sort sort);

    // Single Pokemon bv annotation
    @Data.Query("SELECT p FROM Pokemon p WHERE p.name = :name")
    Pokemon selectByName(String name);

    // Optional<Pokemon> by annotation
    @Data.Query("SELECT p FROM Pokemon p WHERE p.name = :name")
    Optional<Pokemon> optionalSelectByName(String name);

    // List<Pokemon> by annotation
    @Data.Query("SELECT p FROM Pokemon p")
    List<Pokemon> selectAll();

    // Collection<Pokemon> by annotation
    @Data.Query("SELECT p FROM Pokemon p")
    Collection<Pokemon> collectionSelectAll();

    // Stream<Pokemon> by annotation
    @Data.Query("SELECT p FROM Pokemon p")
    Stream<Pokemon> streamSelectAll();

    // TODO:
    List<Pokemon> listFirst10ByHp(int hp);
    List<Pokemon> listFirst15DistinctByHp(int hp);

}
