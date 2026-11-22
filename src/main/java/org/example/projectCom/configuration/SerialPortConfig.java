package org.example.projectCom.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SerialPortProperties.class)
public class SerialPortConfig {

}
