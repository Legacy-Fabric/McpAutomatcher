package net.legacyfabric.mcpmatcher.util;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.*;
import org.objectweb.asm.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class MappingUtils {

    public static MappingSet findAndCreateDescriptors(Path jarFile, MappingSet mappings) throws IOException {
        SignatureVisitor visitor = new SignatureVisitor(mappings);
        readJarFile(jarFile, visitor);

        return visitor.getMappings();
    }

    private static void readJarFile(Path jarFile, SignatureVisitor visitor) throws IOException {
        JarFile file = new JarFile(jarFile.toFile());
        Enumeration<JarEntry> entries = file.entries();

        while (entries.hasMoreElements()) {
            JarEntry e = entries.nextElement();
            if (e.getName().endsWith(".class")) {
                ClassReader reader = new ClassReader(file.getInputStream(e));
                reader.accept(visitor, 0);
            }
        }
    }

    private static class SignatureVisitor extends ClassVisitor {

        private final MappingSet inMappings;
        private final MappingSet outMappings;
        private Optional<? extends ClassMapping<?, ?>> oldClassMapping;
        private TopLevelClassMapping newClassMapping;

        public SignatureVisitor(MappingSet mappings) {
            super(Opcodes.ASM9);
            this.inMappings = mappings;
            this.outMappings = MappingSet.create();
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            oldClassMapping = inMappings.getClassMapping(name);
            oldClassMapping.ifPresent(classMapping -> newClassMapping = outMappings.createTopLevelClassMapping(classMapping.getFullObfuscatedName(), classMapping.getFullDeobfuscatedName()));
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            oldClassMapping.ifPresent(classMapping -> {
                Optional<MethodMapping> methodMapping = classMapping.getMethodMapping(name, descriptor);
                methodMapping.ifPresent(mapping -> newClassMapping.getOrCreateMethodMapping(name, descriptor).setDeobfuscatedName(mapping.getDeobfuscatedName()));
            });
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (oldClassMapping.isPresent()) {
                Optional<FieldMapping> fieldMapping = oldClassMapping.get().getFieldMapping(oldClassMapping.get().getObfuscatedName() + "/" + name);
                fieldMapping.ifPresent(mapping -> newClassMapping.getOrCreateFieldMapping(name, descriptor).setDeobfuscatedName(mapping.getDeobfuscatedName()));
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        public MappingSet getMappings() {
            return outMappings;
        }
    }


    public static void iterateClasses(MappingSet mappings, Consumer<ClassMapping<?, ?>> consumer) {
        for (TopLevelClassMapping topLevelClassMapping : mappings.getTopLevelClassMappings()) {
            iterateClasses(topLevelClassMapping, consumer);
        }
    }

    private static void iterateClasses(ClassMapping<?, ?> classMapping, Consumer<ClassMapping<?, ?>> consumer) {
        consumer.accept(classMapping);

        for (InnerClassMapping innerClassMapping : classMapping.getInnerClassMappings()) {
            iterateClasses(innerClassMapping, consumer);
        }
    }
}
