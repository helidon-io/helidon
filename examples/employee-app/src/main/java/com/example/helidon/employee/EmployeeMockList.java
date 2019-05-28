/* Copyright 2019 (c) Oracle and/or its affiliates. All rights reserved. */
package com.example.helidon.employee;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

public class EmployeeMockList {

	private static final CopyOnWriteArrayList<Employee> eList = new CopyOnWriteArrayList<Employee>();
	
    static {
    	JsonbConfig config = new JsonbConfig().withFormatting(Boolean.TRUE);

    	Jsonb jsonb = JsonbBuilder.create(config);
    	
        String jsonString = "[{\"id\":\"" + "100" +"\",\"firstName\":\"Hugh\",\"lastName\":\"Jast\",\"email\":\"Hugh.Jast@example.com\",\"phone\":\"730-715-4446\",\"birthDate\":\"1970-11-28T08:28:48.078Z\",\"title\":\"National Data Strategist\",\"department\":\"Mobility\"},"
        		+ "{\"id\":\"" + "101" + "\",\"firstName\":\"Toy\",\"lastName\":\"Herzog\",\"email\":\"Toy.Herzog@example.com\",\"phone\":\"769-569-1789\",\"birthDate\":\"1961-08-08T11:39:27.324Z\",\"title\":\"Dynamic Operations Manager\",\"department\":\"Paradigm\"},"
        		+ "{\"id\":\"" + "102" + "\",\"firstName\":\"Reed\",\"lastName\":\"Hahn\",\"email\":\"Reed.Hahn@example.com\",\"phone\":\"429-071-2018\",\"birthDate\":\"1977-02-05T19:18:57.407Z\",\"title\":\"Future Directives Facilitator\",\"department\":\"Quality\"},"
        		+ "{\"id\":\"" + "103" + "\",\"firstName\":\"Novella\",\"lastName\":\"Bahringer\",\"email\":\"Novella.Bahringer@example.com\",\"phone\":\"293-596-3547\",\"birthDate\":\"1961-07-25T10:59:55.485Z\",\"title\":\"Principal Factors Architect\",\"department\":\"Division\"},"
        		+ "{\"id\":\"" + "104" + "\",\"firstName\":\"Zora\",\"lastName\":\"Sawayn\",\"email\":\"Zora.Sawayn@example.com\",\"phone\":\"923-814-0502\",\"birthDate\":\"1978-03-18T17:00:12.938Z\",\"title\":\"Dynamic Marketing Designer\",\"department\":\"Security\"},"
        		+ "{\"id\":\""+ "105" + "\",\"firstName\":\"Cordia\",\"lastName\":\"Willms\",\"email\":\"Cordia.Willms@example.com\",\"phone\":\"778-821-3941\",\"birthDate\":\"1989-03-31T23:03:51.599Z\",\"title\":\"Human Division Representative\",\"department\":\"Optimization\"},"
        		+ "{\"id\":\""+ "106" + "\",\"firstName\":\"Clair\",\"lastName\":\"Bartoletti\",\"email\":\"Clair.Bartoletti@example.com\",\"phone\":\"647-896-8993\",\"birthDate\":\"1986-01-04T01:19:47.243Z\",\"title\":\"Customer Marketing Executive\",\"department\":\"Factors\"},"
        		+ "{\"id\":\""+ "107" + "\",\"firstName\":\"Joe\",\"lastName\":\"Beahan\",\"email\":\"Joe.Beahan@example.com\",\"phone\":\"548-890-6181\",\"birthDate\":\"1990-07-11T23:42:02.063Z\",\"title\":\"District Group Specialist\",\"department\":\"Program\"},"
        		+ "{\"id\":\""+ "108" + "\",\"firstName\":\"Daphney\",\"lastName\":\"Feest\",\"email\":\"Daphney.Feest@example.com\",\"phone\":\"143-967-7041\",\"birthDate\":\"1984-03-26T16:32:41.332Z\",\"title\":\"Dynamic Mobility Consultant\",\"department\":\"Metrics\"},"
        		+ "{\"id\":\""+ "109" + "\",\"firstName\":\"Janessa\",\"lastName\":\"Wyman\",\"email\":\"Janessa.Wyman@example.com\",\"phone\":\"498-186-9009\",\"birthDate\":\"1985-09-06T10:34:05.540Z\",\"title\":\"Investor Brand Associate\",\"department\":\"Markets\"},"
        		+ "{\"id\":\""+ "110" + "\",\"firstName\":\"Mara\",\"lastName\":\"Roob\",\"email\":\"Mara.Roob@example.com\",\"phone\":\"962-540-9884\",\"birthDate\":\"1975-05-11T09:45:58.248Z\",\"title\":\"Legacy Assurance Engineer\",\"department\":\"Usability\"},"
        		+ "{\"id\":\""+ "111" + "\",\"firstName\":\"Brennon\",\"lastName\":\"Bernhard\",\"email\":\"Brennon.Bernhard@example.com\",\"phone\":\"395-224-9853\",\"birthDate\":\"1963-12-05T20:32:26.668Z\",\"title\":\"Product Web Officer\",\"department\":\"Interactions\"},"
        		+ "{\"id\":\""+ "112" + "\",\"firstName\":\"Amya\",\"lastName\":\"Bernier\",\"email\":\"Amya.Bernier@example.com\",\"phone\":\"563-562-4171\",\"birthDate\":\"1972-06-23T15:25:40.799Z\",\"title\":\"Global Tactics Planner\",\"department\":\"Program\"},"
        		+ "{\"id\":\""+ "113" + "\",\"firstName\":\"Claud\",\"lastName\":\"Boehm\",\"email\":\"Claud.Boehm@example.com\",\"phone\":\"089-073-7399\",\"birthDate\":\"1965-02-27T14:09:28.325Z\",\"title\":\"National Marketing Associate\",\"department\":\"Directives\"},"
        		+ "{\"id\":\""+ "114" + "\",\"firstName\":\"Nyah\",\"lastName\":\"Schowalter\",\"email\":\"Nyah.Schowalter@example.com\",\"phone\":\"823-063-7120\",\"birthDate\":\"1969-02-19T19:34:55.044Z\",\"title\":\"Dynamic Communications Assistant\",\"department\":\"Metrics\"},"
        		+ "{\"id\":\""+ "115" + "\",\"firstName\":\"Imogene\",\"lastName\":\"Bernhard\",\"email\":\"Imogene.Bernhard@example.com\",\"phone\":\"747-970-6062\",\"birthDate\":\"1958-02-09T15:03:53.070Z\",\"title\":\"Dynamic Assurance Supervisor\",\"department\":\"Paradigm\"},"
        		+ "{\"id\":\""+ "116" + "\",\"firstName\":\"Chanel\",\"lastName\":\"Kuhlman\",\"email\":\"Chanel.Kuhlman@example.com\",\"phone\":\"882-145-9513\",\"birthDate\":\"1985-03-03T18:12:15.936Z\",\"title\":\"District Paradigm Representative\",\"department\":\"Integration\"},"
        		+ "{\"id\":\""+ "117" + "\",\"firstName\":\"Cierra\",\"lastName\":\"Morar\",\"email\":\"Cierra.Morar@example.com\",\"phone\":\"273-607-2209\",\"birthDate\":\"1965-01-25T20:17:32.836Z\",\"title\":\"Dynamic Data Planner\",\"department\":\"Paradigm\"},"
        		+ "{\"id\":\""+ "118" + "\",\"firstName\":\"Faye\",\"lastName\":\"Grimes\",\"email\":\"Faye.Grimes@example.com\",\"phone\":\"750-139-1344\",\"birthDate\":\"1962-08-21T07:52:29.284Z\",\"title\":\"Central Applications Analyst\",\"department\":\"Tactics\"},"
        		+ "{\"id\":\""+ "119" + "\",\"firstName\":\"Doyle\",\"lastName\":\"Rohan\",\"email\":\"Doyle.Rohan@example.com\",\"phone\":\"093-457-5621\",\"birthDate\":\"1991-11-29T17:19:01.536Z\",\"title\":\"Corporate Accountability Supervisor\",\"department\":\"Applications\"},"
        		+ "{\"id\":\""+ "120" + "\",\"firstName\":\"Jonathan\",\"lastName\":\"Barrows\",\"email\":\"Jonathan.Barrows@example.com\",\"phone\":\"262-503-2161\",\"birthDate\":\"1963-12-15T07:40:48.738Z\",\"title\":\"Regional Configuration Liason\",\"department\":\"Implementation\"},"
        		+ "{\"id\":\""+ "121" + "\",\"firstName\":\"Myriam\",\"lastName\":\"Luettgen\",\"email\":\"Myriam.Luettgen@example.com\",\"phone\":\"951-924-8295\",\"birthDate\":\"1962-02-08T10:09:10.361Z\",\"title\":\"Central Functionality Specialist\",\"department\":\"Accountability\"},"
        		+ "{\"id\":\""+ "122" + "\",\"firstName\":\"Johnnie\",\"lastName\":\"Schiller\",\"email\":\"Johnnie.Schiller@example.com\",\"phone\":\"534-025-2200\",\"birthDate\":\"1965-04-11T02:03:48.333Z\",\"title\":\"Principal Creative Developer\",\"department\":\"Interactions\"},"
        		+ "{\"id\":\""+ "123" + "\",\"firstName\":\"Laila\",\"lastName\":\"White\",\"email\":\"Laila.White@example.com\",\"phone\":\"468-939-2298\",\"birthDate\":\"1956-01-04T02:01:09.619Z\",\"title\":\"Corporate Optimization Engineer\",\"department\":\"Assurance\"},"
        		+ "{\"id\":\""+ "124" + "\",\"firstName\":\"Alessandra\",\"lastName\":\"Becker\",\"email\":\"Alessandra.Becker@example.com\",\"phone\":\"081-724-0866\",\"birthDate\":\"1984-08-12T05:32:50.509Z\",\"title\":\"Central Identity Associate\",\"department\":\"Quality\"},"
        		+ "{\"id\":\""+ "125" + "\",\"firstName\":\"Shannon\",\"lastName\":\"McCullough\",\"email\":\"Shannon.McCullough@example.com\",\"phone\":\"101-995-1089\",\"birthDate\":\"1989-02-25T07:47:10.774Z\",\"title\":\"Global Data Engineer\",\"department\":\"Division\"},"
        		+ "{\"id\":\""+ "126" + "\",\"firstName\":\"Garnet\",\"lastName\":\"Labadie\",\"email\":\"Garnet.Labadie@example.com\",\"phone\":\"147-954-6624\",\"birthDate\":\"1990-01-01T05:31:28.531Z\",\"title\":\"Senior Communications Producer\",\"department\":\"Program\"},"
        		+ "{\"id\":\""+ "127" + "\",\"firstName\":\"Mark\",\"lastName\":\"Graham\",\"email\":\"Mark.Graham@example.com\",\"phone\":\"462-746-7388\",\"birthDate\":\"1991-08-23T11:17:47.950Z\",\"title\":\"Legacy Directives Agent\",\"department\":\"Assurance\"},"
        		+ "{\"id\":\""+ "128" + "\",\"firstName\":\"Tristin\",\"lastName\":\"Bayer\",\"email\":\"Tristin.Bayer@example.com\",\"phone\":\"882-044-3996\",\"birthDate\":\"1964-03-26T17:49:29.143Z\",\"title\":\"Internal Marketing Officer\",\"department\":\"Intranet\"},"
        		+ "{\"id\":\""+ "129" + "\",\"firstName\":\"Maurice\",\"lastName\":\"Renner\",\"email\":\"Maurice.Renner@example.com\",\"phone\":\"383-435-0943\",\"birthDate\":\"1973-11-05T18:41:06.678Z\",\"title\":\"National Accountability Planner\",\"department\":\"Accounts\"},"
        		+ "{\"id\":\""+ "130" + "\",\"firstName\":\"Preston\",\"lastName\":\"Stark\",\"email\":\"Preston.Stark@example.com\",\"phone\":\"080-698-9552\",\"birthDate\":\"1994-02-02T10:24:40.312Z\",\"title\":\"Corporate Program Orchestrator\",\"department\":\"Integration\"},"
        		+ "{\"id\":\""+ "131" + "\",\"firstName\":\"Mabelle\",\"lastName\":\"Herman\",\"email\":\"Mabelle.Herman@example.com\",\"phone\":\"778-672-2787\",\"birthDate\":\"1956-11-30T09:45:45.699Z\",\"title\":\"Human Identity Officer\",\"department\":\"Integration\"},"
        		+ "{\"id\":\""+ "132" + "\",\"firstName\":\"Juvenal\",\"lastName\":\"Swaniawski\",\"email\":\"Juvenal.Swaniawski@example.com\",\"phone\":\"349-906-2745\",\"birthDate\":\"1963-11-17T06:51:48.742Z\",\"title\":\"Future Program Planner\",\"department\":\"Response\"},"
        		+ "{\"id\":\""+ "133" + "\",\"firstName\":\"Rachelle\",\"lastName\":\"Okuneva\",\"email\":\"Rachelle.Okuneva@example.com\",\"phone\":\"134-565-3868\",\"birthDate\":\"1992-05-27T04:39:16.831Z\",\"title\":\"District Creative Architect\",\"department\":\"Paradigm\"},"
        		+ "{\"id\":\""+ "134" + "\",\"firstName\":\"Macey\",\"lastName\":\"Weissnat\",\"email\":\"Macey.Weissnat@example.com\",\"phone\":\"210-461-3749\",\"birthDate\":\"1978-06-24T06:38:18.125Z\",\"title\":\"Product Accountability Facilitator\",\"department\":\"Data\"},"
        		+ "{\"id\":\""+ "135" + "\",\"firstName\":\"Ena\",\"lastName\":\"Gerlach\",\"email\":\"Ena.Gerlach@example.com\",\"phone\":\"429-925-7634\",\"birthDate\":\"1976-04-09T22:36:01.397Z\",\"title\":\"Human Tactics Agent\",\"department\":\"Creative\"},"
        		+ "{\"id\":\""+ "136" + "\",\"firstName\":\"Darrick\",\"lastName\":\"Deckow\",\"email\":\"Darrick.Deckow@example.com\",\"phone\":\"549-222-4121\",\"birthDate\":\"1956-10-25T01:05:22.507Z\",\"title\":\"Lead Solutions Producer\",\"department\":\"Metrics\"},"
        		+ "{\"id\":\""+ "137" + "\",\"firstName\":\"Palma\",\"lastName\":\"Torp\",\"email\":\"Palma.Torp@example.com\",\"phone\":\"346-556-3517\",\"birthDate\":\"1967-06-24T15:42:05.485Z\",\"title\":\"Product Infrastructure Consultant\",\"department\":\"Response\"},"
        		+ "{\"id\":\""+ "138" + "\",\"firstName\":\"Lucious\",\"lastName\":\"Steuber\",\"email\":\"Lucious.Steuber@example.com\",\"phone\":\"977-372-2840\",\"birthDate\":\"1961-11-24T16:19:53.598Z\",\"title\":\"District Creative Supervisor\",\"department\":\"Mobility\"},"
        		+ "{\"id\":\""+ "139" + "\",\"firstName\":\"Kacey\",\"lastName\":\"Kilback\",\"email\":\"Kacey.Kilback@example.com\",\"phone\":\"268-777-2011\",\"birthDate\":\"1957-09-06T10:07:09.719Z\",\"title\":\"Corporate Mobility Agent\",\"department\":\"Infrastructure\"}]";
        eList.addAll(jsonb.fromJson(jsonString, new CopyOnWriteArrayList<Employee>(){}.getClass().getGenericSuperclass()));
    }

    private EmployeeMockList() {
  
    }

    // Get thread safe ArrayList from here
    public static CopyOnWriteArrayList<Employee> getInstance() {
        return eList;
    }

}
