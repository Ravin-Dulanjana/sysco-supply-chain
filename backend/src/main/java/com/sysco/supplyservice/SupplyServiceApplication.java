package com.sysco.supplyservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SupplyServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SupplyServiceApplication.class, args);
	}

}
