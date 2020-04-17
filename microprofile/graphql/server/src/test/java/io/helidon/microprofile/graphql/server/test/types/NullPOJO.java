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

package io.helidon.microprofile.graphql.server.test.types;

import java.util.List;

import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Type;

/**
 * POJO to test nulls.
 */
@Type
public class NullPOJO {

    // should be mandatory
    private int id;

    // should be optional for type
    private Long longValue;

    // should be mandatory
    @NonNull
    private String stringValue;

    private List<@NonNull String> listNonNullStrings;
    private List<List<@NonNull String>> listOfListOfNonNullStrings;

    private List<List<String>> listOfListOfNullStrings;

    // should be nullable for type and non nullable for input type
    private String nonNullForInput;

    private String testNullWithGet;

    public NullPOJO() {
    }

    public NullPOJO(int id,
                    Long longValue,
                    @NonNull String stringValue,
                    List<@NonNull String> listNonNullStrings) {
        this.id = id;
        this.longValue = longValue;
        this.stringValue = stringValue;
        this.listNonNullStrings = listNonNullStrings;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Long getLongValue() {
        return longValue;
    }

    public void setLongValue(Long longValue) {
        this.longValue = longValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }

    public List<@NonNull String> getListNonNullStrings() {
        return listNonNullStrings;
    }

    public void setListNonNullStrings(List<String> listNonNullStrings) {
        this.listNonNullStrings = listNonNullStrings;
    }

    public String getNonNullForInput() {
        return nonNullForInput;
    }

    // should be non null for input type
    @NonNull
    public void setNonNullForInput(String nonNullForInput) {
        this.nonNullForInput = nonNullForInput;
    }

    // should be mandatory for type and optional for input type
    @NonNull
    public String getTestNullWithGet() {
        return testNullWithGet;
    }

    public void setTestNullWithGet(String testNullWithGet) {
        this.testNullWithGet = testNullWithGet;
    }

    public List<List<@NonNull String>> getListOfListOfNonNullStrings() {
        return listOfListOfNonNullStrings;
    }

    public void setListOfListOfNonNullStrings(List<List<String>> listOfListOfNonNullStrings) {
        this.listOfListOfNonNullStrings = listOfListOfNonNullStrings;
    }

    public List<List<String>> getListOfListOfNullStrings() {
        return listOfListOfNullStrings;
    }

    public void setListOfListOfNullStrings(List<List<String>> listOfListOfNullStrings) {
        this.listOfListOfNullStrings = listOfListOfNullStrings;
    }

}
