package org.example.projectCom.service;

import lombok.RequiredArgsConstructor;
import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.dto.PanelTestResult;
import org.example.projectCom.dto.com_port.ExchangeStatus;
import org.example.projectCom.dto.com_port.PanelExchangeResult;
import org.example.projectCom.dto.enum_panels.PanelNameOption;
import org.example.projectCom.port.PortService;
import org.example.projectCom.service.panelService.cacheServicePanel.PanelServiceCache;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PanelTestService {

    private final PanelServiceCache panelServiceCache;
    private final PortService portService;
    /**
     * ЗАГЛУШКА: используется только для массового теста (requestObject),
     * чтобы быстро проверить логику отчёта без реального железа.
     */
    public PanelTestResult runTest(Long panelId, byte[] payload) {
        return PanelTestResult.ok("Заглушка: кадр (" + payload.length + " байт) «отправлен»");
    }

    public String requestObject(List<PanelDto> panels) {
        int sent = 0;
        List<String> problems = new ArrayList<>();

        for (PanelDto panel : panels) {
            String name = panel.getNameElement().getDisplayName();
            if (panel.getListCondition() == null || panel.getListCondition().isBlank()) {
                problems.add(name + ": не настроена");
                continue;
            }
            try {
                byte[] payload = OutputUtils.fromJsonbHex(panel.getListCondition());
                PanelTestResult r = runTest(panel.getId(), payload);
                if (r.ok()) {
                    sent++;
                } else {
                    problems.add(name + ": " + r.text());
                }
            } catch (IllegalArgumentException e) {
                problems.add(name + ": " + e.getMessage());
            }
        }

        if (problems.isEmpty()) {
            return "Отправлено " + sent + " из " + panels.size() + " панелей";
        } else {
            return "Отправлено " + sent + " из " + panels.size()
                    + ". Проблемы: " + String.join("; ", problems);
        }
    }


    /**
     * Реальная отправка с учётом протокола (цепочки панелей).
     * Логика:
     * - Если выбран ГЕНЕРАТОР: сначала Панель синхронизации, затем сам генератор.
     * - Если выбрана СИНХРОНИЗАЦИЯ: сначала Панель коммутации выходов, затем синхронизация.
     * - Если ВЫВОД (коммутация): только её.
     */

    public String question(Long panelId, Long objectId) {
        PanelDto currentPanel = panelServiceCache.getPanelDtoById(panelId);

        // Базовая проверка текущей панели
        if (currentPanel == null) {
            return "Панель не найдена";
        }
        if (currentPanel.getListCondition() == null || currentPanel.getListCondition().isBlank()) {
            return "Панель не настроена";
        }

        // Получаем все панели для этого объекта, чтобы найти нужные по типу
        List<PanelDto> allPanels = panelServiceCache.findPanelsIdObject(objectId);
        if (allPanels == null || allPanels.isEmpty()) {
            return "Не найдены панели для объекта " + objectId;
        }

        // Строим порядок отправки
        List<Long> sendOrder = buildSendOrder(currentPanel, allPanels);

        StringBuilder logText = new StringBuilder();
        boolean allOk = true;

        for (Long idToSend : sendOrder) {
            PanelDto target = panelServiceCache.getPanelDtoById(idToSend);
            if (target == null) {
                logText.append("Панель ID ").append(idToSend).append(" не найдена; ");
                allOk = false;
                continue;
            }
            if (target.getListCondition() == null || target.getListCondition().isBlank()) {
                logText.append(target.getNameElement().getDisplayName()).append(" не настроена; ");
                allOk = false;
                continue;
            }

            byte[] payload = OutputUtils.fromJsonbHex(target.getListCondition());
            System.err.println("Отправка: " + target.getNameElement().getDisplayName() + " (ID=" + idToSend + ") -> " + Arrays.toString(payload));

            try {
                // .join() ждёт реальный ответ от порта
                PanelExchangeResult result = portService.writePanelAsync(idToSend, payload).join();

                if (result.status() == ExchangeStatus.DELIVERED) {
                    logText.append(target.getNameElement().getDisplayName()).append(" OK; ");
                } else {
                    logText.append(target.getNameElement().getDisplayName())
                            .append(" ошибка: ").append(result.message()).append("; ");
                    allOk = false;
                }
            } catch (Exception e) {
                logText.append(target.getNameElement().getDisplayName())
                        .append(" исключение: ").append(e.getMessage()).append("; ");
                allOk = false;
            }
        }

        if (allOk) {
            return "Передано (все панели в цепочке успешно обработаны)";
        } else {
            return "Не передано: " + logText.toString();
        }
    }


    /**
     * Формирует список ID панелей в правильном порядке отправки.
     */
    private List<Long> buildSendOrder(PanelDto current, List<PanelDto> allPanels) {
        List<Long> order = new ArrayList<>();
        PanelNameOption currentType = current.getNameElement();

        Long outputId = findPanelIdByType(allPanels, PanelNameOption.PANEL_OUTPUT_SWITCHING);
        Long syncId = findPanelIdByType(allPanels, PanelNameOption.PANEL_SYNCHRONIZATION);

        // Для ГЕНЕРАТОРА: вывод → синхронизация → генератор
        if (currentType.isGeneratorType()) {
            if (outputId != null) {
                order.add(outputId);
            }
            if (syncId != null) {
                order.add(syncId);
            }
            order.add(current.getId());
        }

        // Для СИНХРОНИЗАЦИИ: вывод → синхронизация
        else if (currentType == PanelNameOption.PANEL_SYNCHRONIZATION) {
            if (outputId != null) {
                order.add(outputId);
            }
            order.add(current.getId());
        }

        // Для ВЫВОДА: только вывод
        else {
            order.add(current.getId());
        }

        return order;
    }
    /**
     * Вспомогательный метод: находит ID панели по её типу (PanelNameOption)
     */
    private Long findPanelIdByType(List<PanelDto> panels, PanelNameOption targetType) {
        return panels.stream()
                .filter(p -> p.getNameElement() == targetType)
                .findFirst()
                .map(PanelDto::getId)
                .orElse(null);
    }
}



