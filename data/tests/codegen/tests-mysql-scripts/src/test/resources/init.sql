--
-- Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

CREATE TABLE TEAM (ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(128) NOT NULL);
CREATE TABLE KIND (ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(32) NOT NULL);
CREATE TABLE KEEPER (ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(128) NOT NULL, TEAM_ID INT NOT NULL REFERENCES TEAM(id));
CREATE TABLE CRITTER (ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(128) NOT NULL, KEEPER_ID INTEGER NOT NULL REFERENCES KEEPER(ID));
INSERT INTO KIND (id, name) VALUES(1, "Normal");
INSERT INTO KIND (id, name) VALUES(2, "Fighting");
INSERT INTO KIND (id, name) VALUES(3, "Flying");
INSERT INTO KIND (id, name) VALUES(4, "Poison");
INSERT INTO KIND (id, name) VALUES(5, "Ground");
INSERT INTO KIND (id, name) VALUES(6, "Rock");
INSERT INTO KIND (id, name) VALUES(7, "Bug");
INSERT INTO KIND (id, name) VALUES(8, "Ghost");
INSERT INTO KIND (id, name) VALUES(9, "Steel");
INSERT INTO KIND (id, name) VALUES(10, "Fire");
INSERT INTO KIND (id, name) VALUES(11, "Water");
INSERT INTO KIND (id, name) VALUES(12, "Grass");
INSERT INTO KIND (id, name) VALUES(13, "Electric");
INSERT INTO KIND (id, name) VALUES(14, "Psychic");
INSERT INTO KIND (id, name) VALUES(15, "Ice");
INSERT INTO KIND (id, name) VALUES(16, "Dragon");
INSERT INTO KIND (id, name) VALUES(17, "Dark");
INSERT INTO KIND (id, name) VALUES(18, "Fairy");
