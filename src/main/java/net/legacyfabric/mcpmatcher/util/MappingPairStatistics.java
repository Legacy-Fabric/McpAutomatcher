package net.legacyfabric.mcpmatcher.util;

import org.cadixdev.lorenz.MappingSet;

public record MappingPairStatistics(
        int leftClassCount, int rightClassCount, int classesUnmatched,
        int leftFieldCount, int rightFieldCount, int fieldsUnmatched,
        int leftMethodCount, int rightMethodCount, int methodsUnmatched,
        MappingSet mergedMcp) {
}
