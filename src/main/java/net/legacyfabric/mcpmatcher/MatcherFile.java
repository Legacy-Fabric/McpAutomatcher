package net.legacyfabric.mcpmatcher;

import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;

public class MatcherFile {

    private final MappingSet mergedMcp;

    public MatcherFile(MappingSet mergedFixedMcp) {
        this.mergedMcp = mergedFixedMcp;
    }

    public void write(Path fileLocation, String fromJarName, String toJarName) throws IOException {
        try (Writer writer = Files.newBufferedWriter(fileLocation, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writeHeader(writer, fromJarName, toJarName);

            for (TopLevelClassMapping classMapping : mergedMcp.getTopLevelClassMappings()) {
                writeClassMapping(writer, classMapping);
            }
        }
    }

    private void writeHeader(Writer writer, String fromJarName, String toJarName) throws IOException {
        writer.write("Matches saved ");
        writer.write(ZonedDateTime.now().toString());
        writer.write(", input files:\n\ta:\n");
        writer.write("\t\t0\tno_hash\t" + fromJarName + "\n");
        writer.write("\tb:\n");
        writer.write("\t\t0\tno_hash\t" + toJarName + "\n");
        writer.write("\tcp:\n");
        writer.write("\tcp a:\n");
        writer.write("\tcp b:\n");
    }

    private void writeClassMapping(Writer writer, TopLevelClassMapping classMapping) throws IOException {
        writer.write("c\tL" + classMapping.getObfuscatedName() + ";\tL" + classMapping.getDeobfuscatedName() + ";\n");

        for (MethodMapping methodMapping : classMapping.getMethodMappings()) {
            writeMethodMapping(writer, methodMapping);
        }

        for (FieldMapping fieldMapping : classMapping.getFieldMappings()) {
            writeFieldMapping(writer, fieldMapping);
        }
    }

    private void writeMethodMapping(Writer writer, MethodMapping methodMapping) throws IOException {
        MethodSignature obfSignature = methodMapping.getSignature();
        MethodSignature deobfSignature = methodMapping.getDeobfuscatedSignature();
        writer.write("\t\tm\t" + obfSignature.getName() + obfSignature.getDescriptor() + "\t" + deobfSignature.getName() + deobfSignature.getDescriptor() + "\n");
    }

    private void writeFieldMapping(Writer writer, FieldMapping fieldMapping) throws IOException {
        String obfFieldInfo = fieldMapping.getObfuscatedName() + ";;" + fieldMapping.getSignature().getType().get();
        String[] splitDeobfName = fieldMapping.getDeobfuscatedName().split("/");
        String deobfFieldInfo = splitDeobfName[splitDeobfName.length - 1] + ";;" + fieldMapping.getDeobfuscatedSignature().getType().get();
        writer.write("\t\tf\t" + obfFieldInfo + "\t" + deobfFieldInfo + "\n");
    }
}
