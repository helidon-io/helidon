/* Copyright 2019 Oracle and/or its affiliates. All rights reserved. */
package com.example.helidon.employee;

public class ReposFactory {

    public static EmployeeRepository getRepo(String driverType) {
        switch (driverType) {
            case "Array":
                return new EmployeeRepositoryImpl();
            case "Oracle":
                return new EmployeeRepositoryImplDB();
            default:
                System.out.println("No database driver found...");
                System.exit(1);
                return null;
        }

    }
}
