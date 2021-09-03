/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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


alter session set "_ORACLE_SCRIPT"= true;
create user frank identified by frank;
grant dba to frank;

grant execute on dbms_aq to frank;
grant execute on dbms_aqadm to frank;
grant execute on dbms_aqin to frank;

CREATE OR REPLACE PROCEDURE create_queue(queueName IN VARCHAR2, qType IN VARCHAR2) IS
BEGIN
    dbms_aqadm.create_queue_table('FRANK.'||queueName||'_TAB', qType);
    dbms_aqadm.create_queue('FRANK.'||queueName,'FRANK.'||queueName||'_TAB');
    dbms_aqadm.start_queue('FRANK.'||queueName);
END;
/

-- Setup example AQ queues FRANK.EXAMPLE_QUEUE_1, FRANK.EXAMPLE_QUEUE_2, FRANK.EXAMPLE_QUEUE_3
begin
    CREATE_QUEUE('example_queue_1', 'SYS.AQ$_JMS_TEXT_MESSAGE');
    CREATE_QUEUE('example_queue_2', 'SYS.AQ$_JMS_TEXT_MESSAGE');
    CREATE_QUEUE('example_queue_3', 'SYS.AQ$_JMS_TEXT_MESSAGE');
    CREATE_QUEUE('example_queue_bytes', 'SYS.AQ$_JMS_BYTES_MESSAGE');
    CREATE_QUEUE('example_queue_map', 'SYS.AQ$_JMS_MAP_MESSAGE');
end;
/

-- Setup example table
CREATE TABLE FRANK.MESSAGE_LOG (
    id NUMBER(15) PRIMARY KEY,
    message VARCHAR2(255) NOT NULL,
    insert_date DATE DEFAULT (sysdate));
COMMENT ON TABLE FRANK.MESSAGE_LOG IS 'Manually logged messages';

CREATE SEQUENCE FRANK.MSG_LOG_SEQ START WITH 1;

CREATE OR REPLACE TRIGGER MESSAGE_LOG_ID
    BEFORE INSERT ON FRANK.MESSAGE_LOG
    FOR EACH ROW

BEGIN
    SELECT FRANK.MSG_LOG_SEQ.NEXTVAL
    INTO   :new.id
    FROM   dual;
END;
/
