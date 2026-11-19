package org.example.projectCom.port;

import org.springframework.stereotype.Component;

import java.util.Arrays;



@Component
public class PanelFrameBuilder {
    private static final int HEADER_LEN = 6;
    /** Кадр запроса JSON панели. */
    public byte[] buildRead(Long panelId) {
        // TODO: реальный формат (адрес, команда, контрольная сумма...)
        return new byte[]{panelId.byteValue(), 0, 0, 0, 0,0 };
    }
    /**
     * Кадр записи: payload уже содержит ключ МК в нулевом байте,
     * дальше — данные. Отправляем как есть.
     */
    public byte[] buildWrite(byte[] payload) {
        if (payload == null || payload.length < 6) {
            throw new IllegalArgumentException(
                    "Payload слишком короткий: первые 6 байт (ключ + заголовок) обязательны");
        }
        return payload;
    }

    /** Ожидаемая длина ответа на чтение (эхо 6 байт + данные). */
    public int readResponseLength() {
        return 6 + 64; // TODO: под протокол
    }

    /** Ожидаемая длина ответа на запись (только эхо). */
    public int writeResponseLength() {
        return 6;
    }
    /** Эхо-блок для проверки — первые 6 байт кадра (ключ + заголовок). */
    public byte[] echoBlock(byte[] frame) {
        return Arrays.copyOfRange(frame, 0, HEADER_LEN);
    }
}
