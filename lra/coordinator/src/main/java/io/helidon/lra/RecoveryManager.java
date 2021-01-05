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

import oracle.jdbc.OraclePreparedStatement;
import oracle.ucp.jdbc.PoolDataSource;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Path("/")
@ApplicationScoped
public class RecoveryManager implements Runnable {

    @Inject
    @Named("coordinatordb")
    PoolDataSource coordinatordb;

    private  Connection connection = null;
    Map<String, LRA> lraRecoveryRecordMap = new HashMap();
    private static RecoveryManager singleton;
    boolean isRecovered = false;

    static RecoveryManager getInstance() {
        return singleton;
    }

    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
        log("RecoveryManager.init " + init + " recovery disabled");
//        recover();
        singleton = this;
        new Thread(this).start();
    }

    public void recover() {
        try {
            connection = coordinatordb.getConnection();
            log("LRARecordPersistence  coordinatordb.getConnection():" + connection);
        } catch (SQLException sqlException) {
            log("LRARecordPersistence.init sqlException (expected if table exists)" + sqlException);
        }
//        try {
//            log("LRARecordPersistence drop table ...");
//            connection.createStatement().execute(
//                    "drop table lrarecords");
//        } catch (SQLException sqlException) {
//            log("LRARecordPersistence.init sqlException (expected if table exists)" + sqlException);
//        }
//        try {
//            log("LRARecordPersistence create table...");
//            connection.createStatement().execute(
//                    "create table lrarecords (lraid varchar(64) PRIMARY KEY NOT NULL, " +
//                            "completeurl varchar(64), compensateurl varchar(64), " +
//                            "statusurl varchar(64), additionaldata varchar(1024) )");
//        } catch (SQLException sqlException) {
//            log("LRARecordPersistence.init sqlException (expected if table exists)" + sqlException);
//        }
        try {
            log("LRARecordPersistence create table...");
            try (OraclePreparedStatement st = (OraclePreparedStatement) connection.prepareStatement("select * from lrarecords")) {
//                st.setString(1, id);
                ResultSet res = st.executeQuery();
                while(res.next()) {
                    String lraid = res.getString(1);
                    String completeurl = res.getString(2);
                    String compensateurl = res.getString(3);
                    String statusurl = res.getString(4);
                    String additionaldata = res.getString(5);
                    log("LRARecord for : " + lraid);
                    log("   completeurl: " + completeurl);
                    log(" compensateurl: " + compensateurl);
                    log("     statusurl: " + statusurl);
                    log("additionaldata: " + additionaldata);
                    LRA lra = new LRA(lraid);
                    lra.addParticipant(additionaldata, false, false); //currently assumes rest for all
                    lraRecoveryRecordMap.put(lraid, lra);
                    lra.tryDoEnd(false, false);
                }

            }
        } catch (SQLException sqlException) {
            log("LRARecordPersistence.init sqlException (expected if table exists)" + sqlException);
        }
    }

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

    void add(String lraId, LRA lra) {
//        Coordinator.getInstance().remove(lraId); //7, 8, or 9 intermittent tests
        this.lraRecoveryRecordMap.put(lraId, lra);
    }

    @Override
    public void run() {
        while (true) {
            if (lraRecoveryRecordMap.size() >0 ) {
                log("-------->RECOVERY MANAGER lraRecoveryRecordMap size" + lraRecoveryRecordMap.size());
                for (String lraId: lraRecoveryRecordMap.keySet()) {
                    LRA lra = lraRecoveryRecordMap.get(lraId);
                        log(
                                "RecoveryManager thread, will status and forget lraId:" + lraId );
                        Response statusResponse = lra.sendStatus();
                        if (statusResponse != null) {
                            int status = statusResponse.getStatus();
                            log("RecoveryManager.run status is " + status);
                            if(status < 500) {
                                lra.sendCompletion();
                                lra.sendForget();
                                lraRecoveryRecordMap.remove(lraId);
                            }
                        } else {
                            log("RecoveryManager.run status is null");
                            lra.sendCompletion();
                            lraRecoveryRecordMap.remove(lraId);
                        }
                    isRecovered = true;
                    }
                }

            try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    void log(String message) {
        System.out.println(message);
    }
}
