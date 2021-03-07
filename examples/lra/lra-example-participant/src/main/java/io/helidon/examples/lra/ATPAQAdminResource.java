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
package io.helidon.examples.lra;

import oracle.AQ.AQException;
import oracle.jms.AQjmsConstants;
import oracle.jms.AQjmsFactory;
import oracle.jms.AQjmsSession;

import java.sql.*;
import java.sql.Connection;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.jms.*;
import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


import java.io.IOException;

@Path("/atpaqadmin")
@ApplicationScoped
public class ATPAQAdminResource {
    PropagationSetup propagationSetup = new PropagationSetup();
    static String orderuser = "ORDERUSER";
    static String orderpw = "Welcome12345"; //System.getenv("orderuser.password").trim();
    static String inventoryuser = "INVENTORYUSER";
    static String inventorypw =  "Welcome12345"; //System.getenv("inventoryuser.password").trim();
    static String lraTestQueueName = "LRATESTQUEUE";
    static String orderQueueName = "ORDERQUEUE";
    static String orderQueueTableName = "ORDERQUEUETABLE";
    static String inventoryQueueName = "INVENTORYQUEUE";
    static String inventoryQueueTableName = "INVENTORYQUEUETABLE";
    static String orderToInventoryLinkName = "ORDERTOINVENTORYLINK";
    static String inventoryToOrderLinkName = "INVENTORYTOORDERLINK";
    static String lraSendQueueName = "LRASENDQUEUE";
    static String lraSendQueueTableName = "LRASENDQUEUETABLE";
    static String lraReplyQueueName = "LRAREPLYQUEUE";
    static String lraReplyQueueTableName = "LRAREPLYQUEUETABLE";
    static String cwalletobjecturi =   System.getenv("cwalletobjecturi");
    static String inventoryhostname =   System.getenv("inventoryhostname");
    static String inventoryport =   System.getenv("inventoryport");
    static String inventoryservice_name =   System.getenv("inventoryservice_name");
    static String inventoryssl_server_cert_dn =   System.getenv("inventoryssl_server_cert_dn");
    static String orderhostname =   System.getenv("orderhostname");
    static String orderport =   System.getenv("orderport");
    static String orderservice_name =   System.getenv("orderservice_name");
    static String orderssl_server_cert_dn =   System.getenv("orderssl_server_cert_dn");

    static {
        System.setProperty("oracle.jdbc.fanEnabled", "false");
        System.out.println("ATPAQAdminResource.static cwalletobjecturi:" + cwalletobjecturi);
    }

    @Inject
    @Named("orderpdb")
    private DataSource orderpdbDataSource;

    @Inject
    @Named("inventorypdb")
    private DataSource inventorypdbDataSource;


