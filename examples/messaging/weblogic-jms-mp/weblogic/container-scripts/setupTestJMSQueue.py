#
# Copyright (c) 2022 Oracle and/or its affiliates.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import os.path
import sys

System.setProperty("weblogic.security.SSL.ignoreHostnameVerification", "true")

connect("admin","Welcome1","t3://localhost:7001")
adm_name=get('AdminServerName')
sub_deployment_name="TestJMSSubdeployment"
jms_module_name="TestJMSModule"
queue_name="TestQueue"
factory_name="TestConnectionFactory"
jms_server_name="TestJMSServer"


def createJMSServer(adm_name, jms_server_name):
        cd('/JMSServers')
        if (len(ls(returnMap='true')) == 0):
                print 'No JMS Server found, creating ' +  jms_server_name
                cd('/')
                cmo.createJMSServer(jms_server_name)
                cd('/JMSServers/'+jms_server_name)
                cmo.addTarget(getMBean("/Servers/" + adm_name))


def createJMSModule(jms_module_name, adm_name, sub_deployment_name):
        print "Creating JMS module " + jms_module_name
        cd('/JMSServers')
        jms_servers=ls(returnMap='true')
        cd('/')
        module = create(jms_module_name, "JMSSystemResource")
        module.addTarget(getMBean("Servers/"+adm_name))
        cd('/SystemResources/'+jms_module_name)
        module.createSubDeployment(sub_deployment_name)
        cd('/SystemResources/'+jms_module_name+'/SubDeployments/'+sub_deployment_name)

        list=[]
        for i in jms_servers:
                list.append(ObjectName(str('com.bea:Name='+i+',Type=JMSServer')))
        set('Targets',jarray.array(list, ObjectName))

def getJMSModulePath(jms_module_name):
        jms_module_path = "/JMSSystemResources/"+jms_module_name+"/JMSResource/"+jms_module_name
        return jms_module_path

def createJMSQueue(jms_module_name,jms_queue_name):
        print "Creating JMS queue " + jms_queue_name
        jms_module_path = getJMSModulePath(jms_module_name)
        cd(jms_module_path)
        cmo.createQueue(jms_queue_name)
        cd(jms_module_path+'/Queues/'+jms_queue_name)
        cmo.setJNDIName("jms/" + jms_queue_name)
        cmo.setSubDeploymentName(sub_deployment_name)

def createDistributedJMSQueue(jms_module_name,jms_queue_name):
        print "Creating distributed JMS queue " + jms_queue_name
        jms_module_path = getJMSModulePath(jms_module_name)
        cd(jms_module_path)
        cmo.createDistributedQueue(jms_queue_name)
        cd(jms_module_path+'/DistributedQueues/'+jms_queue_name)
        cmo.setJNDIName("jms/" + jms_queue_name)

def addMemberQueue(udd_name,queue_name):
        jms_module_path = getJMSModulePath(jms_module_name)
        cd(jms_module_path+'/DistributedQueues/'+udd_name)
        cmo.setLoadBalancingPolicy('Round-Robin')
        cmo.createDistributedQueueMember(queue_name)

def createJMSFactory(jms_module_name,jms_fact_name):
        print "Creating JMS connection factory " + jms_fact_name
        jms_module_path = getJMSModulePath(jms_module_name)
        cd(jms_module_path)
        cmo.createConnectionFactory(jms_fact_name)
        cd(jms_module_path+'/ConnectionFactories/'+jms_fact_name)
        cmo.setJNDIName("jms/" + jms_fact_name)
        cmo.setSubDeploymentName(sub_deployment_name)



edit()
startEdit()

print "Server name: "+adm_name

createJMSServer(adm_name,jms_server_name)
createJMSModule(jms_module_name,adm_name,sub_deployment_name)
createJMSFactory(jms_module_name,factory_name)
createJMSQueue(jms_module_name,queue_name)

### Unified Distributed Destinations(UDD) example
createDistributedJMSQueue(jms_module_name,"udd_queue")
# Normally member queues would be in different sub-deployments
createJMSQueue(jms_module_name,"ms1@udd_queue")
createJMSQueue(jms_module_name,"ms2@udd_queue")
addMemberQueue("udd_queue", "ms1@udd_queue")
addMemberQueue("udd_queue", "ms2@udd_queue")

save()
activate(block="true")
disconnect()