package com.countryservice.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class CountryServiceApplication extends SpringBootServletInitializer {

	// Entry point when deployed as a war on an external Tomcat
	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(CountryServiceApplication.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(CountryServiceApplication.class, args);
	}

}
