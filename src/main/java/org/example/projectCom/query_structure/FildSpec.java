package org.example.projectCom.query_structure;

public record FildSpec(String name, FildType type, int size) {
    public FildSpec {
        if (size <= 0) {
            throw new IllegalArgumentException("Поле " + name + " : размер должен быть больше 0");
        }
    }
}
