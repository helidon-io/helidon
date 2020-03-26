/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import io.helidon.config.Config;

/**
 * Interface for Data Access Objects.
 * <p>
 * As Helidon SE is a reactive framework, we cannot block it.
 * Method on this interface return a {@link java.util.concurrent.CompletionStage} with the data, so it
 * can be correctly handled by the server.
 * <p>
 * Methods in implementation must not block thread
 */
public interface EmployeeRepository {

    /**
     * Create a new employeeRepository instance using one of the two implementations
     * {@link EmployeeRepositoryImpl} or {@link EmployeeRepositoryImplDB} depending
     * on the specified driver type.
     * @param driverType Represents the driver type. It can be Array or Oracle.
     * @param config Contains the application configuration specified in the
     * <code>application.yaml</code> file.
     * @return The employee repository implementation.
     */
    static EmployeeRepository create(String driverType, Config config) {
        switch (driverType) {
        case "Database":
            return new EmployeeRepositoryImplDB(config);
        case "Array":
        default:
            // Array is default
            return new EmployeeRepositoryImpl();
        }
    }

    /**
     * Returns the list of the employees.
     * @return The collection of all the employee objects
     */
    CompletionStage<List<Employee>> getAll();

    /**
     * Returns the list of the employees that match with the specified lastName.
     * @param lastName Represents the last name value for the search.
     * @return The collection of the employee objects that match with the specified
     *         lastName
     */
    CompletionStage<List<Employee>> getByLastName(String lastName);

    /**
     * Returns the list of the employees that match with the specified title.
     * @param title Represents the title value for the search
     * @return The collection of the employee objects that match with the specified
     *         title
     */
    CompletionStage<List<Employee>> getByTitle(String title);

    /**
     * Returns the list of the employees that match with the specified department.
     * @param department Represents the department value for the search.
     * @return The collection of the employee objects that match with the specified
     *         department
     */
    CompletionStage<List<Employee>> getByDepartment(String department);

    /**
     * Add a new employee.
     * @param employee returns the employee object including the ID generated.
     * @return the employee object including the ID generated
     */
    CompletionStage<Employee> save(Employee employee); // Add new employee

    /**
     * Update an existing employee.
     * @param updatedEmployee The employee object with the values to update
     * @param id The employee ID
     * @return number of updated records
     */
    CompletionStage<Long> update(Employee updatedEmployee, String id);

    /**
     * Delete an employee by ID.
     * @param id The employee ID
     * @return number of deleted records
     */
    CompletionStage<Long> deleteById(String id);

    /**
     * Get an employee by ID.
     * @param id The employee ID
     * @return The employee object if the employee is found
     */
    CompletionStage<Optional<Employee>> getById(String id);
}
