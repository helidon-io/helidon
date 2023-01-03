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

# ORACLE DOCKERFILES PROJECT
# --------------------------
# This docker file is customized, originaly taken from https://github.com/oracle/docker-images
# and extends the Oracle WebLogic image by creating a sample domain.
#
# Base image is available at https://container-registry.oracle.com/
#
FROM container-registry.oracle.com/middleware/weblogic:14.1.1.0-dev-11

ENV ORACLE_HOME=/u01/oracle \
    USER_MEM_ARGS="-Djava.security.egd=file:/dev/./urandom" \
    SCRIPT_FILE=/u01/oracle/createAndStartEmptyDomain.sh \
    HEALTH_SCRIPT_FILE=/u01/oracle/get_healthcheck_url.sh \
    PATH=$PATH:${JAVA_HOME}/bin:/u01/oracle/oracle_common/common/bin:/u01/oracle/wlserver/common/bin

ENV DOMAIN_NAME="${DOMAIN_NAME:-base_domain}" \
    ADMIN_LISTEN_PORT="${ADMIN_LISTEN_PORT:-7001}"  \
    ADMIN_NAME="${ADMIN_NAME:-AdminServer}" \
    DEBUG_FLAG=true \
    PRODUCTION_MODE=dev \
    ADMINISTRATION_PORT_ENABLED="${ADMINISTRATION_PORT_ENABLED:-true}" \
    ADMINISTRATION_PORT="${ADMINISTRATION_PORT:-9002}"

COPY container-scripts/createAndStartEmptyDomain.sh container-scripts/get_healthcheck_url.sh /u01/oracle/
COPY container-scripts/create-wls-domain.py container-scripts/setupTestJMSQueue.py /u01/oracle/
COPY properties/domain.properties /u01/oracle/properties/

USER root

RUN chmod +xr $SCRIPT_FILE $HEALTH_SCRIPT_FILE && \
    chown oracle:root $SCRIPT_FILE /u01/oracle/create-wls-domain.py $HEALTH_SCRIPT_FILE

USER oracle

HEALTHCHECK --start-period=10s --timeout=30s --retries=3 CMD curl -k -s --fail `$HEALTH_SCRIPT_FILE` || exit 1
WORKDIR ${ORACLE_HOME}

CMD ["/u01/oracle/createAndStartEmptyDomain.sh"]