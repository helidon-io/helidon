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
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


@ApplicationScoped
@Path("lra-recovery")
public class RecoveryManager  {
    private static final Logger LOGGER = Logger.getLogger(RecoveryManager.class.getName());

    @Inject
    @Named("coordinatordb")
    PoolDataSource coordinatordb;

    private long timeSinceLastPurge = System.currentTimeMillis();
    private static Connection connection = null;
    private static RecoveryManager instance;
    private boolean isLoggingEnabled = Boolean.valueOf(System.getProperty("lra.logging.enabled", "true"));

    static RecoveryManager getInstance() {
        return instance;
    }

    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) throws SQLException {
        LOGGER.info("RecoveryManager init coordinatordb:" + coordinatordb);
        instance = this;
        if(!isLoggingEnabled) return;
        dropTables();
        createTables();
        loadLRALogs();
    }

    void createTables() throws SQLException {
        LOGGER.info("create LRA log table");
        try (Connection connection = coordinatordb.getConnection()){
            connection.createStatement().execute(
                    "create table LRALOGS (" +
                            "lraId varchar(64) NOT NULL, " +
                            // lraState is updated in all participants during lra state change so is 1-to-n but just using single table for now
                            "lraState varchar(16) NOT NULL, " +
                            "participantType varchar(16) NOT NULL, " +
                            "completeURI varchar(64) NOT NULL, " +
                            "compensateURI varchar(64) NOT NULL, " +
                            "afterLRAURI varchar(64), " +
                            "statusURI varchar(64), " +
                            "forgetURI varchar(64) " +
                            ")");
        } catch (SQLException ex) {
            LOGGER.info("RecoveryManager createTable failed (expected if this is not initial setup ex:" + ex); //todo conduct if exists to gate this
        }
        //todo test and fail throw to init
    }


    public void log(Participant participant) {
        if(!isLoggingEnabled) return;
        LOGGER.info("log participant");
        //todo check if exist in map and if not
        try (Connection connection = coordinatordb.getConnection()){
            LOGGER.info("RecoveryManager log connection:" + connection);
        } catch (SQLException ex) {
            LOGGER.info("RecoveryManager dropTables failed ex:" + ex);
        }
    }

    void dropTables()  {
        LOGGER.info("create LRA log table");
        try (Connection connection = coordinatordb.getConnection()){
            connection.createStatement().execute("drop table LRALOGS");
        } catch (SQLException ex) {
            LOGGER.info("RecoveryManager dropTables failed ex:" + ex);
        }
    }

    /**
     * Load records from database and add participants thus initializing config, connectionfactories, etc. as appropriate
     * This is must be a blocking call
     */
    void loadLRALogs(){
        LOGGER.info("logLRALogs");
    }

    /**
     * Purge LRA records that are marked for deletion
     * as well as any participant records/config if inactive for 24hr default //todo make this configurable
     * This is conducted whenever 1000 new records have been entered //todo make this configurable as well and potentially time based as well
     */
    void purge(){
        if (timeSinceLastPurge + (5 * 60 +1000) < System.currentTimeMillis() ) {
            // lraRecoveryRecordMap
            String sqlString = "delete lrarecords  where lraid ='" + "lraRecoveryRecordMap item" + "'";
            LOGGER.info("LRARecordPersistence.delete... " + sqlString);
            try {
                connection.createStatement().execute(sqlString);
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }

    @POST
    @Path("/getAllLRAs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllLRAs() {
        return Response.ok().build();
    } //todo
}
