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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * The Employee service endpoints. Get all employees: curl -X GET
 * http://localhost:8080/employees Get employee by id: curl -X GET
 * http://localhost:8080/employees/{id} Add employee curl -X POST
 * http://localhost:8080/employees/{id} Update employee by id curl -X PUT
 * http://localhost:8080/employees/{id} Delete employee by id curl -X DELETE
 * http://localhost:8080/employees/{id} The message is returned as a JSON object
 */
public class EmployeeService implements Service {
    private final EmployeeRepository employees;
    private static final Logger LOGGER = Logger.getLogger(EmployeeService.class.getName());

    EmployeeService(Config config) {
        employees = EmployeeRepository.create(config.get("app.drivertype")
                                                      .asString()
                                                      .orElse("Array"),
                                              config);
    }

    /**
     * A service registers itself by updating the routine rules.
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
    }

    /**
     * Gets all the employees.
     * @param request  the server request
     * @param response the server response
     */
    private void getAll(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine("getAll");

        this.employees
                .getAll()
                .thenAccept(response::send)
                .exceptionally(response::send);
    }

    /**
     * Gets the employees by the last name specified in the parameter.
     * @param request  the server request
     * @param response the server response
     */
    private void getByLastName(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine("getByLastName");

        String name = request.path().param("name");
        // Invalid query strings handled in isValidQueryStr. Keeping DRY
        if (isValidQueryStr(response, name)) {
            this.employees.getByLastName(name)
                    .thenAccept(response::send)
                    .exceptionally(response::send);
        }
    }

    /**
     * Gets the employees by the title specified in the parameter.
     * @param request  the server request
     * @param response the server response
     */
    private void getByTitle(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine("getByTitle");

        String title = request.path().param("name");
        if (isValidQueryStr(response, title)) {
            this.employees.getByTitle(title)
                    .thenAccept(response::send)
                    .exceptionally(response::send);
        }
    }

    /**
     * Gets the employees by the department specified in the parameter.
     * @param request  the server request
     * @param response the server response
     */
    private void getByDepartment(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine("getByDepartment");

        String department = request.path().param("name");
        if (isValidQueryStr(response, department)) {
            this.employees.getByDepartment(department)
                    .thenAccept(response::send)
                    .exceptionally(response::send);
        }
    }

    /**
     * Gets the employees by the ID specified in the parameter.
     * @param request  the server request
     * @param response the server response
     */
    private void getEmployeeById(ServerRequest request, ServerResponse response) {
        LOGGER.fine("getEmployeeById");

        String id = request.path().param("id");
        // If invalid, response handled in isValidId. Keeping DRY
        if (isValidQueryStr(response, id)) {
            this.employees.getById(id)
                    .thenAccept(it -> {
                        if (it.isPresent()) {
                            // found
                            response.send(it.get());
                        } else {
                            // not found
                            response.status(404).send();
                        }
                    })
                    .exceptionally(response::send);
        }
    }

    /**
     * Saves a new employee.
     * @param request  the server request
     * @param response the server response
     */
    private void save(ServerRequest request, ServerResponse response) {
        LOGGER.fine("save");

        request.content()
                .as(Employee.class)
                .thenApply(e -> Employee.of(null,
                                            e.getFirstName(),
                                            e.getLastName(),
                                            e.getEmail(),
                                            e.getPhone(),
                                            e.getBirthDate(),
                                            e.getTitle(),
                                            e.getDepartment()))
                .thenCompose(this.employees::save)
                .thenAccept(it -> response.status(201).send())
                .exceptionally(response::send);
    }

    /**
     * Updates an existing employee.
     * @param request  the server request
     * @param response the server response
     */
    private void update(ServerRequest request, ServerResponse response) {
        LOGGER.fine("update");

        String id = request.path().param("id");

        if (isValidQueryStr(response, id)) {
            request.content()
                    .as(Employee.class)
                    .thenCompose(e -> this.employees.update(e, id))
                    .thenAccept(count -> {
                        if (count == 0) {
                            response.status(404).send();
                        } else {
                            response.status(204).send();
                        }
                    })
                    .exceptionally(response::send);

        }

    }

    /**
     * Deletes an existing employee.
     * @param request  the server request
     * @param response the server response
     */
    private void delete(final ServerRequest request, final ServerResponse response) {
        LOGGER.fine("delete");

        String id = request.path().param("id");

        if (isValidQueryStr(response, id)) {
            this.employees.deleteById(id)
                    .thenAccept(count -> {
                        if (count == 0) {
                            response.status(404).send();
                        } else {
                            response.status(204).send();
                        }
                    })
                    .exceptionally(response::send);
        }
    }

    /**
     * Validates the parameter.
     * @param response the server response
     * @param nameStr
     * @return
     */
    private boolean isValidQueryStr(ServerResponse response, String nameStr) {
        Map<String, String> errorMessage = new HashMap<>();
        if (nameStr == null || nameStr.isEmpty() || nameStr.length() > 100) {
            errorMessage.put("errorMessage", "Invalid query string");
            response.status(400).send(errorMessage);
            return false;
        } else {
            return true;
        }
    }
}
