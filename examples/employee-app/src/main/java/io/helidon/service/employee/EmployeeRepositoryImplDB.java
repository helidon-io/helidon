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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import io.helidon.config.Config;

import oracle.jdbc.pool.OracleDataSource;

/**
 * Implementation of the {@link EmployeeRepository}. This implementation uses an
 * Oracle database to persist the Employee objects.
 *
 */
public final class EmployeeRepositoryImplDB implements EmployeeRepository {

    private Connection conn;

    /**
     * Creates the database connection using the parameters specified in the
     * <code>application.yaml</code> file located in the <code>resources</code> directory.
     * @param config Represents the application configuration.
     */
    public EmployeeRepositoryImplDB(Config config) {
        String url = "jdbc:oracle:thin:@";
        String driver = "oracle.jdbc.driver.OracleDriver";

        String dbUserName = config.get("app.user").asString().orElse("sys as SYSDBA");
        String dbUserPassword = config.get("app.password").asString().orElse("password");
        String dbHostURL = config.get("app.hosturl").asString().orElse("localhost:1521/xe");

        try {
            Class.forName(driver).getDeclaredConstructor().newInstance();
        } catch (Exception sqle) {
            sqle.printStackTrace();
        }

        try {
            OracleDataSource ods = new OracleDataSource();
            ods.setURL(url + dbHostURL);
            ods.setUser(dbUserName);
            ods.setPassword(dbUserPassword);
            conn = ods.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Employee> getAll() {
        String queryStr = "SELECT * FROM EMPLOYEE";
        List<Employee> resultList = this.query(queryStr, null);
        return resultList;
    }

    @Override
    public List<Employee> getByLastName(String name) {
        String queryStr = "SELECT * FROM EMPLOYEE WHERE LASTNAME LIKE ?";
        List<Employee> resultList = this.query(queryStr, name);
        return resultList;
    }

    @Override
    public List<Employee> getByTitle(String title) {
        String queryStr = "SELECT * FROM EMPLOYEE WHERE TITLE LIKE ?";
        List<Employee> resultList = this.query(queryStr, title);
        return resultList;
    }

    @Override
    public List<Employee> getByDepartment(String department) {
        String queryStr = "SELECT * FROM EMPLOYEE WHERE DEPARTMENT LIKE ?";
        List<Employee> resultList = this.query(queryStr, department);
        return resultList;
    }

    @Override
    public Employee save(Employee employee) {
        String insertTableSQL = "INSERT INTO EMPLOYEE "
                + "(ID, FIRSTNAME, LASTNAME, EMAIL, PHONE, BIRTHDATE, TITLE, DEPARTMENT) "
                + "VALUES(EMPLOYEE_SEQ.NEXTVAL,?,?,?,?,?,?,?)";

        try (PreparedStatement preparedStatement = this.conn.prepareStatement(insertTableSQL)) {

            preparedStatement.setString(1, employee.getFirstName());
            preparedStatement.setString(2, employee.getLastName());
            preparedStatement.setString(3, employee.getEmail());
            preparedStatement.setString(4, employee.getPhone());
            preparedStatement.setString(5, employee.getBirthDate());
            preparedStatement.setString(6, employee.getTitle());
            preparedStatement.setString(7, employee.getDepartment());
            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (SQLException e) {
            System.out.println("SQL Add Error: " + e.getMessage());

        } catch (Exception e) {
            System.out.println("Add Error: " + e.getMessage());
        }
        return employee;
    }

    @Override
    public void deleteById(String id) {
        String deleteRowSQL = "DELETE FROM EMPLOYEE WHERE ID=?";
        try (PreparedStatement preparedStatement = this.conn.prepareStatement(deleteRowSQL)) {
            preparedStatement.setInt(1, Integer.parseInt(id));
            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (SQLException e) {
            System.out.println("SQL Delete Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Delete Error: " + e.getMessage());
        }

    }

    @Override
    public Employee getById(String id) {
        String queryStr = "SELECT * FROM EMPLOYEE WHERE ID =?";
        List<Employee> resultList = this.query(queryStr, Integer.parseInt(id));

        if (resultList.size() > 0) {
            return resultList.get(0);
        } else {
            return null;
        }
    }

    /**
     * Execute the <code>select</code> query specified in the parameters.
     * @param sqlQueryStr Contains the <code>select</code> query
     * @param value Contains the value of the variable of the <code>select</code>.
     * @return
     */
    private List<Employee> query(String sqlQueryStr, Object value) {

        List<Employee> resultList = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sqlQueryStr)) {
            if (value != null) {
                if (value instanceof String) {
                    stmt.setString(1, value + "%");
                } else {
                    stmt.setInt(1, (Integer) value);
                }
            }
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                resultList.add(Employee.of(rs.getString("ID"), rs.getString("FIRSTNAME"), rs.getString("LASTNAME"),
                        rs.getString("EMAIL"), rs.getString("PHONE"), rs.getString("BIRTHDATE"), rs.getString("TITLE"),
                        rs.getString("DEPARTMENT")));
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.out.println("SQL Query Error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultList;
    }

    @Override
    public Employee update(Employee updatedEmployee, String id) {
        String updateTableSQL = "UPDATE EMPLOYEE SET FIRSTNAME=?, LASTNAME=?, EMAIL=?, PHONE=?, BIRTHDATE=?, TITLE=?, "
                + "DEPARTMENT=?  WHERE ID=?";
        try (PreparedStatement preparedStatement = this.conn.prepareStatement(updateTableSQL);) {
            preparedStatement.setString(1, updatedEmployee.getFirstName());
            preparedStatement.setString(2, updatedEmployee.getLastName());
            preparedStatement.setString(3, updatedEmployee.getEmail());
            preparedStatement.setString(4, updatedEmployee.getPhone());
            preparedStatement.setString(5, updatedEmployee.getBirthDate());
            preparedStatement.setString(6, updatedEmployee.getTitle());
            preparedStatement.setString(7, updatedEmployee.getDepartment());
            preparedStatement.setInt(8, Integer.parseInt(id));
            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (SQLException e) {
            System.out.println("SQL Update Error: " + e.getMessage());

        } catch (Exception e) {
            System.out.println("Update Error: " + e.getMessage());

        }
        return null;
    }

    @Override
    public boolean isIdFound(String id) {
        String queryStr = "SELECT * FROM EMPLOYEE WHERE ID =?";
        List<Employee> resultList = this.query(queryStr, Integer.parseInt(id));
        return (resultList.size() > 0);
    }
}
