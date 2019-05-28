/* Copyright 2019 Oracle and/or its affiliates. All rights reserved. */

 /* Interface for Data Access Objects */
package com.example.helidon.employee;

import java.util.List;

public interface EmployeeRepository {

    public List<Employee> getAll();

    public List<Employee> getByLastName(String name);

    public List<Employee> getByTitle(String title);

    public List<Employee> getByDepartment(String department);

    public Employee save(Employee employee); // Add new employee
    
    public Employee update(Employee updatedEmployee, String id);

    public void deleteById(String id);

    public Employee getById(String id);
    
    public boolean isIdFound(String id);
}