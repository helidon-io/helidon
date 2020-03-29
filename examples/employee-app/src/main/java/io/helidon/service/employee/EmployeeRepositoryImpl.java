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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

/**
 * Implementation of the {@link EmployeeRepository}. This implementation uses a
 * mock database written with in-memory ArrayList classes.
 * The strings id, name, and other search strings are validated before being
 * passed to the methods in this class.
 *
 */
public final class EmployeeRepositoryImpl implements EmployeeRepository {

    private final CopyOnWriteArrayList<Employee> eList = new CopyOnWriteArrayList<Employee>();

    /**
     * To load the initial data, parses the content of <code>employee.json</code>
     * file located in the <code>resources</code> directory to a list of Employee
     * objects.
     */
    public EmployeeRepositoryImpl() {
        JsonbConfig config = new JsonbConfig().withFormatting(Boolean.TRUE);

        Jsonb jsonb = JsonbBuilder.create(config);
        try (InputStream jsonFile = EmployeeRepositoryImpl.class.getResourceAsStream("/employees.json")) {
            Employee[] employees = jsonb.fromJson(jsonFile, Employee[].class);
            eList.addAll(Arrays.asList(employees));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public List<Employee> getByLastName(String name) {
        List<Employee> matchList = eList.stream().filter((e) -> (e.getLastName().contains(name)))
                .collect(Collectors.toList());

        return matchList;
    }

    @Override
    public List<Employee> getByTitle(String title) {
        List<Employee> matchList = eList.stream().filter((e) -> (e.getTitle().contains(title)))
                .collect(Collectors.toList());

        return matchList;
    }

    @Override
    public List<Employee> getByDepartment(String department) {
        List<Employee> matchList = eList.stream().filter((e) -> (e.getDepartment().contains(department)))
                .collect(Collectors.toList());

        return matchList;
    }

    @Override
    public List<Employee> getAll() {
        return eList;
    }

    @Override
    public Employee getById(String id) {
        Employee match;
        match = eList.stream().filter(e -> e.getId().equals(id)).findFirst().get();
        return match;
    }

    @Override
    public Employee save(Employee employee) {
        Employee nextEmployee = Employee.of(null, employee.getFirstName(), employee.getLastName(), employee.getEmail(),
                employee.getPhone(), employee.getBirthDate(), employee.getTitle(), employee.getDepartment());
        eList.add(nextEmployee);
        return nextEmployee;
    }

    @Override
    public Employee update(Employee updatedEmployee, String id) {
        deleteById(id);
        Employee e = Employee.of(id, updatedEmployee.getFirstName(), updatedEmployee.getLastName(),
                updatedEmployee.getEmail(), updatedEmployee.getPhone(), updatedEmployee.getBirthDate(),
                updatedEmployee.getTitle(), updatedEmployee.getDepartment());
        eList.add(e);
        return e;
    }

    @Override
    public void deleteById(String id) {
        int matchIndex;
        matchIndex = eList.stream().filter(e -> e.getId().equals(id)).findFirst().map(e -> eList.indexOf(e)).get();
        eList.remove(matchIndex);
    }

    @Override
    public boolean isIdFound(String id) {
        Employee match = null;
        match = eList.stream().filter(e -> e.getId().equals(id)).findFirst().orElse(match);
        return (match != null);
    }

}
