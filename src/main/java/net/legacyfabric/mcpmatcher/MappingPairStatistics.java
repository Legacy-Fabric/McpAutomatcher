package net.legacyfabric.mcpmatcher;

public record MappingPairStatistics(
        int leftClassCount, int rightClassCount, int classesUnmatched,
        int leftFieldCount, int rightFieldCount, int fieldsUnmatched,
        int leftMethodCount, int rightMethodCount, int methodsUnmatched) {
}
