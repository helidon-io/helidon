/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql.server.test.types;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.eclipse.microprofile.graphql.Type;

/**
 * POJO to test multiple levels of lists and arrays.
 */
@Type
public class MultiLevelListsAndArrays {

    private List<List<BigDecimal>> listOfListOfBigDecimal;
    private List<List<Person>> listOfListOfPerson;
    private List<List<Integer>> listOfListOfInteger;
    private List<String[]> listOfStringArrays;
    private Collection<Collection<Collection<String>>> colColColString;
    private int[][] intMultiLevelArray;
    private Person[][] personMultiLevelArray;
    private String[][][] multiStringArray;

    public MultiLevelListsAndArrays(List<List<BigDecimal>> listOfListOfBigDecimal,
                                    List<List<Person>> listOfListOfPerson,
                                    List<List<Integer>> listOfListOfInteger,
                                    int[][] intMultiLevelArray,
                                    Person[][] personMultiLevelArray,
                                    List<String[]> listOfStringArrays,
                                    String[][][] multiStringArray,
                                    Collection<Collection<Collection<String>>> colColColString) {
        this.listOfListOfBigDecimal = listOfListOfBigDecimal;
        this.listOfListOfPerson = listOfListOfPerson;
        this.listOfListOfInteger = listOfListOfInteger;
        this.intMultiLevelArray = intMultiLevelArray;
        this.personMultiLevelArray = personMultiLevelArray;
        this.listOfStringArrays = listOfStringArrays;
        this.multiStringArray = multiStringArray;
        this.colColColString = colColColString;
    }

    public List<List<BigDecimal>> getListOfListOfBigDecimal() {
        return listOfListOfBigDecimal;
    }

    public void setListOfListOfBigDecimal(List<List<BigDecimal>> listOfListOfBigDecimal) {
        this.listOfListOfBigDecimal = listOfListOfBigDecimal;
    }

    public List<List<Person>> getListOfListOfPerson() {
        return listOfListOfPerson;
    }

    public void setListOfListOfPerson(List<List<Person>> listOfListOfPerson) {
        this.listOfListOfPerson = listOfListOfPerson;
    }

    public List<List<Integer>> getListOfListOfInteger() {
        return listOfListOfInteger;
    }

    public void setListOfListOfInteger(List<List<Integer>> listOfListOfInteger) {
        this.listOfListOfInteger = listOfListOfInteger;
    }

    public int[][] getIntMultiLevelArray() {
        return intMultiLevelArray;
    }

    public void setIntMultiLevelArray(int[][] intMultiLevelArray) {
        this.intMultiLevelArray = intMultiLevelArray;
    }

    public Person[][] getPersonMultiLevelArray() {
        return personMultiLevelArray;
    }

    public void setPersonMultiLevelArray(Person[][] personMultiLevelArray) {
        this.personMultiLevelArray = personMultiLevelArray;
    }

    public List<String[]> getListOfStringArrays() {
        return listOfStringArrays;
    }

    public void setListOfStringArrays(List<String[]> listOfStringArrays) {
        this.listOfStringArrays = listOfStringArrays;
    }

    public String[][][] getMultiStringArray() {
        return multiStringArray;
    }

    public void setMultiStringArray(String[][][] multiStringArray) {
        this.multiStringArray = multiStringArray;
    }

    public Collection<Collection<Collection<String>>> getColColColString() {
        return colColColString;
    }

    public void setColColColString(Collection<Collection<Collection<String>>> colColColString) {
        this.colColColString = colColColString;
    }
}
