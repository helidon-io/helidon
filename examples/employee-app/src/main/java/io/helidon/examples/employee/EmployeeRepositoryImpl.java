/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.employee;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

/**
 * Implementation of the {@link EmployeeRepository}. This implementation uses a
 * mock database written with in-memory ArrayList classes.
 * The strings id, name, and other search strings are validated before being
 * passed to the methods in this class.
 */
public final class EmployeeRepositoryImpl implements EmployeeRepository {

    private final CopyOnWriteArrayList<Employee> eList = new CopyOnWriteArrayList<>();

    /**
     * To load the initial data, parses the content of <code>employee.json</code>
     * file located in the <code>resources</code> directory to a list of Employee
     * objects.
     */
    public EmployeeRepositoryImpl() {
        JsonbConfig config = new JsonbConfig().withFormatting(Boolean.TRUE);
        try (Jsonb jsonb = JsonbBuilder.create(config);
             InputStream jsonFile = EmployeeRepositoryImpl.class.getResourceAsStream("/employees.json")) {
            Employee[] employees = jsonb.fromJson(jsonFile, Employee[].class);
            eList.addAll(Arrays.asList(employees));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Employee> getByLastName(String name) {
        return eList.stream()
                .filter((e) -> (e.getLastName().contains(name)))
                .toList();
    }

    @Override
    public List<Employee> getByTitle(String title) {
        return eList.stream()
                .filter((e) -> (e.getTitle().contains(title)))
                .toList();
    }

    @Override
    public List<Employee> getByDepartment(String department) {
        return eList.stream()
                .filter((e) -> (e.getDepartment().contains(department)))
                .toList();
    }

    @Override
    public List<Employee> getAll() {
        return eList;
    }

    @Override
    public Optional<Employee> getById(String id) {
        return eList.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    @Override
    public Employee save(Employee employee) {
        Employee nextEmployee = Employee.of(null,
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getBirthDate(),
                employee.getTitle(),
                employee.getDepartment());
        eList.add(nextEmployee);
        return nextEmployee;
    }

    @Override
    public long update(Employee updatedEmployee, String id) {
        deleteById(id);
        Employee e = Employee.of(id, updatedEmployee.getFirstName(), updatedEmployee.getLastName(),
                updatedEmployee.getEmail(), updatedEmployee.getPhone(), updatedEmployee.getBirthDate(),
                updatedEmployee.getTitle(), updatedEmployee.getDepartment());
        eList.add(e);
        return 1L;
    }

    @Override
    public long deleteById(String id) {
        return eList.stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .map(eList::remove)
                .map(it -> 1L)
                .orElse(0L);
    }
}
