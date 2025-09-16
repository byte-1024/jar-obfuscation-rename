package com.example.analyzer;

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class StringAnalyzer extends ClassVisitor {
    private final String className;
    private final Set<String> foundStrings = new HashSet<>();

    public StringAnalyzer(String className) {
        super(Opcodes.ASM9);
        this.className = className;
    }

    public Set<String> getFoundStrings() {
        return foundStrings;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof String) {
                    foundStrings.add((String) value);
                }
                super.visitLdcInsn(value);
            }
        };
    }
}
