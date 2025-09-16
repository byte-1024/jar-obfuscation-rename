package com.example;

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MemberAnalyzer extends ClassVisitor {
    private final Set<String> fieldNames = new HashSet<>();
    private final Set<String> methodNames = new HashSet<>();
    private final String className;

    public MemberAnalyzer(String className) {
        super(Opcodes.ASM9);
        this.className = className;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        fieldNames.add(name);
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        methodNames.add(name);
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    public Set<String> getFieldNames() {
        return fieldNames;
    }

    public Set<String> getMethodNames() {
        return methodNames;
    }
}
