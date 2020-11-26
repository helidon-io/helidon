package io.helidon.lra;

import oracle.jdbc.OraclePreparedStatement;
import oracle.ucp.jdbc.PoolDataSource;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
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
public class RecoveryManager {

    @Inject
    @Named("coordinatordb")
    PoolDataSource coordinatordb;

    private static Connection connection = null;

    //using separate map approach for recovery and runtime txs
    Map<String, LRA> lraRecoveryRecordMap = new HashMap();

    private static RecoveryManager singleton;
    static {
        System.setProperty("oracle.jdbc.fanEnabled", "false");
    }

    static RecoveryManager getInstance() {
        return singleton;
    }

    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
        System.out.println("LRARecordPersistence.init " + init + " recovery disabled");
//        recover();
        singleton = this;
    }

    public void recover() {
        try {
            connection = coordinatordb.getConnection();
            System.out.println("LRARecordPersistence  coordinatordb.getConnection():" + connection);
        } catch (SQLException sqlException) {
            System.out.println("LRARecordPersistence.init sqlException (expected if table exists)" + sqlException);
        }
//        try {
//            System.out.println("LRARecordPersistence drop table ...");
//            connection.createStatement().execute(
//                    "drop table lrarecords");
//        } catch (SQLException sqlException) {
//            System.out.println("LRARecordPersistence.init sqlException (expected if table exists)" + sqlException);
//        }
//        try {
//            System.out.println("LRARecordPersistence create table...");
//            connection.createStatement().execute(
//                    "create table lrarecords (lraid varchar(64) PRIMARY KEY NOT NULL, " +
//                            "completeurl varchar(64), compensateurl varchar(64), " +
//                            "statusurl varchar(64), additionaldata varchar(1024) )");
//        } catch (SQLException sqlException) {
//            System.out.println("LRARecordPersistence.init sqlException (expected if table exists)" + sqlException);
//        }
        try {
            System.out.println("LRARecordPersistence create table...");
            try (OraclePreparedStatement st = (OraclePreparedStatement) connection.prepareStatement("select * from lrarecords")) {
//                st.setString(1, id);
                ResultSet res = st.executeQuery();
                while(res.next()) {
                    String lraid = res.getString(1);
                    String completeurl = res.getString(2);
                    String compensateurl = res.getString(3);
                    String statusurl = res.getString(4);
                    String additionaldata = res.getString(5);
                    System.out.println("LRARecord for : " + lraid);
                    System.out.println("   completeurl: " + completeurl);
                    System.out.println(" compensateurl: " + compensateurl);
                    System.out.println("     statusurl: " + statusurl);
                    System.out.println("additionaldata: " + additionaldata);
                    LRA lra = new LRA(lraid);
                    lra.addParticipant(additionaldata, false, false); //currently assumes rest for all
                    lraRecoveryRecordMap.put(lraid, lra);
                    lra.tryDoEnd(false, false);
                }

            }
        } catch (SQLException sqlException) {
            System.out.println("LRARecordPersistence.init sqlException (expected if table exists)" + sqlException);
        }
    }

    static void log(LRA lra, String compensatorLink){
        if(true) return;
        System.out.println("LRARecordPersistence.log... lraRecord.lraId = " + lra.lraId + ", compensatorLink = " + compensatorLink);
        try {
            connection.createStatement().execute(
                    "insert into lrarecords values ('"+ lra.lraId + "', " +
                            "'testcompleteurl', 'testcompensateurl', 'teststatusurl', '"+compensatorLink+"')");
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    static void delete(LRA lra){
        String sqlString = "delete lrarecords  where lraid ='"+ lra.lraId + "'";
        System.out.println("LRARecordPersistence.delete... " + sqlString);
        try {
            connection.createStatement().execute(sqlString);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }


}
