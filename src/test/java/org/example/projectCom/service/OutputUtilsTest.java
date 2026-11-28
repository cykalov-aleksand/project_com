package org.example.projectCom.service;


import org.example.projectCom.dto.enum_panels.OutputOption;
import org.example.projectCom.service.OutputUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.projectCom.dto.enum_panels.PanelNameOption.PANEL_OUTPUT_SWITCHING;
import static org.example.projectCom.dto.enum_panels.PanelNameOption.PANEL_SYNCHRONIZATION;

class OutputUtilsTest {

    // 40 выходов / 5 байтов масок — по 8 выходов на байт
    private static List<OutputOption> byteN(int n) {
        return Stream.of(OutputOption.values())
                .filter(o -> o.getByteIndex() == n)
                .toList();
    }

    // ---------- packOutputs ----------

    @Test
    void packOutputs_setsBitsPerByteIndex() {
        byte[] packed = OutputUtils.packOutputs(byteN(1), 5);

        assertThat(packed.length).isEqualTo(5);

        int expected = 0;
        for (OutputOption o : byteN(1)) {
            expected |= (o.getBitMask() & 0xFF); // знаковое расширение byte→int убрано
        }
        assertThat(packed[0] & 0xFF).isEqualTo(expected); // теперь 255 == 255
        assertThat(packed[1]).isZero();
    }

    @Test
    void packOutputs_throws_whenByteIndexOutOfRange() {
        List<OutputOption> outOfRange = Stream.of(OutputOption.values())
                .filter(o -> o.getByteIndex() > 5)
                .toList();
        if (outOfRange.isEmpty()) return; // если таких нет в enum — тест не нужен

        assertThatThrownBy(() -> OutputUtils.packOutputs(outOfRange, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- toJsonbHex / fromJsonbHex ----------

    @Test
    void toJsonbHex_formatsWithQuotesAndPairs() {
        assertThat(OutputUtils.toJsonbHex(new byte[]{0x01, (byte) 0xAB}))
                .isEqualTo("\"01AB\"");
    }

    @Test
    void fromJsonbHex_roundTrip() {
        byte[] original = {0x02, 0x00, 0x64, 0x03};
        String hex = OutputUtils.toJsonbHex(original);

        assertThat(OutputUtils.fromJsonbHex(hex)).isEqualTo(original);
    }

    @Test
    void fromJsonbHex_stripsQuotes() {
        assertThat(OutputUtils.fromJsonbHex("\"01FF\""))
                .isEqualTo(new byte[]{0x01, (byte) 0xFF});
    }

    @Test
    void fromJsonbHex_throws_onBlankOddLengthOrBadChar() {
        assertThatThrownBy(() -> OutputUtils.fromJsonbHex(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutputUtils.fromJsonbHex("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutputUtils.fromJsonbHex("012"))       // нечётная
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutputUtils.fromJsonbHex("0G"))        // не hex
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- buildPayload ----------

    @Test
    void buildPayload_prependsKeyCode() {
        byte[] packed = OutputUtils.packOutputs(byteN(1), 5); // честные 5 байтов масок

        byte[] payload = OutputUtils.buildPayload(PANEL_OUTPUT_SWITCHING, packed);

        assertThat(payload[0]).isEqualTo(PANEL_OUTPUT_SWITCHING.getKeyCode());
        assertThat(payload[1]).isEqualTo(packed[0]);
        assertThat(payload.length).isEqualTo(6); // ключ + 5 байтов масок — теперь корректно
        assertThat(payload).endsWith(packed);    // маски переехали без изменений
    }

    // ---------- buildFixedGrid ----------

    @Test
    void buildFixedGrid_missingBit_staysNull_notShifted() {
        List<OutputOption> options = byteN(1).stream()
                .filter(o -> o.getBitMask() != 0x02)   // убрали второй выход — создаём дырку
                .toList();

        Map<Integer, List<OutputOption>> grid = OutputUtils.buildFixedGrid(options);
        List<OutputOption> row = grid.get(1);

        assertThat(row.get(0)).isNotNull(); // 0x01 на своей колонке
        assertThat(row.get(1)).isNull();    // дырка осталась дыркой...
        assertThat(row.get(2)).isNotNull(); // ...а 0x04 не съехал в колонку 1
        assertThat(row).hasSize(8);
    }

    // ---------- extractSelectedOutputs ----------

    @Test
    void extractSelectedOutputs_blankInput_returnsEmpty() {
        assertThat(OutputUtils.extractSelectedOutputs(null, 1)).isEmpty();
        assertThat(OutputUtils.extractSelectedOutputs("  ", 1)).isEmpty();
    }

    @Test
    void extractSelectedOutputs_roundTripWithPackOutputs() {
        List<OutputOption> expected = byteN(3);

        byte[] packed = OutputUtils.packOutputs(expected, 5);
        byte[] payload = OutputUtils.buildPayload(PANEL_OUTPUT_SWITCHING, packed); // ключ [0] + маски
        String hex = OutputUtils.toJsonbHex(payload);

        assertThat(OutputUtils.extractSelectedOutputs(hex, 1))
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    // ---------- payloadOffset ----------

    @Test
    void payloadOffset_matchesProtocol() {
        assertThat(OutputUtils.payloadOffset(PANEL_OUTPUT_SWITCHING)).isEqualTo(1);
        assertThat(OutputUtils.payloadOffset(PANEL_SYNCHRONIZATION)).isEqualTo(1);
    }

    // ---------- extractDivisor ----------

    @Test
    void extractDivisor_documentedExample() {
        assertThat(OutputUtils.extractDivisor("0203030303036400"))
                .isEqualTo(100);
    }

    @Test
    void extractDivisor_blankOrTooShort_returnsZero() {
        assertThat(OutputUtils.extractDivisor(null)).isZero();
        assertThat(OutputUtils.extractDivisor("0200")).isZero();
    }

    // ---------- parseSelectedOutputs ----------

    @Test
    void parseSelectedOutputs_normalizesAndParses() {
        List<OutputOption> result = OutputUtils.parseSelectedOutputs(
                List.of("OUTPUT_1 ", "OUTPUT_2")); // подставь реальные имена

        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).isEqualTo(OutputOption.valueOf("OUTPUT_1"));
    }

    @Test
    void parseSelectedOutputs_nullOrEmpty_returnsEmpty() {
        assertThat(OutputUtils.parseSelectedOutputs(null)).isEmpty();
        assertThat(OutputUtils.parseSelectedOutputs(List.of())).isEmpty();
    }
}
