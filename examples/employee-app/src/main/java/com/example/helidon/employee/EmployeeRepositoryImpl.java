/* Copyright 2019 Oracle and/or its affiliates. All rights reserved. */
package com.example.helidon.employee;

import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;

/* EmployeeRepositoryImpl
    Note: The strings id, name, and other search strings are validated before 
    being passed to the methods in this class.
*/

public class EmployeeRepositoryImpl implements EmployeeRepository {

    private static final CopyOnWriteArrayList<Employee> eList = EmployeeMockList.getInstance();
    
    @Override
    public List<Employee> getByLastName(String name) {
        List<Employee> matchList
            = eList.stream()
                .filter((e) -> (e.getLastName().contains(name)))
                .collect(Collectors.toList());

        return matchList;
    }

    @Override
    public List<Employee> getByTitle(String title) {
        List<Employee> matchList
            = eList.stream()
                .filter((e) -> (e.getTitle().contains(title)))
                .collect(Collectors.toList());

        return matchList;
    }

    @Override
    public List<Employee> getByDepartment(String department) {
        List<Employee> matchList
            = eList.stream()
                .filter((e) -> (e.getDepartment().contains(department)))
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

        match = eList.stream()
            .filter(e -> e.getId().equals(id))
            .findFirst().get();

        return match;
    }

    @Override
    public Employee save(Employee employee) {
        
        /* Using a UUID for ID when auto generated. Test data starts at 100 */       
        Employee nextEmployee
            = new Employee(UUID.randomUUID().toString(), employee.getFirstName(), employee.getLastName(),
                employee.getEmail(), employee.getPhone(),
                employee.getBirthDate(), employee.getTitle(), employee.getDepartment());

        eList.add(nextEmployee);
        return nextEmployee;
    }

    @Override
    public Employee update(Employee updatedEmployee, String id) {
        Employee oldEmployee = this.getById(id);

        oldEmployee.setFirstName(updatedEmployee.getFirstName());
        oldEmployee.setLastName(updatedEmployee.getLastName());
        oldEmployee.setEmail(updatedEmployee.getEmail());
        oldEmployee.setPhone(updatedEmployee.getPhone());
        oldEmployee.setBirthDate(updatedEmployee.getBirthDate());
        oldEmployee.setTitle(updatedEmployee.getTitle());
        oldEmployee.setDepartment(updatedEmployee.getDepartment());  
       
        return oldEmployee;       
    }

    @Override
    public void deleteById(String id) {
        int matchIndex;

        matchIndex = eList.stream()
            .filter(e -> e.getId().equals(id))
            .findFirst()
            .map(e -> eList.indexOf(e))
            .get();

            eList.remove(matchIndex);
    }
    
    @Override
    public boolean isIdFound(String id) {
        Employee match = null;

        match = eList.stream()
            .filter(e -> e.getId().equals(id))
            .findFirst().orElse(match);

        return (match != null);
    }

}
