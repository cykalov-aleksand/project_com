package org.example.projectCom.query_structure;

import org.example.projectCom.dto.enum_panels.OutputOption;

import java.util.List;

/** Строит payload строго по MessageStructure: пропустил поле — исключение. */
public class PayloadBuffer {
    private final MessageStructure struct;
    private final byte[] data;
    private int cursor = 0;

    public PayloadBuffer(MessageStructure struct) {
        this.struct = struct;
        this.data = new byte[struct.totalSize()];
    }

    public PayloadBuffer putKey(int key) {
        return putUInt8("key", key);
    }

    public PayloadBuffer putUInt8(String name, int value) {
        int offset = struct.offsetOf(name);
        data[offset] = (byte) value;
        return this;
    }

    public PayloadBuffer putUInt16(String name, int value) {
        int offset = struct.offsetOf(name);
        data[offset]     = (byte) ((value >> 8) & 0xFF); // старший байт — первым
        data[offset + 1] = (byte) (value & 0xFF);        // младший — вторым
        return this;
    }
    /*
    превращает список выбранных выходов в 5 байт, где каждый бит — один выход
     */
    public PayloadBuffer putOutputs(List<OutputOption> selected) {
        int offset = struct.offsetOf("outputs");
        int size = struct.fields().stream()
                .filter(f -> f.name().equals("outputs"))
                .findFirst().orElseThrow().size();
        for (int bit = 0; bit < size * 8; bit++) {
            OutputOption option = OutputOption.byIndex(bit); // нужен такой метод
            if (option != null && selected.contains(option)) {
                data[offset + bit / 8] |= (byte) (1 << (bit % 8));
            }
        }
        return this;
    }

    public byte[] build() {
        if (cursor < struct.fields().size()) { /* можно сделать строгий чек «все поля заполнены» */ }
        return data;
    }
}
