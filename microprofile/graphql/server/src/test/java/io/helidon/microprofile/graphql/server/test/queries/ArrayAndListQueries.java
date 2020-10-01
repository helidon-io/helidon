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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.types.MultiLevelListsAndArrays;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NumberFormat;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds array, {@link java.util.Collection} and {@link java.util.Map} queries.
 */
@GraphQLApi
@ApplicationScoped
public class ArrayAndListQueries {

    @Inject
    private TestDB testDB;

    public ArrayAndListQueries() {
    }

    @Query
    @Name("getMultiLevelList")
    public MultiLevelListsAndArrays returnLists() {
        return testDB.getMultiLevelListsAndArrays();
    }

    @Query("returnListOfStringArrays")
    public Collection<String[]> getListOfStringArrays() {
        ArrayList<String[]> arrayList = new ArrayList<>();
        arrayList.add(new String[] { "one", "two"});
        arrayList.add(new String[] { "three", "four", "five"});
        return arrayList;
    }

    @Query
    public List<BigDecimal> echoLinkedListBigDecimals(@Name("param") LinkedList<BigDecimal> value) {
        return value;
    }

    @Query
    public List<BigDecimal> echoListBigDecimal(@Name("param") List<BigDecimal> value) {
        return value;
    }

    @Query
    public BigDecimal[] echoBigDecimalArray(@Name("param") BigDecimal[] value) {
        return value;
    }

    @Query
    public int[] echoIntArray(@Name("param") int[] value) {
        return value;
    }

    @Query
    public short[] echoShortArray(@Name("param") short[] value) {
        return value;
    }

    @Query
    public long[] echoLongArray(@Name("param") long[] value) {
        return value;
    }

    @Query
    public double[] echoDoubleArray(@Name("param") double[] value) {
        return value;
    }

    @Query
    public boolean[] echoBooleanArray(@Name("param") boolean[] value) {
        return value;
    }

    @Query
    public char[] echoCharArray(@Name("param") char[] value) {
        return value;
    }

    @Query
    public float[] echoFloatArray(@Name("param") float[] value) {
        return value;
    }

    @Query
    public byte[] echoByteArray(@Name("param") byte[] value) {
        return value;
    }

    @Query
    public SimpleContact[] echoSimpleContactArray(@Name("param") SimpleContact[] value) {
        return value;
    }

    @Query
    public String processListListBigDecimal(@Name("param") List<List<
            @NumberFormat(value = "00.0000000 longlat", locale = "en-GB") BigDecimal>> value) {
        return value.toString();
    }
}
