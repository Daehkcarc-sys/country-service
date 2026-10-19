package com.countryservice.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.countryservice.demo.beans.Country;
import com.countryservice.demo.controllers.CountryController;
import com.countryservice.demo.services.CountryService;

// Error cases: the service throws, the controller must answer with the right HTTP status
@ExtendWith(MockitoExtension.class)
class ControllerErrorTests {

	@Mock
	CountryService countryService;

	@InjectMocks
	CountryController countryController;

	@Test
	void test_getAllCountries_notFound() {
		when(countryService.getAllCountries()).thenThrow(new RuntimeException("db down"));
		assertEquals(HttpStatus.NOT_FOUND, countryController.getCountries().getStatusCode());
	}

	@Test
	void test_getCountryByID_notFound() {
		when(countryService.getCountryById(99)).thenThrow(new NoSuchElementException());
		assertEquals(HttpStatus.NOT_FOUND, countryController.getCountryById(99).getStatusCode());
	}

	@Test
	void test_getCountryByName_notFound() {
		when(countryService.getCountryByName("Atlantis")).thenThrow(new NoSuchElementException());
		assertEquals(HttpStatus.NOT_FOUND, countryController.getCountryByName("Atlantis").getStatusCode());
	}

	@Test
	void test_addCountry_conflict() {
		Country country = new Country(1, "India", "Delhi");
		when(countryService.addCountry(country)).thenThrow(new NoSuchElementException());
		assertEquals(HttpStatus.CONFLICT, countryController.addCountry(country).getStatusCode());
	}

	@Test
	void test_updateCountry_conflict() {
		when(countryService.getCountryById(99)).thenThrow(new NoSuchElementException());
		ResponseEntity<Country> res = countryController.updateCountry(99, new Country(99, "Japan", "Tokyo"));
		assertEquals(HttpStatus.CONFLICT, res.getStatusCode());
	}

	@Test
	void test_deleteCountry_notFound() {
		when(countryService.getCountryById(99)).thenReturn(new Country(99, "Japan", "Tokyo"));
		doThrow(new NoSuchElementException()).when(countryService).deleteCountry(any(Country.class));
		assertEquals(HttpStatus.NOT_FOUND, countryController.deleteCountry(99).getStatusCode());
	}
}
