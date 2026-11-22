package org.example.projectCom.port;

import com.fazecast.jSerialComm.SerialPort;
import lombok.RequiredArgsConstructor;
import org.example.projectCom.dto.com_port.ExchangeStatus;
import org.example.projectCom.dto.com_port.PanelExchangeResult;
import org.example.projectCom.service.errors.SerialPortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;


@Service
@RequiredArgsConstructor
public class PortService {
    private static final Logger log = LoggerFactory.getLogger(PortService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration PER_EXCHANGE_TIMEOUT = Duration.ofSeconds(5);

    private final SerialPortService portService;
    private final SerialWorker worker;
    private final PanelFrameBuilder frames;

    // Один поток на все отправки — пакеты не перемешаются в линии
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "panel-exchange");
                t.setDaemon(true);
                return t;
            });

    // Твоя мапа: панель -> понятный статус
    private final Map<Long, String> lastStatus = new ConcurrentHashMap<>();

    public CompletableFuture<PanelExchangeResult> writePanelAsync(Long panelId, byte[] payload) {
        return CompletableFuture.supplyAsync(() -> {
            byte[] request = frames.buildWrite(payload);
            PanelExchangeResult result = exchange(panelId, request, request.length);

            // Пишем понятный статус в мапу
            String statusText;
            switch (result.status()) {
                case DELIVERED -> statusText = "Передано (эхо совпало)";
                case NO_RESPONSE -> statusText = "Не передано (нет ответа)";
                case ECHO_MISMATCH -> statusText = "Не передано (эхо не совпало)";
                case PORT_CLOSED -> statusText = "Не передано (порт закрыт)";
                default -> statusText = "Не передано (неизвестная ошибка)";
            }
            lastStatus.put(panelId, statusText);

            return result;
        }, executor);
    }

    private PanelExchangeResult exchange(Long panelId, byte[] request, int responseLength) {
        SerialPort port;
        try {
            port = portService.port();
        } catch (SerialPortException e) {
            return PanelExchangeResult.failure(panelId, ExchangeStatus.PORT_CLOSED, request, e.getMessage());
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                byte[] response = worker.sendAndAwait(port, request, PER_EXCHANGE_TIMEOUT, responseLength);

                if (echoMatches(request, response)) {
                    log.info("Панель {}: доставка подтверждена (попытка {})", panelId, attempt);
                    return PanelExchangeResult.delivered(panelId, request, response);
                }
                log.warn("Панель {}: эхо не совпало (попытка {})", panelId, attempt);

            } catch (Exception e) {
                log.warn("Панель {}: нет ответа (попытка {}): {}", panelId, attempt, e.getMessage());
                // Небольшая пауза перед повтором, чтобы не спамить линию
                try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }

        return PanelExchangeResult.failure(panelId, ExchangeStatus.NO_RESPONSE, request,
                "Ответ не получен за " + MAX_ATTEMPTS + " попыток");
    }

    private boolean echoMatches(byte[] request, byte[] response) {
        if (response == null || response.length < 6 || request.length < 6) return false;
        return Arrays.equals(
                Arrays.copyOfRange(request, 0, 6),
                Arrays.copyOfRange(response, 0, 6));
    }
    // Простой метод для контроллера: получить статус по ID панели
    public String getStatus(Long panelId) {
        return lastStatus.getOrDefault(panelId, "Ожидание");
    }

}