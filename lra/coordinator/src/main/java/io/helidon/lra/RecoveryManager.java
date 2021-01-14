/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.lra;

import oracle.ucp.jdbc.PoolDataSource;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Path("/")
@ApplicationScoped
public class RecoveryManager  {

    @Inject
    @Named("coordinatordb")
    PoolDataSource coordinatordb;

    private  Connection connection = null;
    Map<String, LRA> lraRecoveryRecordMap = new HashMap();

    void log(LRA lra, String compensatorLink){
        if(true) return;
        log("LRARecordPersistence.log... lraRecord.lraId = " + lra.lraId + ", compensatorLink = " + compensatorLink);
        try {
            connection.createStatement().execute(
                    "insert into lrarecords values ('"+ lra.lraId + "', " +
                            "'testcompleteurl', 'testcompensateurl', 'teststatusurl', '"+compensatorLink+"')");
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    void delete(LRA lra){
        String sqlString = "delete lrarecords  where lraid ='"+ lra.lraId + "'";
        log("LRARecordPersistence.delete... " + sqlString);
        try {
            connection.createStatement().execute(sqlString);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    void log(String message) {
        System.out.println(message);
    }
}
