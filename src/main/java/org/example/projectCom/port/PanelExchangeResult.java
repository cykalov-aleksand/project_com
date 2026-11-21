package org.example.projectCom.port;

public record PanelExchangeResult(
        Long panelId,
        ExchangeStatus status,
        byte[] request,   // что отправили
        byte[] response,  // что получили (может быть null)
        String message) {

    public static PanelExchangeResult delivered(Long panelId, byte[] req, byte[] resp) {
        return new PanelExchangeResult(panelId, ExchangeStatus.DELIVERED, req, resp, null);
    }
    public static PanelExchangeResult failure(Long panelId, ExchangeStatus status, byte[] req, String msg) {
        return new PanelExchangeResult(panelId, status, req, null, msg);
    }
}

