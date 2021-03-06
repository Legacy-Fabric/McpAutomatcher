package net.legacyfabric.mcpmatcher;

import net.legacyfabric.mcpmatcher.util.MappingUtils;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;

public class McpAutoMatcher {

    public static void main(String[] args) throws IOException {
        if (args.length != 6) {
            System.out.println("java -jar McpAutomatcher.jar <older-version-jar> <newer-version-jar> <older-version-joined.csrg> <newer-version-joined.csrg> <fixes.properties> <output.match>");
            System.out.println("(Dont use spaces in file names!)");
            System.exit(69);
        }

        Path oldJar = Paths.get(args[0]);
        Path newJar = Paths.get(args[1]);
        MappingSet oldJoined = MappingUtils.readMappingsFile(args[2]);
        MappingSet newJoined = MappingUtils.readMappingsFile(args[3]);

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(args[4]))) {
            while (reader.ready()) {
                String[] property = reader.readLine().split("=");
                if (!property[0].startsWith("#") && property.length == 2) {
                    MappingUtils.MCP_NAME_CHANGE_MAP.put(compilePattern(property[0]), property[1]);
                }
            }
        }

        MappingSet mergedMcp = mergeMappings(oldJoined, newJoined);

        System.out.println("Fixing Signatures");
        MappingSet mergedMcpFixed = MappingUtils.findAndCreateDescriptors(oldJar, mergedMcp);

        System.out.println("Generating Match File");
        MatcherFile matcherFile = new MatcherFile(mergedMcpFixed);
        matcherFile.write(Paths.get(args[5]), oldJar, newJar);
    }

    /**
     * Merges the Obfuscated side of 2 mapping sets by using the Deobfuscated names as an intermediary.
     */
    public static MappingSet mergeMappings(MappingSet left, MappingSet right) {
        // We are comparing named against named, so we need to invert the mappings.
        MappingSet reversedLeft = left.reverse();
        MappingSet reversedRight = right.reverse();
        MappingSet mergedMcp = MappingSet.create();

        MappingUtils.iterateClasses(reversedLeft, leftClass -> {
            Optional<? extends ClassMapping<?, ?>> rightClass = MappingUtils.getClassAndFixRepackaging(leftClass.getFullObfuscatedName(), reversedRight);
            ClassMapping<?, ?> mergedClassMapping = null; // Won't be null as long as rightClass.isPresent()

            if (rightClass.isPresent()) {
                mergedClassMapping = mergedMcp.getOrCreateClassMapping(leftClass.getFullDeobfuscatedName()).setDeobfuscatedName(rightClass.get().getFullDeobfuscatedName());
            }

            for (FieldMapping leftField : leftClass.getFieldMappings()) {
                Optional<FieldMapping> rightField = rightClass.isPresent() ? rightClass.get().getFieldMapping(leftField.getSignature()) : Optional.empty();

                if (rightField.isPresent()) {
                    mergedClassMapping.createFieldMapping(leftField.getFullDeobfuscatedName(), rightField.get().getFullDeobfuscatedName());
                }
            }

            for (MethodMapping leftMethod : leftClass.getMethodMappings()) {
                Optional<MethodMapping> rightMethod = rightClass.isPresent() ? rightClass.get().getMethodMapping(leftMethod.getSignature()) : Optional.empty();

                if (rightMethod.isPresent()) {
                    MethodMapping methodMapping = mergedClassMapping.createMethodMapping(leftMethod.getDeobfuscatedSignature());
                    methodMapping.setDeobfuscatedName(rightMethod.get().getDeobfuscatedName());
                }
            }
        });
        return mergedMcp;
    }

    private static Pattern compilePattern(String pattern) {
        return Pattern.compile(pattern);
    }
}
