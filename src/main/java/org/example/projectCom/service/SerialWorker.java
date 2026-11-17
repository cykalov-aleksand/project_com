package org.example.projectCom.service;

import com.fazecast.jSerialComm.SerialPort;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SerialWorker {
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "serial-worker");
                t.setDaemon(true);
                return t;
            });

    public CompletableFuture<byte[]> sendAndAwait(SerialPort port,
                                                  byte[] payload,
                                                  Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            port.writeBytes(payload, payload.length);
            byte[] buf = new byte[6];
            port.readBytes(buf, 6); // вернёт -1 при таймауте
            return buf;
        }, executor);
    }
}
