package com.example;

import com.fazecast.jSerialComm.SerialPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;


public class Connector {

    private final String url;
    private final String token;
    private final String portName;   // null = имитатор
    private final int baud;
    private final Consumer<String> logConsumer;   // куда писать журнал

    private volatile boolean running = false;
    private volatile WebSocket ws = null;
    private volatile SerialPort port = null;
    private final AtomicBoolean authOk = new AtomicBoolean(false);
    private volatile CompletableFuture<Void> closed = new CompletableFuture<>();

    public Connector(String url, String token, String portName, int baud, Consumer<String> logConsumer) {
        this.url = url;
        this.token = token;
        this.portName = portName;
        this.baud = baud;
        this.logConsumer = logConsumer;
    }
    private void log(String line) {
        logConsumer.accept(line);
    }
    public boolean isSimulator() { return portName == null; }

    public void start() {
        running = true;
        Thread.ofVirtual().name("agent-main").start(this::runLoop);
    }

    public void stop() {
        running = false;
        WebSocket w = ws;
        if (w != null) w.sendClose(WebSocket.NORMAL_CLOSURE, "stop");
        SerialPort p = port;
        if (p != null) p.closePort();
        closed.complete(null);
    }

    private void runLoop() {
        try {
            while (running) {
                try {
                    if (!isSimulator()) {
                        port = openPort();
                        log("Открыт порт " + portName + " @ " + baud);
                        Thread.ofVirtual().name("serial-reader").start(this::serialReadLoop);
                    } else {
                        log("Режим имитатора: всё, что придёт с сервера, вернётся обратно");
                    }
                    connect();
                    closed.join();
                    if (!running) break;
                    log("Соединение закрыто, переподключение через 3 сек...");
                } catch (Exception e) {
                    log("Ошибка соединения: " + e.getMessage());
                }
                if (running) {
                    Thread.sleep(3000);
                    authOk.set(false);
                    closed = new CompletableFuture<>();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (port != null) port.closePort();
            log("Агент остановлен");
        }
    }

    private SerialPort openPort() {
        SerialPort p = SerialPort.getCommPort(portName);
        p.setBaudRate(baud);
        p.setNumDataBits(8);
        p.setNumStopBits(SerialPort.ONE_STOP_BIT);
        p.setParity(SerialPort.NO_PARITY);
        p.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
        p.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 500, 0);
        if (!p.openPort()) {
            throw new IllegalStateException("Не удалось открыть " + portName
                    + ". Проверьте, что порт не занят другим приложением.");
        }
        return p;
    }

    private void serialReadLoop() {
        byte[] buf = new byte[1024];
        SerialPort p = port;
        while (p != null && p.isOpen()) {
            int n = p.readBytes(buf, buf.length);
            if (n <= 0) continue;
            if (!authOk.get()) continue;
            WebSocket w = ws;
            if (w != null) {
                String text = new String(buf, 0, n, StandardCharsets.UTF_8);
                log(">>> Ответ порта: " + text.trim());
                w.sendText(text, true);
            }
        }
    }

    private void connect() {
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(url), new Listener())
                .join();
    }

    private class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            ws = webSocket;
            webSocket.sendText(token, true);   // первое сообщение — токен
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String msg = buffer.toString();
                buffer.setLength(0);

                if (msg.startsWith("AUTH_OK:")) {
                    authOk.set(true);
                    log("Авторизация пройдена: " + msg);
                } else {
                    log("<<< Команда с сервера: " + msg);
                    if (isSimulator()) {
                        // эхо: возвращаем ту же строку с небольшой задержкой
                        Thread.ofVirtual().start(() -> {
                            try { Thread.sleep(200); } catch (InterruptedException ignored) { }
                            WebSocket sock = ws;
                            if (sock != null && running) {
                                log(">>> Ответ (имитатор): " + msg);
                                sock.sendText(msg, true);
                            }
                        });
                    } else {
                        writeToSerial(msg);
                    }
                }
            }
            w.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
            log("Соединение закрыто: " + code + " " + reason);
            closed.complete(null);
            return null;
        }

        @Override
        public void onError(WebSocket w, Throwable error) {
            log("Ошибка WebSocket: " + error.getMessage());
            closed.complete(null);
        }
    }

    private void writeToSerial(String message) {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        port.writeBytes(data, data.length);
    }
}
