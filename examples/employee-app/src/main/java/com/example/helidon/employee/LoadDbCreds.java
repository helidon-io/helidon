/* Copyright 2019 Oracle and/or its affiliates. All rights reserved. */
package com.example.helidon.employee;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class LoadDbCreds {
    private void LoadsDbCreds(){ }

    public static Properties getProperties(){

        String curDir = System.getProperty("user.dir");
        Path filePath = Paths.get(curDir, "resources/DbCreds.properties");
        Properties myCreds = new Properties();
        try (InputStream is = Files.newInputStream(filePath)) {
            myCreds.load(is);
        } catch (IOException io) {
            System.out.println("IO Error: " + io.getMessage());
        }
        return myCreds;
    }

}