package net.legacyfabric.mcpmatcher;

import net.legacyfabric.mcpmatcher.util.MappingPairStatistics;
import net.legacyfabric.mcpmatcher.util.MappingUtils;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class McpAutoMatcher {

    public static void main(String[] args) throws IOException {
        Path v1_8_9 = Paths.get("/home/cope/projects/Legacy-Fabric/McpAutomatcher/run/1.8.9");
        Path v1_9_4 = Paths.get("/home/cope/projects/Legacy-Fabric/McpAutomatcher/run/1.9.4");
        Path matcherOutput = Paths.get("/home/cope/projects/Legacy-Fabric/McpAutomatcher/run/1.8.9-1.9.4.match");

        MappingSet v1_8_9Mappings = MappingFormats.CSRG.read(v1_8_9.resolve("joined.csrg"));
        MappingSet v1_9_4Mappings = MappingFormats.CSRG.read(v1_9_4.resolve("joined.csrg"));

        MappingPairStatistics statistics = getStatistics(v1_8_9Mappings, v1_9_4Mappings);

        // Class Statistics
        int adjustedUnmatchableClasses = statistics.classesUnmatched() - (statistics.rightClassCount() - statistics.leftClassCount());
        System.out.println("===========================================");
        System.out.println("Left Class Count " + statistics.leftClassCount());
        System.out.println("Right Class Count " + statistics.rightClassCount());
        System.out.println("Total Unmatchable Classes " + statistics.classesUnmatched());
        System.out.println("Total Unmatchable Classes (Adjusted for Class Difference) " + adjustedUnmatchableClasses);
        System.out.println("===========================================");

        // Field Statistics
        System.out.println("Left Field Count " + statistics.leftFieldCount());
        System.out.println("Right Field Count " + statistics.rightFieldCount());
        System.out.println("Total Unmatched Fields " + statistics.fieldsUnmatched());
        System.out.println("===========================================");

        // Method Statistics
        System.out.println("Left Method Count " + statistics.leftMethodCount());
        System.out.println("Right Method Count " + statistics.rightMethodCount());
        System.out.println("Total Unmatched Methods " + statistics.methodsUnmatched());
        System.out.println("===========================================");

        System.out.println("Fixing Signatures");
        MappingSet mergedMcpFixed = MappingUtils.findAndCreateDescriptors(v1_8_9.resolve("minecraft-1.8.9-merged.jar"), statistics.mergedMcp());

        System.out.println("Generating Match File");
        MatcherFile matcherFile = new MatcherFile(mergedMcpFixed);
        matcherFile.write(matcherOutput, "1.8.9-merged.jar", "1.9.4-merged.jar");
    }

    public static MappingPairStatistics getStatistics(MappingSet left, MappingSet right) {
        // We are comparing named against named, so we need to invert the mappings.
        MappingSet reversedLeft = left.reverse();
        MappingSet reversedRight = right.reverse();
        MappingSet mergedMcp = MappingSet.create();

        int unmatchedClasses = 0;
        int unmatchedFields = 0;
        int unmatchedMethods = 0;
        int leftFieldCount = 0;
        int rightFieldCount = 0;
        int leftMethodCount = 0;
        int rightMethodCount = 0;

        for (TopLevelClassMapping leftClass : reversedLeft.getTopLevelClassMappings()) {
            Optional<? extends ClassMapping<?, ?>> rightClass = getClassAndFixRepackaging(leftClass.getFullObfuscatedName(), reversedRight);
            TopLevelClassMapping mergedClassMapping = null; // Won't be null as long as rightClass.isPresent()

            if (rightClass.isEmpty()) {
                unmatchedClasses++;
            } else {
                mergedClassMapping = mergedMcp.createTopLevelClassMapping(leftClass.getFullDeobfuscatedName(), rightClass.get().getFullDeobfuscatedName());

                rightFieldCount += rightClass.get().getFieldMappings().size();
                rightMethodCount += rightClass.get().getMethodMappings().size();
            }

            leftFieldCount += leftClass.getFieldMappings().size();
            leftMethodCount += leftClass.getMethodMappings().size();

            for (FieldMapping leftField : leftClass.getFieldMappings()) {
                Optional<FieldMapping> rightField = rightClass.isPresent() ? rightClass.get().getFieldMapping(leftField.getSignature()) : Optional.empty();

                if (rightField.isEmpty()) {
                    unmatchedFields++;
                } else {
                    mergedClassMapping.createFieldMapping(leftField.getFullDeobfuscatedName(), rightField.get().getFullDeobfuscatedName());
                }
            }

            for (MethodMapping leftMethod : leftClass.getMethodMappings()) {
                Optional<MethodMapping> rightMethod = rightClass.isPresent() ? rightClass.get().getMethodMapping(leftMethod.getSignature()) : Optional.empty();

                if (rightMethod.isEmpty()) {
                    unmatchedMethods++;
                } else {
                    MethodMapping methodMapping = mergedClassMapping.createMethodMapping(leftMethod.getDeobfuscatedSignature());
                    methodMapping.setDeobfuscatedName(rightMethod.get().getDeobfuscatedName());
                }
            }
        }

        return new MappingPairStatistics(
                left.getTopLevelClassMappings().size(), right.getTopLevelClassMappings().size(), unmatchedClasses,
                leftFieldCount, rightFieldCount, unmatchedFields,
                leftMethodCount, rightMethodCount, unmatchedMethods,
                mergedMcp
        );
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
