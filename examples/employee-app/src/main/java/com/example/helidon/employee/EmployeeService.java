/* Copyright 2019 Oracle and/or its affiliates. All rights reserved. */
package com.example.helidon.employee;

import java.net.URI;
import java.util.List;
import java.util.logging.Logger;

import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A simple service to greet you. Examples:
 *
 * Get all employees: curl -X GET http://localhost:8080/employees
 *
 * Get employee by id: curl -X GET http://localhost:8080/employees/{id}
 *
 * Add employee curl -X POST http://localhost:8080/employees/{id}
 *
 * Update employee by id curl -X PUT http://localhost:8080/employees/{id}
 *
 * Delete employee by id curl -X DELETE http://localhost:8080/employees/{id}
 *
 * The message is returned as a JSON object
 */
public class EmployeeService implements Service {

    Properties dbCreds = LoadDbCreds.getProperties();
    
    private final EmployeeRepository employees = 
        ReposFactory.getRepo(dbCreds.getProperty("drivertype"));
    private final Employee tempEmployee = Employee.of("", "", "", "", "", "", "");
    private final static Logger LOGGER = 
        Logger.getLogger(EmployeeService.class.getName());

    
    /**
     * A service registers itself by updating the routine rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/", this::getAll)
            .get("/lastname/{name}", this::getByLastName)
            .get("/department/{name}", this::getByDepartment)
            .get("/title/{name}", this::getByTitle)
            .post("/", this::save)
            .get("/{id}", this::getEmployeeById)
            .put("/{id}", this::update)
            .delete("/{id}", this::delete);
        LOGGER.info("Driver "+dbCreds.getProperty("drivertype"));
    }

    private void getAll(final ServerRequest request, final ServerResponse response) {
    	List<Employee> allEmployees = this.employees.getAll();
        response.send(allEmployees);
    }

    private void getByLastName(final ServerRequest request, final ServerResponse response) {
        
        // Invalid query strings handled in isValidQueryStr. Keeping DRY
        if (isValidQueryStr(response, request.path().param("name"))){
            response.status(200).send(this.employees.getByLastName(request.path().param("name")));
        }
    }

    private void getByTitle(final ServerRequest request, final ServerResponse response) {
        
        if (isValidQueryStr(response, request.path().param("name"))){
            response.status(200).send(
                this.employees.getByTitle(request.path().param("name")));
        }
    }

    private void getByDepartment(final ServerRequest request, final ServerResponse response) {

        if (isValidQueryStr(response, request.path().param("name"))){
            response.status(200).send(
                this.employees.getByDepartment(request.path().param("name")));
        }
    }

    private void getEmployeeById(ServerRequest request, ServerResponse response) {
        
        // If invalid, response handled in isValidId. Keeping DRY
        if (isValidId(response, request.path().param("id"))){        
            Employee employee = this.employees.getById(request.path().param("id"));
            response.status(200).send(employee);                
        }        
    }

    /* This is add/create */
    private void save(ServerRequest request, ServerResponse response) {   	 
        request.content().as(Employee.class)
            .thenApply(e -> Employee.of(e.getFirstName(), e.getLastName(), e.getEmail(), e.getPhone(),
            e.getBirthDate(), e.getTitle(), e.getDepartment()))
            .thenApply(this.employees::save)
            .thenCompose(p -> {
                response.status(201).headers().location(URI.create("/employees/" + p.getId()));
                return response.send();
            });
    }

    private void update(ServerRequest request, ServerResponse response) { 

        if (isValidId(response, request.path().param("id"))){
            request.content().as(Employee.class)
                .thenApply(e -> {
                    tempEmployee.setFirstName(e.getFirstName());
                    tempEmployee.setLastName(e.getLastName());
                    tempEmployee.setEmail(e.getEmail());
                    tempEmployee.setPhone(e.getPhone());
                    tempEmployee.setBirthDate(e.getBirthDate());
                    tempEmployee.setTitle(e.getTitle());
                    tempEmployee.setDepartment(e.getDepartment());                       
                    return this.employees.update(tempEmployee, request.path().param("id"));
                })
                .thenCompose(
                    p -> response.status(204).send()
                );
        }

    }
    
    private void delete(final ServerRequest request, final ServerResponse response) {
        if (isValidId(response, request.path().param("id"))){
            this.employees.deleteById(request.path().param("id"));
            response.status(204).send();
        }
    }
    
    private boolean isValidQueryStr(ServerResponse response, String nameStr){
		Map<String, String> errorMessage = new HashMap<>();   
        if (nameStr == null || nameStr.isEmpty() || nameStr.length() > 100){
			errorMessage.put("errorMessage","Invalid query string");
            response.status(400).send(errorMessage);
            return false;
        } else return true;

    }
    
    private boolean isValidId(ServerResponse response, String idStr){
        Map<String, String> errorMessage = new HashMap<>();        
        if (idStr == null || idStr.isEmpty()){
			  errorMessage.put("errorMessage","Invalid query string");
              response.status(400).send(errorMessage);
              return false;
        } else if (this.employees.isIdFound(idStr)){
            return true;
        } else {
            errorMessage.put("errorMessage","ID " + idStr + " not found");
            response.status(404).send(errorMessage);       
            return false;
        }
    }

}
