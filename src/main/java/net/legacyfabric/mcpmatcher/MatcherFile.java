package net.legacyfabric.mcpmatcher;

import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.Base64;

public class MatcherFile {

    private static final ThreadLocal<TlData> globalTlData = ThreadLocal.withInitial(TlData::new);
    private final MappingSet mergedMcp;

    public MatcherFile(MappingSet mergedFixedMcp) {
        this.mergedMcp = mergedFixedMcp;
    }

    public void write(Path fileLocation, Path fromJar, Path toJar) throws IOException {
        try (Writer writer = Files.newBufferedWriter(fileLocation, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writeHeader(writer, fromJar, toJar);

            for (TopLevelClassMapping classMapping : mergedMcp.getTopLevelClassMappings()) {
                writeClassMapping(writer, classMapping);
            }
        }
    }

    private void writeHeader(Writer writer, Path fromJar, Path toJar) throws IOException {
        writer.write("Matches saved ");
        writer.write(ZonedDateTime.now().toString());
        writer.write(", input files:\n\ta:\n");
        writeFileInformation(writer, fromJar);
        writer.write("\tb:\n");
        writeFileInformation(writer, toJar);
        writer.write("\tcp:\n");
        writer.write("\tcp a:\n");
        writer.write("\tcp b:\n");
    }

    private void writeFileInformation(Writer writer, Path jar) throws IOException {
        writer.write("\t\t");
        writer.write(Long.toString(Files.size(jar)));
        writer.write('\t');
        writer.write(Base64.getEncoder().encodeToString(hash(jar)));
        writer.write('\t');
        writer.write(jar.getFileName().toString().replace('\n', ' '));
        writer.write('\n');
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

    // Stolen From Matcher. https://github.com/FabricMC/matcher
    private static byte[] hash(Path path) throws IOException {
        TlData tlData = globalTlData.get();

        MessageDigest digest = tlData.digest;
        ByteBuffer buffer = tlData.buffer;
        buffer.clear();

        try (SeekableByteChannel channel = Files.newByteChannel(path)) {
            while (channel.read(buffer) != -1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }

        return digest.digest();
    }

    private static class TlData {
        TlData() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
                buffer = ByteBuffer.allocate(256 * 1024);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        final MessageDigest digest;
        final ByteBuffer buffer;
    }
}
