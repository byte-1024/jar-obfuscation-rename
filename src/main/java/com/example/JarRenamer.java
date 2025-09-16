package com.example;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import com.example.JEBJsonParser.Node;
import com.example.analyzer.FieldAnalyzer;
import com.example.analyzer.MemberAnalyzer;
import com.example.analyzer.StringAnalyzer;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public class JarRenamer {
    private final File jarFile;
    private final Set<String> excludeClasses;
    private final List<Node> classNodes;
    private final List<Node> methodNodes;
    private final List<Node> fieldNodes;

    private final Map<String, String> fieldMappingGlobal = new HashMap<>();
    private final Map<String, String> methodMappingGlobal = new HashMap<>();
    private final Map<String, String> stringMappingGlobal = new HashMap<>();

    private final Map<String, Integer> classNameCounters = new HashMap<>();
    private final Map<String, Integer> methodNameCounters = new HashMap<>();
    private final Map<String, Integer> fieldNameCounters = new HashMap<>();

    private final Map<String, Set<String>> usedNamesInClass = new HashMap<>();

    private final Map<String, String> consistentRenamingCache = new HashMap<>();

    public JarRenamer(File jarFile, List<Node> classNames, List<Node> methodNames,
                             List<Node> fieldNames, Set<String> excludeClasses) {
        this.jarFile = jarFile;
        this.excludeClasses = excludeClasses;
        this.classNodes = classNames;
        this.methodNodes = methodNames;
        this.fieldNodes = fieldNames;
    }

    public File execute() throws IOException {
        String originalName = jarFile.getName();
        String baseName = originalName.substring(0, originalName.lastIndexOf('.'));
        File outputFile = new File(jarFile.getParentFile(), baseName + "-renamed.jar");
        analyzeClasses();
        analyzeFieldsAndMethods();
        analyzeStrings();
        try (JarInputStream jarIn = new JarInputStream(new FileInputStream(jarFile));
             JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(outputFile))) {
            Set<String> processedEntries = new HashSet<>();
            JarEntry entry;
            while ((entry = jarIn.getNextJarEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.endsWith(".class")) {
                    String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');

                    byte[] classBytes = readAllBytes(jarIn);
                    byte[] transformedClass = transformClass(classBytes, className);

                    String newEntryName;
                    if (excludeClasses == null || !excludeClasses.contains(className)) {
                        String newClassName = getConsistentNameForClass(className);
                        newEntryName = newClassName != null
                                ? newClassName.replace('.', '/') + ".class"
                                : entryName;
                    } else {
                        newEntryName = entryName;
                    }

                    if (!processedEntries.contains(newEntryName)) {
                        processedEntries.add(newEntryName);
                        jarOut.putNextEntry(new JarEntry(newEntryName));
                        jarOut.write(transformedClass);
                    }
                } else {
                    if (!processedEntries.contains(entryName)) {
                        processedEntries.add(entryName);

                        jarOut.putNextEntry(new JarEntry(entryName));
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = jarIn.read(buffer)) != -1) {
                            jarOut.write(buffer, 0, bytesRead);
                        }
                    }
                }
                System.err.println("Processed entry: " + entryName);
                jarOut.closeEntry();
            }
        }

        return outputFile;
    }
    private void analyzeStrings() throws IOException {
        if ((classNodes == null || classNodes.isEmpty()) && 
            (methodNodes == null || methodNodes.isEmpty())) {
            return;
        }
        
        try (JarInputStream jarIn = new JarInputStream(new FileInputStream(jarFile))) {
            JarEntry entry;
            while ((entry = jarIn.getNextJarEntry()) != null) {
                String entryName = entry.getName();

                if (entryName.endsWith(".class")) {
                    String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');

                    if (excludeClasses == null || !excludeClasses.contains(className)) {
                        byte[] classBytes = readAllBytes(jarIn);
                        ClassReader reader = new ClassReader(classBytes);
                        StringAnalyzer analyzer = new StringAnalyzer(className);
                        reader.accept(analyzer, 0);
                        
                        for (String originalString : analyzer.getFoundStrings()) {
                            if (!stringMappingGlobal.containsKey(originalString)) {
                                String replacement = findClassOrMethodReplacement(originalString);
                                if (replacement != null && !replacement.equals(originalString)) {
                                    stringMappingGlobal.put(originalString, replacement);
                                }
                            }
                        }
                    }
                }
            }
            System.err.println("String analysis completed.");
        } catch (IOException e) {
            throw new IOException("Error analyzing strings: " + e.getMessage(), e);
        }
    }

    private String findClassOrMethodReplacement(String original) {
        if (classNodes != null && !classNodes.isEmpty()) {
            for (Node node : classNodes) {
                if (node.desc.equals(original)) {
                    return node.newName;
                }
            }
        }
        
        if (methodNodes != null && !methodNodes.isEmpty()) {
            for (Node node : methodNodes) {
                if (node.name.equals(original)) {
                    return node.newName;
                }
            }
        }
        
        return null;
    }

    private String getConsistentNameForClass(String className) {
        if (consistentRenamingCache.containsKey(className)) {
            return consistentRenamingCache.get(className);
        }

        String newName = getNewName(className, "class");
        consistentRenamingCache.put(className, newName);
        return newName;
    }

    private String getConsistentNameForMethod(String className, String methodName) {
        String key = className + "." + methodName;
        if (consistentRenamingCache.containsKey(key)) {
            return consistentRenamingCache.get(key);
        }

        String newName = getNewName(methodName, "method");
        consistentRenamingCache.put(key, newName);
        return newName;
    }

    private String getConsistentNameForField(String className, String fieldName) {
        String key = className + "." + fieldName;
        if (consistentRenamingCache.containsKey(key)) {
            return consistentRenamingCache.get(key);
        }

        String newName = getNewName(fieldName, "field");
        consistentRenamingCache.put(key, newName);
        return newName;
    }

    private String getReplacementForString(String originalString) {
        return stringMappingGlobal.getOrDefault(originalString, originalString);
    }

    private void analyzeClasses() throws IOException {
        try (JarInputStream jarIn = new JarInputStream(new FileInputStream(jarFile))) {
            JarEntry entry;
            while ((entry = jarIn.getNextJarEntry()) != null) {
                String entryName = entry.getName();

                if (entryName.endsWith(".class")) {
                    byte[] classBytes = readAllBytes(jarIn);
                    ClassReader reader = new ClassReader(classBytes);
                    FieldAnalyzer analyzer = new FieldAnalyzer();
                    reader.accept(analyzer, 0);
                }
            }
            System.err.println("Class analysis completed.");
        }
    }

    private void analyzeFieldsAndMethods() throws IOException {
        try (JarInputStream jarIn = new JarInputStream(new FileInputStream(jarFile))) {
            JarEntry entry;
            while ((entry = jarIn.getNextJarEntry()) != null) {
                String entryName = entry.getName();

                if (entryName.endsWith(".class")) {
                    String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');

                    if (excludeClasses == null || !excludeClasses.contains(className)) {
                        byte[] classBytes = readAllBytes(jarIn);
                        ClassReader reader = new ClassReader(classBytes);
                        MemberAnalyzer analyzer = new MemberAnalyzer(className);
                        reader.accept(analyzer, 0);

                        for (String fieldName : analyzer.getFieldNames()) {
                            String key = className.replace('.', '/') + "." + fieldName;
                            String newFieldName = getConsistentNameForField(className, fieldName);

                            if(fieldName.equals(newFieldName)){
                                continue;
                            }

                            Set<String> usedNames = usedNamesInClass.get(className);
                            if (usedNames == null) {
                                usedNames = new HashSet<>();
                                usedNamesInClass.put(className, usedNames);
                            }

                            while (usedNames.contains(newFieldName)) {
                                newFieldName = newFieldName + "_" + generateDeterministicSuffix(fieldName, fieldNameCounters);
                            }

                            usedNames.add(newFieldName);
                            fieldMappingGlobal.put(key, newFieldName);
                        }

                        for (String methodName : analyzer.getMethodNames()) {
                            if (methodName.equals("<init>") || methodName.equals("<clinit>")) {
                                continue;
                            }

                            String key = className.replace('.', '/') + "." + methodName;
                            String newMethodName = getConsistentNameForMethod(className, methodName);

                            if(methodName.equals(newMethodName)){
                                continue;
                            }

                            Set<String> usedNames = usedNamesInClass.get(className);
                            if (usedNames == null) {
                                usedNames = new HashSet<>();
                                usedNamesInClass.put(className, usedNames);
                            }

                            while (usedNames.contains(newMethodName)) {
                                newMethodName = newMethodName + "_" + generateDeterministicSuffix(methodName, methodNameCounters);
                            }

                            usedNames.add(newMethodName);
                            methodMappingGlobal.put(key, newMethodName);
                        }
                    }
                }
            }
            System.err.println("Field and method analysis completed.");
        } catch (IOException e) {
            throw new IOException("Error analyzing fields and methods: " + e.getMessage(), e);
        }
    }

    private byte[] transformClass(byte[] classBytes, String className) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

        ClassRemapper remapper = new ClassRemapper(writer, new CustomRemapper(className));
        reader.accept(remapper, ClassReader.EXPAND_FRAMES);

        return writer.toByteArray();
    }

    private String getNewName(String originalName, String type) {
        if (originalName.startsWith("java.") || originalName.startsWith("javax.") || originalName.startsWith("android.")) {
            return originalName;
        }
        switch (type) {
            case "class":
                if (classNodes != null && !classNodes.isEmpty()) {
                    for(Node node: classNodes){
                        if(node.desc.equals(originalName)){
                            return node.owner + "." + node.newName;
                        }
                    }
                }
                break;
            case "method":
                if (methodNodes != null && !methodNodes.isEmpty()) {
                    for(Node node: methodNodes){
                        if(node.name.equals(originalName)){
                            return node.newName;
                        }
                    }
                }
                break;
            case "field":
                if (fieldNodes != null && !fieldNodes.isEmpty()) {           
                    for(Node node: fieldNodes){
                        if(node.name.equals(originalName)){
                            return node.newName;
                        }
                    }
                }
                break;
        }
        return originalName;
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int bytesRead;
        byte[] data = new byte[4096];

        while ((bytesRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }

        return buffer.toByteArray();
    }

    private String generateDeterministicSuffix(String originalName, Map<String, Integer> counters) {
        Integer counter = counters.getOrDefault(originalName, 0);
        counters.put(originalName, counter + 1);
        return String.valueOf(counter);
    }

    private class CustomRemapper extends Remapper {
        private final Map<String, String> fieldMappings = new HashMap<>();
        private final Map<String, String> methodMappings = new HashMap<>();

        public CustomRemapper(String currentClassName) {
            fieldMappings.putAll(fieldMappingGlobal);
            methodMappings.putAll(methodMappingGlobal);
        }

        @Override
        public String map(String internalName) {
            String className = internalName.replace('/', '.');
            String newClassName;
            if (excludeClasses == null || !excludeClasses.contains(className)) {
                newClassName = getConsistentNameForClass(className);
            } else {
                newClassName = className;
            }
            return newClassName != null ? newClassName.replace('.', '/') : internalName;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            if (name.equals("<init>") || name.equals("<clinit>")) {
                return name;
            }

            String key = owner + "." + name;
            if (methodMappings.containsKey(key)) {
                return methodMappings.get(key);
            }

            String ownerClassName = owner.replace('/', '.');
            if (excludeClasses == null || !excludeClasses.contains(ownerClassName)) {
                String newName = getConsistentNameForMethod(ownerClassName, name);
                methodMappings.put(key, newName);
                return newName;
            }
            return name;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            String key = owner + "." + name;
            if (fieldMappings.containsKey(key)) {
                return fieldMappings.get(key);
            }

            String ownerClassName = owner.replace('/', '.');
            if (excludeClasses == null || !excludeClasses.contains(ownerClassName)) {
                String newName = getConsistentNameForField(ownerClassName, name);
                fieldMappings.put(key, newName);
                return newName;
            }
            return name;
        }

        @Override
        public Object mapValue(Object value) {
            if (value instanceof String) {
                String original = (String) value;
                String replacement = getReplacementForString(original);
                return replacement != null ? replacement : original;
            }
            return super.mapValue(value);
        }
    }

}
    