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




**AQ without propagation (single DB)

**AQ with propagation
curl localhost:8091/setupLRA


define edn_user=dev12c_soainfra 
define topic=EDN_AQJMS_CUST_TOPIC 
define topic_table=EDN_AQJMS_CUST_TOPIC_TBL

  
begin 
  DBMS_AQADM.stop_queue(queue_name => ‘&edn_user..&topic’); 
  DBMS_AQADM.drop_queue(queue_name => ‘&edn_user..&topic’); 
  DBMS_AQADM.drop_queue_table(queue_table => ‘&edn_user..&topic_table’); 
end; 
/ 
begin 
  dbms_aqadm.create_queue_table(queue_table => ‘&edn_user..&topic_table’, 
                                queue_payload_type => ‘SYS.AQ$_JMS_MESSAGE’, 
                                multiple_consumers => true); 
  dbms_aqadm.create_queue(queue_name => ‘&edn_user..&topic’, 
                          queue_table => ‘&edn_user..&topic_table’, 
                          max_retries => 256); 
  dbms_aqadm.start_queue(queue_name        =>       ‘&edn_user..&topic’); 
end; 
/ 
commit;