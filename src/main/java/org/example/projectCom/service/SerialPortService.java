package org.example.projectCom.service;


import com.fazecast.jSerialComm.SerialPort;
import org.example.projectCom.configuration.SerialPortProperties;
import org.example.projectCom.service.errors.SerialPortException;
import org.springframework.stereotype.Component;

@Component
public class SerialPortService {

    private final SerialPortProperties props; // настройки из файла — только читаем
    private SerialPort currentPort;           // состояние сеанса — живёт здесь

    public SerialPortService(SerialPortProperties props) {
        this.props = props;
    }

    public void open(String portName) {
        close();
        if (SerialPort.getCommPort(portName) == null) {
            throw new SerialPortException(
                    "Порт " + portName + " не найден. Возможно, устройство переподключилось. Обновите страницу.");
        }
        currentPort = buildPort(portName);
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
    public void openDefault() {
        String name = props.getPortName();
        if (name == null || name.isBlank()) {
            throw new SerialPortException("Порт по умолчанию не настроен (serial.port-name)");
        }
        open(name);
    }

    public void close() {
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