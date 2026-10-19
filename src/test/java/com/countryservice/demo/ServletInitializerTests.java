package com.countryservice.demo;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

// configure() is what Tomcat calls when the war is deployed
class ServletInitializerTests {

	static class TestableApplication extends CountryServiceApplication {
		SpringApplicationBuilder callConfigure(SpringApplicationBuilder builder) {
			return configure(builder);
		}
	}

	@Test
	void test_configure_registersApplicationSource() {
		SpringApplicationBuilder builder = new TestableApplication().callConfigure(new SpringApplicationBuilder());
		assertNotNull(builder.build());
	}
}
