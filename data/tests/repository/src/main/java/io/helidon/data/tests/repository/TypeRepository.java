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
package io.helidon.data.tests.repository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.data.Data;
import io.helidon.data.Page;
import io.helidon.data.PageRequest;
import io.helidon.data.Slice;
import io.helidon.data.Sort;
import io.helidon.data.tests.model.Type;

@Data.Repository
public interface TypeRepository extends Data.PageableRepository<Type, Integer> {

    long deleteAll();

    Stream<Type> findAll();

    Optional<Type> findById(int id);

    long count();

    List<Type> list(Sort sort);

    long delete();

    Slice<Type> listByNameNotStartsWith(PageRequest request, String prefix);

    Page<Type> listByNameEndsWithOrIdBetween(String prefix, PageRequest request, int min, int max);

    List<Type> listByName(String name);

    List<Type> listIdByName(String name, Sort sort);

    List<Type> sortedListByName(Sort sort, String name);

    List<Type> sortedListByNameIgnoreCase(Sort sort, String name);

    List<Type> sortedListByNameNot(Sort sort, String name);

    List<Type> sortedListByNameNotIgnoreCase(Sort sort, String name);

    Stream<Type> streamByNameContains(String name);

    Stream<Type> sortedStreamByNameContains(Sort sort, String name);

    Stream<Type> sortedStreamByNameIgnoreCaseContains(Sort sort, String name);

    Stream<Type> sortedStreamByNameNotContains(Sort sort, String name);

    Stream<Type> sortedStreamByNameNotIgnoreCaseContains(Sort sort, String name);

    Stream<Type> streamByNameEndsWith(String name);

    Stream<Type> sortedStreamByNameEndsWith(Sort sort, String name);

    Stream<Type> sortedStreamByNameIgnoreCaseEndsWith(Sort sort, String name);

    Stream<Type> sortedStreamByNameNotEndsWith(Sort sort, String name);

    Stream<Type> sortedStreamByNameNotIgnoreCaseEndsWith(Sort sort, String name);

    Stream<Type> streamByNameStartsWith(String name);

    Stream<Type> sortedStreamByNameStartsWith(Sort sort, String name);

    Stream<Type> sortedStreamByNameIgnoreCaseStartsWith(Sort sort, String name);

    Stream<Type> sortedStreamByNameNotStartsWith(Sort sort, String name);

    Stream<Type> sortedStreamByNameNotIgnoreCaseStartsWith(Sort sort, String name);

    Stream<Type> streamByIdLessThan(int id);

    Stream<Type> sortedStreamByIdLessThan(Sort sort, int id);

    // IgnoreCase can't be used with non String argument
    //Stream<Kind> sortedListByIdIgnoreCaseLessThan(Sort sort, int id);
    Stream<Type> sortedStreamByIdNotLessThan(Sort sort, int id);
    // IgnoreCase can't be used with non String argument
    //Stream<Kind> sortedListByIdNotIgnoreCaseLessThan(Sort sort, int id);

    //Stream<Kind> sortedListNameByNameOrNameAndIdOrId(Sort sort, String name1, String name2, int id1, int id2);

    Collection<Type> listByIdLessThanEqual(int id);

    Collection<Type> sortedListByIdLessThanEqual(Sort sort, int id);

    Collection<Type> sortedListByIdNotLessThanEqual(Sort sort, int id);

    Collection<Type> listByIdGreaterThan(int id);

    Collection<Type> sortedListByIdGreaterThan(Sort sort, int id);

    Collection<Type> sortedListByIdNotGreaterThan(Sort sort, int id);

    Collection<Type> listByIdGreaterThanEqual(int id);

    Collection<Type> sortedListByIdGreaterThanEqual(Sort sort, int id);

    Collection<Type> sortedListByIdNotGreaterThanEqual(Sort sort, int id);

    Collection<Type> listByIdBetween(int min, int max);

    Collection<Type> sortedListByIdBetween(Sort sort, int min, int max);

    Collection<Type> sortedListByIdNotBetween(Sort sort, int min, int max);

    Stream<Type> streamByNameLike(String name);

    Stream<Type> sortedStreamByNameLike(Sort sort, String name);

    Stream<Type> sortedStreamByNameIgnoreCaseLike(Sort sort, String name);

    Stream<Type> sortedStreamByNameNotLike(Sort sort, String name);

    Stream<Type> sortedStreamByNameNotIgnoreCaseLike(Sort sort, String name);

    Stream<Type> streamByNameIn(List<String> name);

    Stream<Type> streamByNameIgnoreCaseIn(List<String> name);

    Stream<Type> sortedStreamByNameIn(Sort sort, List<String> name);

    //Stream<Kind> sortedListByNameIgnoreCaseIn(Sort sort, List<String> name);
    Stream<Type> sortedStreamByNameNotIn(Sort sort, List<String> name);
    //Stream<Kind> sortedListByNameIgnoreCaseNotIn(Sort sort, List<String> name);

    Stream<Type> streamByNameEmpty(String name);

    Stream<Type> sortedStreamByNameEmpty(Sort sort, String name);

    Stream<Type> sortedStreamByNameNotEmpty(Sort sort, String name);

    Stream<Type> streamByNameNull(String name);

    Stream<Type> sortedStreamByNameNull(Sort sort, String name);

    Stream<Type> sortedStreamByNameNotNull(Sort sort, String name);

    Stream<Type> streamByNameTrue(String name);

    Stream<Type> sortedStreamByNameTrue(Sort sort, String name);

    Stream<Type> sortedStreamByNameNotTrue(Sort sort, String name);

    Stream<Type> streamByNameFalse(String name);

    Stream<Type> sortedStreamByNameFalse(Sort sort, String name);

    Stream<Type> sortedStreamByNameNotFalse(Sort sort, String name);

    List<Type> sortedListByNameOrderByNameDescId(String name);

    List<Type> sortedListByNameOrderByName(Sort sort, String name);

    Page<Type> pageListByNameOrderByName(PageRequest pageRequest, Sort sort, String name);

    Slice<Type> sliceListByNameOrderByName(PageRequest pageRequest, Sort sort, String name);

    Optional<String> findNameById(Sort sort, int id);

    String getNameByName(Sort sort, String name);

    long longGetMaxId(Sort sort, int id);

    Long longGetMinId(Sort sort, int id);

    int intGetSumId(Sort sort, int id);

    Integer intGetMaxId(Sort sort, int id);

    short shortGetMinId(Sort sort, int id);

    Short shortGetMaxId(Sort sort, int id);

    byte byteGetSumId(Sort sort, int id);

    Byte byteGetMinId(Sort sort, int id);

    float floatGetMaxId(Sort sort, int id);

    Float floatGetMinId(Sort sort, int id);

    double doubleGetAvgId(int id, Sort sort);

    Double doubleGetAvgId(Sort sort, int id);

    BigInteger bigIntGetMaxId(Sort sort, int id);

    BigDecimal bigDecGetMinId(Sort sort, int id);

    boolean getExistsIdByName(Sort sort, String name);

    boolean getExistsByName(Sort sort, String name);

    Boolean getExistsIdById(Sort sort, int id);

    @Data.Query("SELECT k FROM Kind k WHERE k.id > :minId")
    Slice<Type> selectKindByIdGreaterThan(PageRequest request, int minId);

}
