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

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.jdbc.JdbcClientBuilder;

/**
 * Implementation of the {@link EmployeeRepository}. This implementation uses an
 * Oracle database to persist the Employee objects.
 */
final class EmployeeRepositoryImplDB implements EmployeeRepository {

    private final DbClient dbClient;

    /**
     * Creates the database connection using the parameters specified in the
     * <code>application.yaml</code> file located in the <code>resources</code> directory.
     *
     * @param config Represents the application configuration.
     */
    EmployeeRepositoryImplDB(Config config) {
        String url = "jdbc:oracle:thin:@";
        String driver = "oracle.jdbc.driver.OracleDriver";

        String dbUserName = config.get("app.user").asString().orElse("sys as SYSDBA");
        String dbUserPassword = config.get("app.password").asString().orElse("password");
        String dbHostURL = config.get("app.hosturl").asString().orElse("localhost:1521/xe");

        try {
            Class.forName(driver);
        } catch (Exception sqle) {
            sqle.printStackTrace();
        }

        // now we create the DB Client - explicitly use JDBC, so we can
        // configure JDBC specific configuration
        dbClient = JdbcClientBuilder.create()
                .url(url + dbHostURL)
                .username(dbUserName)
                .password(dbUserPassword)
                .build();
    }

    @Override
    public List<Employee> getAll() {
        String queryStr = "SELECT * FROM EMPLOYEE";

        return toEmployeeList(dbClient.execute().query(queryStr));
    }

    @Override
    public List<Employee> getByLastName(String name) {
        String queryStr = "SELECT * FROM EMPLOYEE WHERE LASTNAME LIKE ?";

        return toEmployeeList(dbClient.execute().query(queryStr, name));
    }

    @Override
    public List<Employee> getByTitle(String title) {
        String queryStr = "SELECT * FROM EMPLOYEE WHERE TITLE LIKE ?";

        return toEmployeeList(dbClient.execute().query(queryStr, title));
    }

    @Override
    public List<Employee> getByDepartment(String department) {
        String queryStr = "SELECT * FROM EMPLOYEE WHERE DEPARTMENT LIKE ?";

        return toEmployeeList(dbClient.execute().query(queryStr, department));
    }

    @Override
    public Employee save(Employee employee) {
        String insertTableSQL = "INSERT INTO EMPLOYEE "
                + "(ID, FIRSTNAME, LASTNAME, EMAIL, PHONE, BIRTHDATE, TITLE, DEPARTMENT) "
                + "VALUES(EMPLOYEE_SEQ.NEXTVAL,?,?,?,?,?,?,?)";

        dbClient.execute()
                .createInsert(insertTableSQL)
                .addParam(employee.getFirstName())
                .addParam(employee.getLastName())
                .addParam(employee.getEmail())
                .addParam(employee.getPhone())
                .addParam(employee.getBirthDate())
                .addParam(employee.getTitle())
                .addParam(employee.getDepartment())
                .execute();
        // let's always return the employee once the insert finishes
        return employee;
    }

    @Override
    public long deleteById(String id) {
        String deleteRowSQL = "DELETE FROM EMPLOYEE WHERE ID=?";

        return dbClient.execute().delete(deleteRowSQL, id);
    }

    @Override
    public Optional<Employee> getById(String id) {
        String queryStr = "SELECT * FROM EMPLOYEE WHERE ID =?";

        return dbClient.execute()
                .get(queryStr, id)
                .map(row -> row.as(Employee.class));
    }

    @Override
    public long update(Employee updatedEmployee, String id) {
        String updateTableSQL = "UPDATE EMPLOYEE SET FIRSTNAME=?, LASTNAME=?, EMAIL=?, PHONE=?, BIRTHDATE=?, TITLE=?, "
                + "DEPARTMENT=?  WHERE ID=?";

        return dbClient.execute()
                .createUpdate(updateTableSQL)
                .addParam(updatedEmployee.getFirstName())
                .addParam(updatedEmployee.getLastName())
                .addParam(updatedEmployee.getEmail())
                .addParam(updatedEmployee.getPhone())
                .addParam(updatedEmployee.getBirthDate())
                .addParam(updatedEmployee.getTitle())
                .addParam(updatedEmployee.getDepartment())
                .addParam(Integer.parseInt(id))
                .execute();
    }

    private static List<Employee> toEmployeeList(Stream<DbRow> rows) {
        return rows.map(EmployeeDbMapper::read).toList();
    }

    private static final class EmployeeDbMapper {
        private EmployeeDbMapper() {
        }

        static Employee read(DbRow row) {
            // map named columns to an object
            return Employee.of(
                    row.column("ID").get(String.class),
                    row.column("FIRSTNAME").get(String.class),
                    row.column("LASTNAME").get(String.class),
                    row.column("EMAIL").get(String.class),
                    row.column("PHONE").get(String.class),
                    row.column("BIRTHDATE").get(String.class),
                    row.column("TITLE").get(String.class),
                    row.column("DEPARTMENT").get(String.class)
            );
        }
    }
}
