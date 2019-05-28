/* Copyright 2019 Oracle and/or its affiliates. All rights reserved. */
package com.example.helidon.employee;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import oracle.jdbc.pool.OracleDataSource;

public class DBConnection {

    public static final String DB_USERNAME;
    public static final String DB_USER_PASSWORD;
    public static final String DB_HOST_URL;

    //Initialize DB Credentials
    static {
        Properties myCreds = LoadDbCreds.getProperties();

        DB_USERNAME = myCreds.getProperty("user");
        DB_USER_PASSWORD =  myCreds.getProperty("password");
        DB_HOST_URL = myCreds.getProperty("hosturl");
    }

    private static final String URL = "jdbc:oracle:thin:@";
    private static final String DRIVER = "oracle.jdbc.driver.OracleDriver";
    

    private static Connection connection = null;
    private static DBConnection instance = null;

    
    private DBConnection() {
    	
        try {
            // Note: 5/20 Added .getDecl because of deprecation in Java 9
            Class.forName(DRIVER).getDeclaredConstructor().newInstance();
        } catch (Exception sqle) {
            sqle.getMessage();
        }
    }

    public static DBConnection getInstance() {
        if (connection == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    public Connection getConnection() {
        if (connection == null) {
            try {
                OracleDataSource ods = new OracleDataSource();
                ods.setURL(URL + DB_HOST_URL);
                ods.setUser(DB_USERNAME);
                ods.setPassword(DB_USER_PASSWORD);
                connection = ods.getConnection();
            } catch (SQLException e) {
                e.printStackTrace();
                e.getMessage();
            }
        }
        return connection;
    }

}
