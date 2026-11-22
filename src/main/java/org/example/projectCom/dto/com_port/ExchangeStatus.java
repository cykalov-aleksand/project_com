package org.example.projectCom.dto.com_port;

public enum ExchangeStatus {
    DELIVERED,      // эхо совпало — данные в МК
    NO_RESPONSE,    // три попытки — тишина
    ECHO_MISMATCH,  // ответ есть, но первые 6 байт не совпали
    PORT_CLOSED     // порт не открыт
}
