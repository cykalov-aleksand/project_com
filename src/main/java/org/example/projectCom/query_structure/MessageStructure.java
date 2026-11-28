package org.example.projectCom.query_structure;

import java.util.List;

public record MessageStructure(String name, List<FildSpec> fields) {
    public int totalSize() {
        return fields.stream().mapToInt(FildSpec::size).sum();
    }

    public int offsetOf(String fieldName) {
        int offset = 0;
        for (FildSpec f : fields) {
            if (f.name().equals(fieldName)) {
                return offset;
            }
            offset += f.size();
        }
        throw new IllegalArgumentException("Поле " + fieldName + " не найдено в " + name);
    }
}
