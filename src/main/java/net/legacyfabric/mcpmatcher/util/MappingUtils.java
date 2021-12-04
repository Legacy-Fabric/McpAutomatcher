package net.legacyfabric.mcpmatcher.util;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.lorenz.model.*;
import org.objectweb.asm.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MappingUtils {

    public static final Map<Pattern, String> MCP_NAME_CHANGE_MAP = new HashMap<>();

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

    public static MappingSet readMappingsFile(String arg) throws IOException {
        //TODO: could use a map here and support more formats.
        if (arg.endsWith(".csrg")) {
            return MappingFormats.CSRG.read(Paths.get(arg));
        } else {
            return MappingFormats.SRG.read(Paths.get(arg));
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

    /**
     * Utility to fix repackaged classes in MCP.
     */
    public static Optional<? extends ClassMapping<?, ?>> getClassAndFixRepackaging(String fullObfName, MappingSet right) {
        // Check if the class normally matches first.
        Optional<? extends ClassMapping<?, ?>> result = right.getClassMapping(fullObfName);

        if (result.isPresent()) {
            return result;
        }

        // If that fails, check if the class was repackaged.
        String leftClassName = getClassName(fullObfName);

        for (TopLevelClassMapping classMapping : right.getTopLevelClassMappings()) {
            String rightClassName = getClassName(classMapping.getFullObfuscatedName());
            if (rightClassName.equals(leftClassName)) {
                System.out.println("Merging Repackaged classes '" + fullObfName + "' and '" + classMapping.getFullObfuscatedName() + "'");
            }
        }

        for (Pattern pattern : MCP_NAME_CHANGE_MAP.keySet()) {
            String[] innerClassSplit = fullObfName.split("\\$");
            Matcher matcher = pattern.matcher(innerClassSplit[0]);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(sb, MCP_NAME_CHANGE_MAP.get(pattern));
            }

            if (!sb.isEmpty()) {
                if (innerClassSplit.length == 2) {
                    sb.append("$").append(innerClassSplit[1]);
                }

                System.out.println(fullObfName + " -> " + sb);
                return getClassAndFixRepackaging(sb.toString(), right);
            }
        }
        return Optional.empty();
    }

    private static String getClassName(String fullName) {
        String[] split = fullName.split("/");
        return split[split.length - 1];
    }
}
