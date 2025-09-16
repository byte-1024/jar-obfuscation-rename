package com.example;

import java.io.File;
import java.io.IOException;

public class Start {
    public static void main(String[] args) {
        try {
            JEBJsonParser parser = new JEBJsonParser(new File("C:\\JEB\\bin\\codedata.txt")); 
            
            File jarFile = new File("C:\\target.jar");
            JarRenamer renamer = new JarRenamer(
                jarFile,
                parser.classNodes,
                parser.methodNodes,
                parser.fieldNodes,
                null);
            File outputFile = renamer.execute();
            System.out.println("Output file: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
