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

#
# WebLogic on Docker Default Domain
#
# Domain, as defined in DOMAIN_NAME, will be created in this script. Name defaults to 'base_domain'.
#
# Since : October, 2014
# Author: monica.riccelli@oracle.com
# ==============================================
domain_name  = os.environ.get("DOMAIN_NAME", "base_domain")
admin_name  = os.environ.get("ADMIN_NAME", "AdminServer")
admin_listen_port   = int(os.environ.get("ADMIN_LISTEN_PORT", "7001"))
domain_path  = '/u01/oracle/user_projects/domains/%s' % domain_name
production_mode = os.environ.get("PRODUCTION_MODE", "prod")
administration_port_enabled = os.environ.get("ADMINISTRATION_PORT_ENABLED", "true")
administration_port = int(os.environ.get("ADMINISTRATION_PORT", "9002"))

print('domain_name                 : [%s]' % domain_name);
print('admin_listen_port           : [%s]' % admin_listen_port);
print('domain_path                 : [%s]' % domain_path);
print('production_mode             : [%s]' % production_mode);
print('admin name                  : [%s]' % admin_name);
print('administration_port_enabled : [%s]' % administration_port_enabled);
print('administration_port         : [%s]' % administration_port);

# Open default domain template
# ============================
readTemplate("/u01/oracle/wlserver/common/templates/wls/wls.jar")

set('Name', domain_name)
setOption('DomainName', domain_name)

# Set Administration Port 
# =======================
if administration_port_enabled != "false":
   set('AdministrationPort', administration_port)
   set('AdministrationPortEnabled', 'false')

# Disable Admin Console
# --------------------
# cmo.setConsoleEnabled(false)

# Configure the Administration Server and SSL port.
# =================================================
cd('/Servers/AdminServer')
set('Name', admin_name)
set('ListenAddress', '')
set('ListenPort', admin_listen_port)
if administration_port_enabled != "false":
   create(admin_name, 'SSL')
   cd('SSL/' + admin_name)
   set('Enabled', 'True')

# Define the user password for weblogic
# =====================================
cd(('/Security/%s/User/weblogic') % domain_name)
cmo.setName(username)
cmo.setPassword(password)

# Write the domain and close the domain template
# ==============================================
setOption('OverwriteDomain', 'true')
setOption('ServerStartMode',production_mode)

# Create Node Manager
# ===================
#cd('/NMProperties')
#set('ListenAddress','')
#set('ListenPort',5556)
#set('CrashRecoveryEnabled', 'true')
#set('NativeVersionEnabled', 'true')
#set('StartScriptEnabled', 'false')
#set('SecureListener', 'false')
#set('LogLevel', 'FINEST')

# Set the Node Manager user name and password 
# ===========================================
#cd('/SecurityConfiguration/%s' % domain_name)
#set('NodeManagerUsername', username)
#set('NodeManagerPasswordEncrypted', password)

# Write Domain
# ============
writeDomain(domain_path)
closeTemplate()

# Exit WLST
# =========
exit()
