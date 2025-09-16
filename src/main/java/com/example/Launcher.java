package com.example;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Launcher {
    public static List<Node> classNodes = new ArrayList<>();
    public static List<Node> methodNodes = new ArrayList<>();
    public static List<Node> fieldNodes = new ArrayList<>();

    public static void main(String[] args) {
        try {
            readJEBJSON(new File("C:\\xxx\\JEB\\bin\\codedata.txt"));

            File jarFile = new File("C:\\target.jar");
            JarRenamer renamerService = new JarRenamer(
                jarFile,
                classNodes,
                methodNodes,
                fieldNodes,
                null);
            File outputFile = renamerService.execute();
            System.out.println("Output file: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void readJEBJSON(File jsonFile) throws IOException{
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> originalData = objectMapper.readValue(
            jsonFile, 
            new TypeReference<Map<String, Object>>() {}
        );
        
        for (Object o : originalData.values()) {
            if (o instanceof Map) {
                Map<?, ?> innerMap = (Map<?, ?>) o;
                for (Object j : innerMap.values()) {
                    if (j instanceof Map) {
                        Map<?, ?> valueMap = (Map<?, ?>) j;

                        if (valueMap.containsKey("renamed_fields")) {
                            Map<String, String> fieldData = (Map<String, String>)valueMap.get("renamed_fields");
                            for(String key: fieldData.keySet()){
                                String normalized = key.replaceAll("L", "")
                                                .replaceAll(";", "")
                                                .replaceAll("/", ".");
                                String[] split = normalized.split("->");
                                String className = split[0];
                                String fieldName = split[1];
                                String[] split1 = fieldName.split(":");
                                Node node = new Node(){
                                    {
                                        owner = className;
                                        name = split1[0];
                                        desc = split1[1];
                                        newName = fieldData.get(key);
                                    }
                                };
                                fieldNodes.add(node);
                            }
                        }
                        if (valueMap.containsKey("renamed_methods")) {
                            Map<String, String> methodData = (Map<String, String>)valueMap.get("renamed_methods");
                            for(String key: methodData.keySet()){
                                String normalized = key.replaceAll("L", "")
                                                .replaceAll(";", "")
                                                .replaceAll("/", ".");
                                String[] split = normalized.split("->");
                                String className = split[0];
                                String methodName = split[1];
                                String[] split1 = methodName.split("\\(");
                                Node node = new Node(){
                                    {
                                        owner = className;
                                        name = split1[0];
                                        desc = split1[1];
                                        newName = methodData.get(key);
                                    }
                                };
                                methodNodes.add(node);
                            }
                        }
                        if (valueMap.containsKey("renamed_classes")) {
                            Map<String, String> classData = (Map<String, String>)valueMap.get("renamed_classes");
                            for(String key: classData.keySet()){
                                String normalized = key.replaceAll("L", "")
                                                .replaceAll(";", "")
                                                .replaceAll("/", ".");
                                String[] split = normalized.split("->");
                                String className = split[0];
                                int pos = className.lastIndexOf(".");
                                String packageName = className.substring(0, pos);
                                String simpleName = className.substring(pos + 1);
                                Node node = new Node(){
                                    {
                                        owner = packageName;
                                        name = simpleName;
                                        desc = className;
                                        newName = classData.get(key);
                                    }
                                };
                                classNodes.add(node);
                            }
                        }
                    }
                }
            }
        }
    }
}
