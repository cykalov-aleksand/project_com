package org.example.projectCom.port;

import com.fazecast.jSerialComm.SerialPort;
import lombok.RequiredArgsConstructor;
import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.service.SerialPortService;
import org.example.projectCom.service.SerialWorker;
import org.example.projectCom.service.panelService.cacheServicePanel.PanelServiceCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PortService {
    private final static Logger logger= LoggerFactory.getLogger(PortService.class);

    private static final int HEADER_LEN = 6;          // эхо-блок
    private static final int MAX_ATTEMPTS = 3;        // 1 попытка + 2 повтора
    private static final Duration PER_EXCHANGE_TIMEOUT = Duration.ofSeconds(5);

    private final SerialPortService portService;
    private final SerialWorker worker;
    private final PanelFrameBuilder frames;
    private final PanelServiceCache cachePanelService;

    // один поток — запросы не перемешаются в порту, очередь выстроит их сама
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "panel-exchange");
                t.setDaemon(true);
                return t;
            });

    // последняя мапа результатов — для polling'а из UI, если понадобится
    private final Map<Long, PanelExchangeResult> lastResults = new LinkedHashMap<>();

    // ==================== ЧТЕНИЕ ====================

    /** Чтение JSON одной панели: 1 попытка + 2 повтора, результат в мапу. */
    public CompletableFuture<PanelExchangeResult> readPanelJsonAsync(Long panelId) {
        return CompletableFuture.supplyAsync(() -> {
            PanelDto panel = cachePanelService.getPanelDtoById(panelId);
            PanelExchangeResult result = exchange(panel.getId(),
                    frames.buildRead(panel.getId()),
                    frames.readResponseLength());
            store(panel.getId(), result);
            return result;
        }, executor);
    }

    /** Чтение JSON всех панелей, где он не пустой. Асинхронно, мапа панель → результат. */
    public CompletableFuture<Map<Long, PanelExchangeResult>> readAllJsonAsync(Long objectId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<Long, PanelExchangeResult> results = new LinkedHashMap<>();
            List<PanelDto> panels = cachePanelService.findPanelsIdObject(objectId).stream()
                    .filter(p -> p.getListCondition() != null && !p.getListCondition().isBlank())
                    .toList();

            for (PanelDto panel : panels) {
                // «нет ответа → 2 повтора → к следующей панели» — внутри exchange
                PanelExchangeResult result = exchange(panel.getId(),
                        frames.buildRead(panel.getId()),
                        frames.readResponseLength());
                results.put(panel.getId(), result);
                store(panel.getId(), result);
            }
            return results;
        }, executor);
    }

    // ==================== ЗАПИСЬ ====================

    /** Запись payload в панель с проверкой эхо первых 6 байт. */
    public CompletableFuture<PanelExchangeResult> writePanelAsync(Long panelId, byte[] payload) {
        return CompletableFuture.supplyAsync(() -> {
            byte[] request = frames.buildWrite(payload);
            PanelExchangeResult result = exchange(panelId, request, frames.writeResponseLength());
            store(panelId, result);
            return result;
        }, executor);
    }

    // ==================== ЯДРО ОБМЕНА ====================

    /**
     * Один обмен: до MAX_ATTEMPTS попыток. Совпало эхо — DELIVERED,
     * исчерпали попытки — NO_RESPONSE/ECHO_MISMATCH. Исключения не летят наружу.
     */
    private PanelExchangeResult exchange(Long panelId, byte[] request, int responseLength) {
        SerialPort port;
        try {
            port = portService.port();
        } catch (org.example.projectCom.service.errors.SerialPortException e) {
            return PanelExchangeResult.failure(panelId, ExchangeStatus.PORT_CLOSED, request, e.getMessage());
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                byte[] response = worker.sendAndAwait(port, request,
                                PER_EXCHANGE_TIMEOUT, responseLength)
                        .get(PER_EXCHANGE_TIMEOUT.toSeconds() + 1, TimeUnit.SECONDS);

                if (echoMatches(request, response)) {
                    logger.info("панель {}: доставка подтверждена (попытка {})", panelId, attempt);
                    return PanelExchangeResult.delivered(panelId, request, response);
                }
                logger.warn("панель {}: эхо не совпало (попытка {})", panelId, attempt);

            } catch (Exception e) {
                // таймаут, недочёт байтов, прерывание — всё трактуем как «ответа нет»
                logger.warn("панель {}: ответа нет (попытка {}): {}", panelId, attempt, e.getMessage());
            }
        }

        return PanelExchangeResult.failure(panelId, ExchangeStatus.NO_RESPONSE, request,
                "Ответ не получен за " + MAX_ATTEMPTS + " попыток");
    }

    /** Первые 6 байт запроса должны быть равны первым 6 байтам ответа. */
    private boolean echoMatches(byte[] request, byte[] response) {
        if (response == null || response.length < HEADER_LEN || request.length < HEADER_LEN) {
            return false;
        }
        return Arrays.equals(
                Arrays.copyOfRange(request, 0, HEADER_LEN),
                Arrays.copyOfRange(response, 0, HEADER_LEN));
    }

    private void store(Long panelId, PanelExchangeResult result) {
        synchronized (lastResults) {
            lastResults.put(panelId, result);
        }
    }

    public synchronized Map<Long, PanelExchangeResult> snapshotResults() {
        synchronized (lastResults) {
            return new LinkedHashMap<>(lastResults);
        }
    }
}
