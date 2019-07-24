/*
  Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/
CREATE TABLE AUTHOR (
   ID INT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   NAME VARCHAR(64) NOT NULL
);

CREATE TABLE MICROBLOG (
   ID INT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   AUTHOR_ID INT NOT NULL,
   NAME VARCHAR(64) NOT NULL,
   FOREIGN KEY (AUTHOR_ID) REFERENCES AUTHOR(ID)
);

CREATE TABLE CHIRP (
   ID INT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   MICROBLOG_ID INT NOT NULL,
   CONTENT VARCHAR(140) NOT NULL,
   FOREIGN KEY (MICROBLOG_ID) REFERENCES MICROBLOG(ID)
);
