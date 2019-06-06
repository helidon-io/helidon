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
/**
 * Represents an employee.
 */
public final class Employee {

    private final String id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;
    private final String birthDate;
    private final String title;
    private final String department;

    /**Creates a new Employee.
     * @param id The employee ID.
     * @param firstName The employee first name.
     * @param lastName The employee lastName.
     * @param email The employee email.
     * @param phone The employee phone.
     * @param birthDate The employee birthDatee.
     * @param title The employee title.
     * @param department The employee department.*/
    @SuppressWarnings("checkstyle:ParameterNumber")
    private Employee(String id, String firstName, String lastName, String email, String phone, String birthDate,
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

    /**
     * Creates a new employee. This method helps to parse the json parameters in the requests.
     * @param id The employee ID. If the employee ID is null or empty generates a new ID.
     * @param firstName The employee first name.
     * @param lastName The employee lastName.
     * @param email The employee email.
     * @param phone The employee phone.
     * @param birthDate The employee birthDatee.
     * @param title The employee title.
     * @param department The employee department.
     * @return A new employee object
     */
    @JsonbCreator
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static Employee of(@JsonbProperty("id") String id, @JsonbProperty("firstName") String firstName,
            @JsonbProperty("lastName") String lastName, @JsonbProperty("email") String email,
            @JsonbProperty("phone") String phone, @JsonbProperty("birthDate") String birthDate,
            @JsonbProperty("title") String title, @JsonbProperty("department") String department) {
        if (id == null || id.trim().equals("")) {
            id = UUID.randomUUID().toString();
        }
        Employee e = new Employee(id, firstName, lastName, email, phone, birthDate, title, department);
        return e;
    }

    /**
     * Returns the employee ID.
     * @return the ID
     */
    public String getId() {
        return this.id;
    }

    /**
     * Returns the employee first name.
     * @return The first name
     */
    public String getFirstName() {
        return this.firstName;
    }

    /**
     * Returns the employee last name.
     * @return The last name
     */
    public String getLastName() {
        return this.lastName;
    }

    /**
     * Returns the employee e-mail.
     * @return The email
     */
    public String getEmail() {
        return this.email;
    }

    /**
     * Returns the employee phone.
     * @return The phone
     */
    public String getPhone() {
        return this.phone;
    }

    /**
     * Returns the employee birthdate.
     * @return The birthdate
     */
    public String getBirthDate() {
        return this.birthDate;
    }

    /**
     * Returns the employee title.
     * @return The title
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Returns the employee department.
     * @return The department
     */
    public String getDepartment() {
        return this.department;
    }

    @Override
    public String toString() {
        return "ID: " + id + " First Name: " + firstName + " Last Name: " + lastName + " EMail: " + email + " Phone: "
                + phone + " Birth Date: " + birthDate + " Title: " + title + " Department: " + department;
    }

}
