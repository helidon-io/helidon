--
-- Copyright (c) 2026 Oracle and/or its affiliates.
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

SET ECHO OFF
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE

-- Allow bursts of short-lived, unpooled connections between listener load updates.
-- The image contains a prebuilt database, so this static parameter needs a restart.
ALTER SYSTEM SET PROCESSES = 400 SCOPE = SPFILE;
SHUTDOWN IMMEDIATE;
STARTUP;
ALTER PLUGGABLE DATABASE ALL OPEN;
ALTER SYSTEM REGISTER;

DECLARE
    process_limit NUMBER;
BEGIN
    SELECT TO_NUMBER(value) INTO process_limit FROM v$parameter WHERE name = 'processes';
    IF process_limit < 400 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Oracle test database requires PROCESSES >= 400');
    END IF;
END;
/

PROMPT HELIDON ORACLE TEST DATABASE READY
EXIT SUCCESS
