package com.example;

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

public class FieldAnalyzer extends ClassVisitor {
    private final Set<String> fieldNames = new HashSet<>();

    public FieldAnalyzer() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        fieldNames.add(name);
        return super.visitField(access, name, descriptor, signature, value);
    }

    public Set<String> getFieldNames() {
        return fieldNames;
    }
}
