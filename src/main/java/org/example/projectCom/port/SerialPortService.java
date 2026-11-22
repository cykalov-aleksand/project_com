package org.example.projectCom.port;


import com.fazecast.jSerialComm.SerialPort;
import org.example.projectCom.configuration.SerialPortProperties;
import org.example.projectCom.service.errors.SerialPortException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class SerialPortService {

    private final SerialPortProperties props; // настройки из файла — только читаем
    private SerialPort currentPort;           // состояние сеанса — живёт здесь

    public SerialPortService(SerialPortProperties props) {
        this.props = props;
    }
    public SerialPort port() {
        if (!isOpen()) {
            throw new SerialPortException("Порт не открыт. Откройте COM-порт перед обменом.");
        }
        return currentPort;
    }
    public void open(String portName) {
        close();
        SerialPort port = waitForPort(portName, 3000);
        currentPort = port;

        currentPort.setBaudRate(props.getBaudRate());
        currentPort.setNumDataBits(props.getDataBits());
        currentPort.setParity(props.getParity());
        currentPort.setNumStopBits(props.getStopBits());
        currentPort.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_BLOCKING,
                props.getReadTimeout(),
                props.getWriteTimeout());

        if (!currentPort.openPort()) {
            int code = currentPort.getLastErrorCode();
            currentPort = null;
            throw new SerialPortException(
                    "Не удалось открыть порт " + portName + " (код ошибки: " + code + ")");
        }
    }

    /**
     * Ждёт появления порта до timeoutMs: система может ещё не завершить
     * перечисление USB-устройства на момент первого запроса.
     */
    private SerialPort waitForPort(String portName, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        do {
            for (SerialPort p : SerialPort.getCommPorts()) {
                if (p.getSystemPortName().equalsIgnoreCase(portName)) {
                    return p; // объект готов к конфигурации и openPort()
                }
            }
            if (System.currentTimeMillis() >= deadline) break;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SerialPortException("Ожидание порта прервано");
            }
        } while (true);
        throw new SerialPortException(
                "Порт " + portName + " не найден за " + (timeoutMs / 1000) + " с. " +
                        "Доступны: " + Arrays.stream(SerialPort.getCommPorts())
                        .map(SerialPort::getSystemPortName)
                        .collect(Collectors.joining(", ")));
    }
    public void openDefault() {
        String name = props.getPortName();
        if (name == null || name.isBlank()) {
            throw new SerialPortException("Порт по умолчанию не настроен (serial.port-name)");
        }
        open(name);
    }

    public synchronized void close() {
        if (currentPort != null && currentPort.isOpen()) {
            currentPort.closePort();
        }
        currentPort = null;
    }

    public boolean isOpen() {
        return currentPort != null && currentPort.isOpen();
    }

    public String getCurrentPortName() {
        return currentPort != null ? currentPort.getSystemPortName() : null;
    }

    private SerialPort buildPort(String portName) {
        SerialPort port = SerialPort.getCommPort(portName);
        if (port == null) {
            throw new SerialPortException("Порт не найден: " + portName);
        }
        return port;
    }
}