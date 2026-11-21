package org.example.projectCom.service;

import com.fazecast.jSerialComm.SerialPort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@Service
public class SerialWorker {
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "serial-worker");
                t.setDaemon(true);
                return t;
            });

    public CompletableFuture<byte[]> sendAndAwait(SerialPort port, byte[] payload,
                                                  Duration timeout, int responseLength) {
        return CompletableFuture.supplyAsync(() -> {
            int written = port.writeBytes(payload, payload.length);
            if (written != payload.length) {
                throw new org.example.projectCom.service.errors.SerialPortException(
                        "Записано " + written + " из " + payload.length + " байт");
            }
            byte[] buf = new byte[responseLength];
            int read = port.readBytes(buf, buf.length);
            if (read < buf.length) {
                throw new org.example.projectCom.service.errors.SerialPortException(
                        "Прочитано " + read + " из " + responseLength + " байт" +
                                (read == -1 ? " (таймаут)" : ""));
            }
            return buf;
        }, executor);
    }
}
