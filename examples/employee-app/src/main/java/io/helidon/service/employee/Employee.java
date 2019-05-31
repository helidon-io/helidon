/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.service.employee;

import java.util.UUID;

import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;

public final class Employee {

    private final String id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;
    private final String birthDate;
    private final String title;
    private final String department;


    public Employee(String id, String firstName, String lastName, 
        String email, String phone, String birthDate,
        String title, String department) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.birthDate = birthDate;
        this.title = title;
        this.department = department;
    }

    @JsonbCreator
    public static Employee of(
    		@JsonbProperty("firstName") String firstName,
            @JsonbProperty("lastName") String lastName,
            @JsonbProperty("email") String email,
            @JsonbProperty("phone") String phone,
            @JsonbProperty("birthDate") String birthDate,
            @JsonbProperty("title") String title,
            @JsonbProperty("department") String department) {
            Employee e = new Employee(UUID.randomUUID().toString(), firstName, 
                lastName, email, phone, birthDate, title, department);
        return e;
    }

    public String getId() {
        return this.id;
    }

    public String getFirstName() {
        return this.firstName;
    }

    public String getLastName() {
        return this.lastName;
    }

    public String getEmail() {
        return this.email;
    }

    public String getPhone() {
        return this.phone;
    }

    public String getBirthDate() {
        return this.birthDate;
    }

    public String getTitle() {
        return this.title;
    }

    public String getDepartment() {
        return this.department;
    }    
   

	@Override
    public String toString() {
        return "ID: " + id + " First Name: " + firstName + " Last Name: " + lastName + " EMail: " + email + " Phone: "
            + phone + " Birth Date: " + birthDate + " Title: " + title + " Department: " + department;
    }

}
