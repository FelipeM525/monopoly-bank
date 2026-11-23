package com.asd.monopoly_bank;

import com.asd.monopoly_bank.config.GameRulesProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GameRulesProperties.class)
public class MonopolyBankApplication {

	public static void main(String[] args) {
		SpringApplication.run(MonopolyBankApplication.class, args);
	}

}
