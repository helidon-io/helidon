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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * The Employee service endpoints.
 * <ul>
 *     <li>Get all employees: {@code curl -X GET http://localhost:8080/employees}</li>
 *     <li>Get employee by id: {@code curl -X GET http://localhost:8080/employees/{id}}</li>
 *     <li>Add employee {@code curl -X POST http://localhost:8080/employees/{id}}</li>
 *     <li>Update employee by id {@code curl -X PUT http://localhost:8080/employees/{id}}</li>
 *     <li>Delete employee by id {@code curl -X DELETE http://localhost:8080/employees/{id}}</li>
 * </ul>
 * The message is returned as a JSON object
 */
public class EmployeeService implements HttpService {
    private final EmployeeRepository employees;
    private static final Logger LOGGER = Logger.getLogger(EmployeeService.class.getName());

    EmployeeService(Config config) {
        String driverType = config.get("app.drivertype").asString().orElse("Array");
        employees = EmployeeRepository.create(driverType, config);
    }

    /**
     * A service registers itself by updating the routine rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::getAll)
             .get("/lastname/{name}", this::getByLastName)
             .get("/department/{name}", this::getByDepartment)
             .get("/title/{name}", this::getByTitle)
             .post("/", this::save)
             .get("/{id}", this::getEmployeeById)
             .put("/{id}", this::update)
             .delete("/{id}", this::delete);
    }

    /**
     * Gets all the employees.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getAll(ServerRequest request, ServerResponse response) {
        LOGGER.fine("getAll");

        response.send(employees.getAll());
    }

    /**
     * Gets the employees by the last name specified in the parameter.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getByLastName(ServerRequest request, ServerResponse response) {
        LOGGER.fine("getByLastName");

        String name = request.path().pathParameters().get("name");
        // Invalid query strings handled in isValidQueryStr. Keeping DRY
        if (isValidQueryStr(response, name)) {
            response.send(employees.getByLastName(name));
        }
    }

    /**
     * Gets the employees by the title specified in the parameter.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getByTitle(ServerRequest request, ServerResponse response) {
        LOGGER.fine("getByTitle");

        String title = request.path().pathParameters().get("name");
        if (isValidQueryStr(response, title)) {
            response.send(employees.getByTitle(title));
        }
    }

    /**
     * Gets the employees by the department specified in the parameter.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getByDepartment(ServerRequest request, ServerResponse response) {
        LOGGER.fine("getByDepartment");

        String department = request.path().pathParameters().get("name");
        if (isValidQueryStr(response, department)) {
            response.send(employees.getByDepartment(department));
        }
    }

    /**
     * Gets the employees by the ID specified in the parameter.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getEmployeeById(ServerRequest request, ServerResponse response) {
        LOGGER.fine("getEmployeeById");

        String id = request.path().pathParameters().get("id");
        // If invalid, response handled in isValidId. Keeping DRY
        if (isValidQueryStr(response, id)) {
            employees.getById(id)
                     .ifPresentOrElse(response::send, () -> response.status(404).send());
        }
    }

    /**
     * Saves a new employee.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void save(ServerRequest request, ServerResponse response) {
        LOGGER.fine("save");

        Employee employee = request.content().as(Employee.class);
        employees.save(Employee.of(null,
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getBirthDate(),
                employee.getTitle(),
                employee.getDepartment()));
        response.status(201).send();
    }

    /**
     * Updates an existing employee.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void update(ServerRequest request, ServerResponse response) {
        LOGGER.fine("update");

        String id = request.path().pathParameters().get("id");
        if (isValidQueryStr(response, id)) {
            if (employees.update(request.content().as(Employee.class), id) == 0) {
                response.status(404).send();
            } else {
                response.status(204).send();
            }
        }
    }

    /**
     * Deletes an existing employee.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void delete(ServerRequest request, ServerResponse response) {
        LOGGER.fine("delete");

        String id = request.path().pathParameters().get("id");
        if (isValidQueryStr(response, id)) {
            if (employees.deleteById(id) == 0) {
                response.status(404).send();
            } else {
                response.status(204).send();
            }
        }
    }

    /**
     * Validates the parameter.
     *
     * @param response the server response
     * @param name     employee name
     * @return true if valid, false otherwise
     */
    private boolean isValidQueryStr(ServerResponse response, String name) {
        Map<String, String> errorMessage = new HashMap<>();
        if (name == null || name.isEmpty() || name.length() > 100) {
            errorMessage.put("errorMessage", "Invalid query string");
            response.status(400).send(errorMessage);
            return false;
        } else {
            return true;
        }
    }
}
