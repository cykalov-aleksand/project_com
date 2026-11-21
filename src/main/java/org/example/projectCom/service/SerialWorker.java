package org.example.projectCom.service;

import com.fazecast.jSerialComm.SerialPort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fazecast.jSerialComm.SerialPort;
import org.example.projectCom.service.errors.SerialPortException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@Service
public class SerialWorker {
    public byte[] sendAndAwait(SerialPort port, byte[] payload, Duration timeout, int responseLength) {
        int written = port.writeBytes(payload, payload.length);
        if (written != payload.length) {
            throw new SerialPortException("Записано " + written + " из " + payload.length + " байт");
        }

        byte[] buf = new byte[responseLength];
        int read = port.readBytes(buf, buf.length); // блокирующее чтение до таймаута порта
        if (read < buf.length) {
            throw new SerialPortException("Прочитано " + read + " из " + responseLength + " байт");
        }
        return buf;
    }
}
