package com.ektrepha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EktrephaApplication {

	public static void main(String[] args) {
		SpringApplication.run(EktrephaApplication.class, args);
	}


}