    @Path("/testorderdatasource")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String testorderdatasource() {
        System.out.println("testorderdatasource...");
        try (Connection connection = orderpdbDataSource.getConnection()){
            System.out.println("ATPAQAdminResource.testdatasources orderpdbDataSource connection:" + connection );
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "success";
    }

    @Path("/testdatasources")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String testdatasources() {
        System.out.println("test datasources...");
        String resultString = "orderpdbDataSource...";
        try {
            orderpdbDataSource.getConnection();
            resultString += " connection successful";
            System.out.println(resultString);
        } catch (Exception e) {
            resultString += e;
            e.printStackTrace();
        }
        resultString += " inventorypdbDataSource...";
        try {
            inventorypdbDataSource.getConnection();
            resultString += " connection successful";
            System.out.println(resultString);
        } catch (Exception e) {
            resultString += e;
            e.printStackTrace();
        }
        return resultString;
    }

    @Path("/setupAll")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String setupAll() {
        String returnValue = "";
        try {
            System.out.println("setupAll ...");
            returnValue += propagationSetup.createUsers(orderpdbDataSource, inventorypdbDataSource);
            returnValue += propagationSetup.createInventoryTable(inventorypdbDataSource);
            returnValue += propagationSetup.createDBLinks(orderpdbDataSource, inventorypdbDataSource);
            returnValue += propagationSetup.setupTablesQueuesAndPropagation(orderpdbDataSource, inventorypdbDataSource,
                    true, true);
            return " result of setupAll : success... " + returnValue;
        } catch (Exception e) {
            e.printStackTrace();
            returnValue += e;
            return " result of setupAll : " + returnValue;
        }
    }

    @Path("/createUsers")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String createUsers() {
        String returnValue = "";
        try {
            System.out.println("createUsers ...");
            returnValue += propagationSetup.createUsers(orderpdbDataSource, inventorypdbDataSource);
            return " result of createUsers : " + returnValue;
        } catch (Exception e) {
            e.printStackTrace();
            returnValue += e;
            return " result of createUsers : " + returnValue;
        }
    }

    @Path("/createInventoryTable")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String createInventoryTable() {
        String returnValue = "";
        try {
            System.out.println("createInventoryTable ...");
            returnValue += propagationSetup.createInventoryTable(inventorypdbDataSource);
            return " result of createInventoryTable :  " + returnValue;
        } catch (Exception e) {
            e.printStackTrace();
            returnValue += e;
            return " result of createInventoryTable : " + returnValue;
        }
    }

    @Path("/createDBLinks")
    @GET
    @Produces(MediaType.TEXT_PLAIN) // does verifyDBLinks as well
    public String createDBLinks() {
        String returnValue = "";
        try {
            System.out.println("createDBLinks ...");
            returnValue += propagationSetup.createDBLinks(orderpdbDataSource, inventorypdbDataSource);
            return  returnValue;
        } catch (Exception e) {
            e.printStackTrace();
            returnValue += e;
            return "Exception during DBLinks create : " + returnValue;
        }
    }

    @Path("/verifyDBLinks")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String verifyDBLinks() {
        String returnValue = "";
        try {
            System.out.println("verifyDBLinks ...");
            returnValue += propagationSetup.verifyDBLinks(orderpdbDataSource, inventorypdbDataSource);
            return " result of verifyDBLinks :  " + returnValue;
        } catch (Exception e) {
            e.printStackTrace();
            returnValue += e;
            return " result of verifyDBLinks : " + returnValue;
        }
    }

    @Path("/setupTablesQueuesAndPropagation")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String setupTablesQueuesAndPropagation() {
        try {
            System.out.println("setupTablesQueuesAndPropagation ...");
            return propagationSetup.setupTablesQueuesAndPropagation(orderpdbDataSource, inventorypdbDataSource,
                    true, true);
        } catch (Exception e) {
            e.printStackTrace();
            return "Setup Tables Queues And Propagation failed : " + e;
        }
    }


    @Path("/setupLRA")
    @GET
    @Produces(MediaType.TEXT_PLAIN) 
    public String setupLRA() {
        return " result of setupTablesQueuesAndPropagationForLRA :  " +
                propagationSetup.setupTablesQueuesAndPropagationForLRA(orderpdbDataSource, inventorypdbDataSource);
    }

    private String setupTablesQueuesAndPropagationAndGetString(
            String returnValue, String s, boolean isSetupOrderToInventory, boolean isSetupInventoryToOrder, String resultDebugString) {
        try {
            System.out.println(s);
            returnValue += propagationSetup.setupTablesQueuesAndPropagation(orderpdbDataSource, inventorypdbDataSource,
                    isSetupOrderToInventory,  isSetupInventoryToOrder);
            return resultDebugString + returnValue;
        } catch (Exception e) {
            e.printStackTrace();
            returnValue += e;
            return resultDebugString + returnValue;
        }
    }

    @Path("/setupOrderToInventory")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String setupOrderToInventory() {
        String returnValue = "";
        return getString(returnValue, "setupOrderToInventory ...", true, false,
                " result of setupOrderToInventory :  ", " result of setupOrderToInventory : ");
    }

    @Path("/setupInventoryToOrder")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String setupInventoryToOrder() {
        String returnValue = "";
        return getString(returnValue, "setupInventoryToOrder ...", false, true,
                " result of setupInventoryToOrder :  ", " result of setupInventoryToOrder : ");
    }

    private String getString(String returnValue, String s, boolean b, boolean b2, String s2, String s3) {
        try {
            System.out.println(s);
            returnValue += propagationSetup.setupTablesQueuesAndPropagation(orderpdbDataSource, inventorypdbDataSource,  b, b2);
            return s2 + returnValue;
        } catch (Exception e) {
            e.printStackTrace();
            returnValue += e;
            return s3 + returnValue;
        }
    }

    @Path("/testInventoryToOrder")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String testInventoryToOrder() {
        String returnValue = "";
        try {
            System.out.println("testInventoryToOrder ...");
            returnValue += propagationSetup.testInventoryToOrder(orderpdbDataSource, inventorypdbDataSource);
            return " result of testInventoryToOrder :  " + returnValue;
        } catch (Exception e) {
            e.printStackTrace();
            returnValue += e;
            return " result of testInventoryToOrder : " + returnValue;
        }
    }

    @Path("/testOrderToInventory")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String testOrderToInventory() {
        String returnValue = "";
        try {
            System.out.println("testOrderToInventory ...");
            returnValue += propagationSetup.testOrderToInventory(orderpdbDataSource, inventorypdbDataSource);
            return " result of testOrderToInventory :  " + returnValue;
        } catch (Exception e) {
            e.printStackTrace();
            returnValue += e;
            return " result of testOrderToInventory : " + returnValue;
        }
    }

    @Path("/enablePropagation")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response enablePropagation() {
        System.out.println("ATPAQAdminResource.enablePropagation");
        String returnString =  propagationSetup.enablePropagation(
                orderpdbDataSource, orderuser, orderpw, orderQueueName, orderToInventoryLinkName);
        returnString +=  propagationSetup.enablePropagation(
                inventorypdbDataSource, inventoryuser, inventorypw, inventoryQueueName, inventoryToOrderLinkName);
        return Response.ok()
                .entity("enablePropagation:" + returnString)
                .build();
    }

    @Path("/enablePropagationInventoryToOrder")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response enablePropagationInventoryToOrder()  {
        System.out.println("ATPAQAdminResource.enablePropagationInventoryToOrder");
        String returnString =  propagationSetup.enablePropagation(
                inventorypdbDataSource, inventoryuser, inventorypw, inventoryQueueName, inventoryToOrderLinkName);
        return Response.ok()
                .entity("enablePropagationInventoryToOrder:" + returnString)
                .build();
    }

    @Path("/unschedulePropagation")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response unschedulePropagation()  {
        System.out.println("ATPAQAdminResource.unschedulePropagation");
        String returnString =  propagationSetup.unschedulePropagation(
                orderpdbDataSource, orderuser, orderpw, orderQueueName, orderToInventoryLinkName);
        returnString +=  propagationSetup.unschedulePropagation(
                inventorypdbDataSource, inventoryuser, inventorypw, inventoryQueueName, inventoryToOrderLinkName);
        return Response.ok()
                .entity("unschedulePropagation:" + returnString)
                .build();
    }

    @Path("/deleteUsers")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String deleteUsers() {
        String returnValue = "";
        try {
            System.out.println("deleteUsers ...");
            returnValue += propagationSetup.deleteUsers(orderpdbDataSource, inventorypdbDataSource);
            return " result of deleteUsers : " + returnValue;
        } catch (Exception e) {
            e.printStackTrace();
            returnValue += e;
            return " result of deleteUsers : " + returnValue;
        }
    }

    @Path("/getConnectionMetaData")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response getConnectionMetaData() throws SQLException {
        Response returnValue;
        try (Connection connection = orderpdbDataSource.getConnection()) {
            returnValue = Response.ok()
                    .entity("Connection obtained successfully metadata:" + connection.getMetaData())
                    .build();
        }
        return returnValue;
    }

    @Path("/createLRATestQueue")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response createLRATestQueue() throws JMSException, AQException {
        propagationSetup.createQueue(orderpdbDataSource, orderuser, orderpw, "LRATESTQUEUE");
        propagationSetup.createQueue(orderpdbDataSource, orderuser, orderpw, "HELIDONLRAQUEUE");
        return Response.ok()
                        .entity("createLRATestQueue")
                        .build();
    }


    /**
     * This is for the propagation case where there are mutliple (P)DBs
     */
    @Path("/sendTestMessageToTopic")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response sendTestMessageToTopic(@QueryParam("LRAType") String lratype) throws JMSException {
        TopicSession session = null;
        TopicConnectionFactory q_cf = AQjmsFactory.getTopicConnectionFactory(orderpdbDataSource);
        try (TopicConnection q_conn = q_cf.createTopicConnection()){
            session = q_conn.createTopicSession(true, Session.CLIENT_ACKNOWLEDGE);
            Topic topic = ((AQjmsSession) session).getTopic(orderuser, orderQueueName);
            System.out.println(" about to sendTestMessageToTopic to topic:" + topic);
            TopicPublisher publisher = session.createPublisher(topic);
            TextMessage objmsg = session.createTextMessage();
            objmsg.setStringProperty("LRAType", lratype);
            objmsg.setIntProperty("Id", 1);
            objmsg.setIntProperty("Priority", 2);
            objmsg.setText("test message");
            objmsg.setJMSCorrelationID("" + 1);
            objmsg.setJMSPriority(2);
            // publish(Topic topic, Message message, int deliveryMode, int priority, long timeToLive)
            publisher.publish(topic, objmsg, DeliveryMode.PERSISTENT, 2, AQjmsConstants.EXPIRATION_NEVER);
            session.commit();
            System.out.println("sendTestMessageToTopic complete");
            return Response.ok()
                    .entity("sendTestMessageToTopic complete")
                    .build();
        }
    }

    @Path("/sendTestMessageToQueue")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response sendTestMessageToQueue(@QueryParam("LRAType") String lratype) throws JMSException {
        QueueSession session;
        QueueConnectionFactory q_cf = AQjmsFactory.getQueueConnectionFactory(orderpdbDataSource);
        try (QueueConnection q_conn = q_cf.createQueueConnection()){
            session = q_conn.createQueueSession(true, Session.CLIENT_ACKNOWLEDGE);
            Queue queue = ((AQjmsSession) session).getQueue(orderuser, lraTestQueueName);
            System.out.println(" about to sendTestMessageToQueue to queue:" + queue);
            MessageProducer producer = session.createProducer(queue);
            TextMessage objmsg = session.createTextMessage();
            objmsg.setStringProperty("LRAType", lratype);
            objmsg.setIntProperty("Id", 1);
            objmsg.setIntProperty("Priority", 2);
            objmsg.setText("test message");
            objmsg.setJMSCorrelationID("" + 1);
            objmsg.setJMSPriority(2);
            producer.send(objmsg);
            session.commit();
            System.out.println("sendTestMessageToQueue complete");
            return Response.ok()
                    .entity("sendTestMessageToQueue complete")
                    .build();
        }
    }

}

