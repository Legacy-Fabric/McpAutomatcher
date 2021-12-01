package net.legacyfabric.mcpmatcher;

import net.legacyfabric.mcpmatcher.util.MappingUtils;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class McpAutoMatcher {

    public static void main(String[] args) throws IOException {
        Path v1_8_9 = Paths.get("/home/cope/projects/Legacy-Fabric/McpAutomatcher/run/1.8.9");
        Path v1_9_4 = Paths.get("/home/cope/projects/Legacy-Fabric/McpAutomatcher/run/1.9.4");
        Path matcherOutput = Paths.get("/home/cope/projects/Legacy-Fabric/McpAutomatcher/run/1.9.4-1.8.9.match");

        Path v1_8_9Jar = v1_8_9.resolve("minecraft-1.8.9-merged.jar");
        Path v1_9_4Jar = v1_9_4.resolve("minecraft-1.9.4-merged.jar");

        MappingSet v1_8_9Mappings = MappingFormats.CSRG.read(v1_8_9.resolve("joined.csrg"));
        MappingSet v1_9_4Mappings = MappingFormats.CSRG.read(v1_9_4.resolve("joined.csrg"));

        MappingSet mergedMcp = mergeMcp(v1_9_4Mappings, v1_8_9Mappings);

        System.out.println("Fixing Signatures");
        MappingSet mergedMcpFixed = MappingUtils.findAndCreateDescriptors(v1_9_4Jar, mergedMcp);

        System.out.println("Generating Match File");
        MatcherFile matcherFile = new MatcherFile(mergedMcpFixed);
        matcherFile.write(matcherOutput, v1_9_4Jar, v1_8_9Jar);
    }

    public static MappingSet mergeMcp(MappingSet left, MappingSet right) {
        // We are comparing named against named, so we need to invert the mappings.
        MappingSet reversedLeft = left.reverse();
        MappingSet reversedRight = right.reverse();
        MappingSet mergedMcp = MappingSet.create();

        AtomicInteger i = new AtomicInteger();
        MappingUtils.iterateClasses(reversedLeft, leftClass -> {
            Optional<? extends ClassMapping<?, ?>> rightClass = getClassAndFixRepackaging(leftClass.getFullObfuscatedName(), reversedRight);
            if (rightClass.isPresent()) {
                i.getAndIncrement();
            }
        });
        System.out.println(i.get());

/*
        for (TopLevelClassMapping leftClass : reversedLeft.getTopLevelClassMappings()) {
            TopLevelClassMapping mergedClassMapping = null; // Won't be null as long as rightClass.isPresent()

            if (rightClass.isPresent()) {
                mergedClassMapping = mergedMcp.createTopLevelClassMapping(leftClass.getFullDeobfuscatedName(), rightClass.get().getFullDeobfuscatedName());
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
        }
*/

        return mergedMcp;
    }

    /**
     * Utility to fix repackaged classes in MCP.
     */
    protected static Optional<? extends ClassMapping<?, ?>> getClassAndFixRepackaging(String fullObfName, MappingSet right) {
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
        return Optional.empty();
    }

    private static String getClassName(String fullName) {
        String[] split = fullName.split("/");
        return split[split.length - 1];
    }
}
