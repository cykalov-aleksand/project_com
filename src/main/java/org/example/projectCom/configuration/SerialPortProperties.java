package org.example.projectCom.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "serial")
public class SerialPortProperties {
    private String portName = "";
    private int baudRate = 9600;
    private int dataBits = 8;
    private int parity = 0;
    private int stopBits = 1;
    private int readTimeout = 2000;
    private int writeTimeout = 0;
}
