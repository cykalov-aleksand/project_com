package org.example.projectCom.service;

import org.example.projectCom.dto.PanelTestResult;
import org.springframework.stereotype.Service;

@Service
public class PanelTestService {

    public PanelTestResult runTest(Long panelId, byte[] payload) {
        // ЗАГЛУШКА: всегда «доставлено». Здесь позже будет отправка в порт + сверка эха.
        return PanelTestResult.ok("Заглушка: кадр (" + payload.length + " байт) «отправлен»");
    }
}
