/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server.test.queries;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.annotation.JsonbNumberFormat;
import javax.json.bind.annotation.JsonbProperty;

import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithNumberFormats;
import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithNameAnnotation;
import io.helidon.microprofile.graphql.server.test.types.ContactRelationship;
import io.helidon.microprofile.graphql.server.test.types.Person;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.microprofile.graphql.DateFormat;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NumberFormat;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds simple query definitions with various numbers of arguments.
 */
@GraphQLApi
@ApplicationScoped
public class SimpleQueriesWithArgs {

    public SimpleQueriesWithArgs() {
    }

    @Inject
    private TestDB testDB;

    // tests for ID
    @Query
    public Integer returnIntegerAsId(@Name("param1") @Id Integer value) {
        return value;
    }

    @Query
    public int returnIntPrimitiveAsId(@Name("param1") @Id int value) {
        return value;
    }

    @Query
    public int returnIntPrimitiveAsIdWithFormat(@JsonbNumberFormat("0 'hello'") @Name("param1") @Id int value) {
        return value;
    }

    @Query
    public Integer returnIntegerAsIdWithFormat(@NumberFormat("0 'format'") @Name("param1") @Id Integer value) {
        return value;
    }

    @Query
    public Integer returnIntAsId(@Name("param1") @Id int value) {
        return value;
    }

    @Query
    public String returnStringAsId(@Name("param1") @Id String value) {
        return value;
    }

    @Query
    public Long returnLongAsId(@Name("param1") @Id Long value) {
        return value;
    }

    @Query
    public Long returnLongAsIdWithFormat(@NumberFormat("#######-Long") @Name("param1") @Id Long value) {
        return value;
    }

    @Query
    public long returnLongPrimitiveAsId(@Name("param1") @Id long value) {
        return value;
    }

    @Query
    public long returnLongPrimitiveAsIdWithFormat(@NumberFormat("#######-long") @Name("param1") @Id long value) {
        return value;
    }

    @Query
    public UUID returnUUIDAsId(@Name("param1") @Id UUID value) {
        return value;
    }

    @Query
    public String echoString(@Name("String") String value) {
        return value;
    }

    @Query
    public int echoInt(@Name("value") int value) {
        return value;
    }

    @Query
    public Integer echoIntegerObject(@Name("value") Integer value) {
        return value;
    }

    @Query
    public int echoIntWithFormat(@NumberFormat("0 'value'") @Name("value") int value) {
        return value;
    }

    @Query
    public Integer echoIntegerObjectWithFormat(@NumberFormat("0 'value'") @Name("value") Integer value) {
        return value;
    }

    @Query
    public double echoDouble(@Name("value") double value) {
        return value;
    }

    @Query
    public double echoDoubleWithFormat(@NumberFormat("#####-format") @Name("value") double value) {
        return value;
    }

    @Query
    public Double echoDoubleObjectWithFormat(@NumberFormat("#####-format") @Name("value") Double value) {
        return value;
    }

    @Query
    public Double echoDoubleObject(@Name("value") Double value) {
        return value;
    }

    @Query
    public float echoFloat(@Name("value") float value) {
        return value;
    }

    @Query
    public float echoFloatWithFormat(@JsonbNumberFormat(value = "¤ 000.00", locale = "en-AU")
                                     @Name("value") float value) {
        return value;
    }

    @Query
    public Float echoFloatObjectWithFormat(@JsonbNumberFormat(value = "¤ 000.00", locale = "en-AU")
                                     @Name("value") Float value) {
        return value;
    }

    @Query
    public Float echoFloatObject(@Name("value") Float value) {
        return value;
    }

    @Query
    public byte echoByte(@Name("value") byte value) {
        return value;
    }

    @Query
    public Byte echoByteObject(@Name("value") Byte value) {
        return value;
    }

    @Query
    public long echoLong(@Name("value") long value) {
        return value;
    }

    @Query
    public long echoLongWithFormat(@NumberFormat("Long-##########") @Name("value") long value) {
        return value;
    }

    @Query
    public Long echoLongObjectWithFormat(@NumberFormat("Long-##########") @Name("value") Long value) {
        return value;
    }

    @Query
    public Long echoLongObject(@Name("value") Long value) {
        return value;
    }

    @Query
    public boolean echoBoolean(@Name("value") boolean value) {
        return value;
    }

