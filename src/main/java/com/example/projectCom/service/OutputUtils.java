package com.example.projectCom.service;

import com.example.projectCom.setup_controller.OutputOption;
import com.example.projectCom.setup_controller.PanelNameOption;

import java.util.*;

/**
 * Утилитный класс для упаковки/распаковки конфигураций панелей.
 *
 * Форматы пакетов (индексация байтов с нуля):
 *   Коммутация:   [0]=ключ панели, [1..5]=маски выходов (5 байтов)
 *   Синхронизация: [0]=ключ панели, [1..2]=делитель периода (big-endian),
 *                  [3..7]=маски выходов (5 байтов)
 *
 * Маски хранятся в базе как hex-строка вида "0200640303030303".
 */
public final class OutputUtils {

    private OutputUtils() {
        // утилитный класс — без экземпляров
    }

    /**
     * Собирает список выбранных выходов в массив битовых масок.
     * Каждый выход ставит свой бит в байте с номером (byteIndex - 1).
     *
     * @param byteCount сколько байтов масок отводить (для 40 выходов — 5)
     */
    public static byte[] packOutputs(List<OutputOption> selected, int byteCount) {
        byte[] result = new byte[byteCount];
        for (OutputOption option : selected) {
            int index = option.getByteIndex() - 1;   // «1-й байт» → индекс 0
            if (index < 0 || index >= byteCount) {
                throw new IllegalArgumentException(
                        "Некорректный byteIndex для " + option.name() + ": " + option.getByteIndex());
            }
            result[index] |= option.getBitMask();
        }
        return result;
    }

    /**
     * Превращает массив байтов в hex-строку для хранения в jsonb.
     * Формат: кавычки + пары hex-символов, например "0200640303030303".
     */
    public static String toJsonbHex(byte[] packed) {
        StringBuilder sb = new StringBuilder("\"");
        for (byte b : packed) sb.append(String.format("%02X", b));
        sb.append("\"");
        return sb.toString();
    }

    /**
     * Собирает пакет конфигурации коммутации: ключ панели + 5 байтов масок.
     * Обратная операция — extractSelectedOutputs(hex) с startByte=1.
     */
    public static byte[] buildPayload(PanelNameOption panel, byte[] packed) {
        byte[] payload = new byte[packed.length + 1];
        payload[0] = panel.getKeyCode();
        System.arraycopy(packed, 0, payload, 1, packed.length);
        return payload;
    }

    /**
     * Разбирает hex-строку из базы в массив байтов.
     * Снимает кавычки, если jsonb вернул строку в них.
     * Бросает IllegalArgumentException при пустой или некорректной строке.
     */
    public static byte[] fromJsonbHex(String hex) {
        if (hex == null || hex.isBlank()) {
            throw new IllegalArgumentException("Пустая hex-строка конфигурации");
        }
        String clean = hex.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1);
        }
        if (clean.length() % 2 != 0) {
            throw new IllegalArgumentException(
                    "Нечётная длина hex-строки: " + clean.length());
        }
        byte[] result = new byte[clean.length() / 2];
        for (int i = 0; i < result.length; i++) {
            try {
                result[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Некорректный hex-символ в позиции " + i * 2 + ": «"
                                + clean.substring(i * 2, i * 2 + 2) + "»");
            }
        }
        return result;
    }

    /**
     * Раскладывает выходы в фиксированную сетку для таблицы шаблона:
     * ключ — номер байта (1-й, 2-й...), значение — список из ровно 8 ячеек.
     * Отсутствующий выход — null, поэтому чекбоксы не смещаются влево.
     * Позицию бита вычисляет из маски: 0x01→колонка 0, 0x02→колонка 1, 0x04→колонка 2...
     */
    public static Map<Integer, List<OutputOption>> buildFixedGrid(Collection<OutputOption> options) {
        Map<Integer, List<OutputOption>> grid = new LinkedHashMap<>();
        for (OutputOption opt : options) {
            int bitIndex = Integer.numberOfTrailingZeros(opt.getBitMask());
            List<OutputOption> row = grid.computeIfAbsent(
                    opt.getByteIndex(), k -> new ArrayList<>(Collections.nCopies(8, null)));
            row.set(bitIndex, opt);
        }
        return grid;
    }

    /**
     * Читает выбранные выходы из пакета, начиная с байта startByte.
     * Коммутация: startByte=1 (маски после ключа),
     * синхронизация: startByte=3 (маски после ключа и делителя).
     * Пустая строка конфигурации → пустой список, а не ошибка.
     */
    public static List<OutputOption> extractSelectedOutputs(String jsonbHex, int startByte) {
        if (jsonbHex == null || jsonbHex.isBlank()) {
            return List.of();
        }
        byte[] payload = fromJsonbHex(jsonbHex);
        List<OutputOption> selected = new ArrayList<>();
        for (OutputOption option : OutputOption.values()) {
            int index = startByte + (option.getByteIndex() - 1);
            if (index >= payload.length) break;   // вышли за пределы сохранённой маски
            if ((payload[index] & option.getBitMask()) != 0) {
                selected.add(option);
            }
        }
        return selected;
    }

    /**
     * Чтение масок для панели коммутации — маски начинаются с байта 1.
     */
    public static List<OutputOption> extractSelectedOutputs(String jsonbHex) {
        return extractSelectedOutputs(jsonbHex, 1);
    }

    /**
     * Читает делитель периода синхронизации из байтов 1–2 (big-endian).
     * Пример: "020064..." → байты 00 64 → 100 → период «10 мс».
     * Возвращает 0, если конфигурации нет или пакет короче ожидаемого.
     */
    public static int extractDivisor(String jsonbHex) {
        if (jsonbHex == null || jsonbHex.isBlank()) {
            return 0;
        }
        byte[] payload = fromJsonbHex(jsonbHex);
        if (payload.length < 3) {
            return 0;
        }
        return ((payload[1] & 0xFF) << 8) | (payload[2] & 0xFF);
    }

    /**
     * Собирает пакет конфигурации синхронизации:
     * [0]=ключ панели, [1..2]=делитель (big-endian), [3..7]=5 байтов масок.
     * Обратные операции — extractDivisor(hex) и extractSelectedOutputs(hex, 3).
     */
    public static byte[] buildSyncPayload(PanelNameOption panelKey, int divisor,
                                          List<OutputOption> selected) {
        byte[] payload = new byte[8];
        payload[0] = panelKey.getKeyCode();
        payload[1] = (byte) ((divisor >> 8) & 0xFF);
        payload[2] = (byte) (divisor & 0xFF);
        byte[] mask = packOutputs(selected, 5);
        System.arraycopy(mask, 0, payload, 3, 5);
        return payload;
    }
}