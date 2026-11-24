package com.asd.monopoly_bank.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI monopolyBankOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("The Bank of Banco Imobiliário API")
						.version("1.0.0")
						.description(
								"REST API for table banking in Banco Imobiliário. "
										+ "All monetary values are represented in integer centavos (BIGINT/long)."));
	}
}
