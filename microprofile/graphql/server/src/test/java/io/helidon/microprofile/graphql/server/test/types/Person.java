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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.graphql.Type;

/**
 * Class representing a Person to test various getters.
 */
@Type
public class Person {
    private int personId;
    private String name;
    private Address homeAddress;
    private Address workAddress;
    private BigDecimal creditLimit;
    private Collection<String> listQualifications;
    private List<Address> previousAddresses;
    private int[] intArray;
    private String[] stringArray;
    private Map<String, Address> addressMap;
    private LocalDate localDate;
    private long longValue;
    private BigDecimal bigDecimal;

    public Person(int personId,
                  String name,
                  Address homeAddress,
                  Address workAddress,
                  BigDecimal creditLimit,
                  Collection<String> listQualifications,
                  List<Address> previousAddresses,
                  int[] intArray,
                  String[] stringArray,
                  Map<String, Address> addressMap, LocalDate localDate, long longValue, BigDecimal bigDecimal) {
        this.personId = personId;
        this.name = name;
        this.homeAddress = homeAddress;
        this.workAddress = workAddress;
        this.creditLimit = creditLimit;
        this.listQualifications = listQualifications;
        this.previousAddresses = previousAddresses;
        this.intArray = intArray;
        this.stringArray = stringArray;
        this.addressMap = addressMap;
        this.localDate = localDate;
        this.longValue = longValue;
        this.bigDecimal = bigDecimal;
    }

    public long getLongValue() {
        return longValue;
    }

    public void setLongValue(long longValue) {
        this.longValue = longValue;
    }

    public int getPersonId() {
        return personId;
    }

    public void setPersonId(int personId) {
        this.personId = personId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Address getHomeAddress() {
        return homeAddress;
    }

    public void setHomeAddress(Address homeAddress) {
        this.homeAddress = homeAddress;
    }

    public Address getWorkAddress() {
        return workAddress;
    }

    public void setWorkAddress(Address workAddress) {
        this.workAddress = workAddress;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public Collection<String> getListQualifications() {
        return listQualifications;
    }

    public void setListQualifications(Collection<String> listQualifications) {
        this.listQualifications = listQualifications;
    }

    public List<Address> getPreviousAddresses() {
        return previousAddresses;
    }

    public void setPreviousAddresses(List<Address> previousAddresses) {
        this.previousAddresses = previousAddresses;
    }

    public int[] getIntArray() {
        return intArray;
    }

    public void setIntArray(int[] intArray) {
        this.intArray = intArray;
    }

    public String[] getStringArray() {
        return stringArray;
    }

    public void setStringArray(String[] stringArray) {
        this.stringArray = stringArray;
    }

    public Map<String, Address> getAddressMap() {
        return addressMap;
    }

    public void setAddressMap(Map<String, Address> addressMap) {
        this.addressMap = addressMap;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public void setLocalDate(LocalDate localDate) {
        this.localDate = localDate;
    }

    public BigDecimal getBigDecimal() {
        return bigDecimal;
    }

    public void setBigDecimal(BigDecimal bigDecimal) {
        this.bigDecimal = bigDecimal;
    }

    @Override
    public String toString() {
        return "Person{" +
                "personId=" + personId +
                ", name='" + name + '\'' +
                ", homeAddress=" + homeAddress +
                ", workAddress=" + workAddress +
                ", creditLimit=" + creditLimit +
                ", listQualifications=" + listQualifications +
                ", previousAddresses=" + previousAddresses +
                ", intArray=" + Arrays.toString(intArray) +
                ", stringArray=" + Arrays.toString(stringArray) +
                ", addressMap=" + addressMap +
                ", longValue=" + longValue +
                ", localDate=" + localDate +
                ", bigDecimal=" + bigDecimal +
                '}';
    }
}
