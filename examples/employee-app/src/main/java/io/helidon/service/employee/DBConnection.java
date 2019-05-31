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
import java.sql.SQLException;
import java.util.Properties;

import io.helidon.config.Config;
import oracle.jdbc.pool.OracleDataSource;

public final class  DBConnection {

    public final String DB_USERNAME;
    public final String DB_USER_PASSWORD;
    public final String DB_HOST_URL;

    private static final String URL = "jdbc:oracle:thin:@";
    private static final String DRIVER = "oracle.jdbc.driver.OracleDriver";
    

    private static Connection connection = null;
    private static DBConnection instance = null;

    
    private DBConnection() {
    	Config config = Config.create();

        DB_USERNAME = config.get("app.user").asString().orElse("sys as SYSDBA");
        DB_USER_PASSWORD =  config.get("app.password").asString().orElse("password");
        DB_HOST_URL = config.get("app.hosturl").asString().orElse("localhost:1521/xe");
		
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