    @Query
    public Boolean echoBooleanObject(@Name("value") Boolean value) {
        return value;
    }

    @Query
    public char echoChar(@Name("value") char value) {
        return value;
    }

    @Query
    public Character echoCharacterObject(@Name("value") Character value) {
        return value;
    }

    @Query
    public BigDecimal echoBigDecimal(@Name("value") BigDecimal value) {
        return value;
    }

    @Query
    public BigDecimal echoBigDecimalWithFormat(@NumberFormat("######.##-BigDecimal") @Name("value") BigDecimal value) {
        return value;
    }

    @Query
    public BigInteger echoBigInteger(@Name("value") BigInteger value) {
        return value;
    }

    @Query
    public BigInteger echoBigIntegerWithFormat(@NumberFormat("######-BigInteger") @Name("value") BigInteger value) {
        return value;
    }

    @Query
    public String hero(@Name("heroType") String heroType) {
        return "human".equalsIgnoreCase(heroType)
                ? "Luke"
                : "R2-D2";
    }

    @Query("multiply")
    public long multiplyFunction(int number1, int number2) {
        return number1 * number2;
    }

    @Query
    @Name("findAPerson")
    public Person findPerson(@Name("personId") int personId) {
        return testDB.getPerson(personId);
    }

    @Query
    public Collection<Person> findPeopleFromState(@Name("state") String state) {
        return testDB.getAllPeople()
                .stream().filter(p -> p.getHomeAddress().getState().equals(state))
                .collect(Collectors.toList());
    }

    @Query
    public List<LocalDate> findLocalDates(@Name("numberOfValues") int count) {
        List<LocalDate> localDates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            localDates.add(LocalDate.now());
        }
        return localDates;
    }

    @Query("getMonthFromDate")
    public String returnDateAsLong(@Name("date") LocalDate localDate) {
        return localDate.getMonth().toString();
    }

    @Query
    public Collection<EnumTestWithNameAnnotation> findOneEnum(@Name("enum") EnumTestWithNameAnnotation enum1) {
        return Collections.singleton(enum1);
    }

    @Query
    public boolean canFindContact(@Name("contact") SimpleContact contact) {
        return false;
    }

    @Query
    @JsonbProperty("findEnums")
    public String findEnumValue(EnumTestWithNameAnnotation enum1) {
        return enum1.name();
    }

    @Query("canFindContactRelationship")
    public boolean getContactRelationships(@Name("relationship") ContactRelationship relationship) {
        return false;
    }

    @Query("additionQuery")
    public int addNumbers(@Name("value1") int value1, @Name("value2") int value2) {
        return value1 + value2;
    }

    @Query
    public boolean canFindSimpleContactWithNumberFormats(@Name("contact") SimpleContactWithNumberFormats contact) {
        return false;
    }

    @Query
    public List<String> echoListOfStrings(@Name("value") List<String> value) {
        return value;
    }

    @Query
    public List<Integer> echoListOfIntegers(@Name("value") List<Integer> value) {
        return value;
    }

    @Query
    public List<BigInteger> echoListOfBigIntegers(@Name("value") List<BigInteger> value) {
        return value;
    }

    @Query
    public List<SimpleContact> echoListOfSimpleContacts(@Name("value") List<SimpleContact> value) {
        return value;
    }

    @Query
    public Collection<SimpleContact> echoCollectionOfSimpleContacts(@Name("value") Collection<SimpleContact> value) {
        return value;
    }

    @Query
    public String[] echoStringArray(@Name("value") String[] value) {
        return value;
    }

    @Query
    public int[] echoIntArray(@Name("value") int[] value) {
        return value;
    }

    @Query
    public Boolean[] echoBooleanArray(@Name("value") Boolean[] value) {
        return value;
    }

    @Query
    public String[][] echoStringArray2(@Name("value") String[][] value) {
        return value;
    }

    @Query
    public SimpleContact[] echoSimpleContactArray(@Name("value") SimpleContact[] value) {
        return value;
    }

    @Query
    public List<Integer> echoFormattedListOfIntegers(@Name("value") List<@NumberFormat("0 'years old'") Integer> value) {
        return value;
    }

    @Query
    public List<LocalDate> echoFormattedLocalDate(@Name("value") List<@DateFormat("dd-MM-yyyy") LocalDate> value) {
        return value;
    }
}
