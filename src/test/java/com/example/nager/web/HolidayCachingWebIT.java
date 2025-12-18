package com.example.nager.web;

import com.example.nager.NagerHolidaysApplication;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(classes = NagerHolidaysApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HolidayCachingWebIT {

    // WireMock server for backend Nager.Date API
    static WireMockServer wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @Autowired
    WebTestClient webClient;

    static {
        wm.start();
        WireMock.configureFor("localhost", wm.port());
    }

    @AfterAll
    static void stopWireMock(){
        if (wm != null && wm.isRunning()) wm.stop();
    }

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry){
        // client appends API paths like /PublicHolidays/... so base-url is host:port
        registry.add("nager.base-url", () -> "http://localhost:" + wm.port());
    }

    @TestConfiguration
    @EnableCaching
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            // simple in-memory caches for deterministic behavior in tests
            return new ConcurrentMapCacheManager("lastThree", "weekdayCounts", "commonDates", "publicHolidays");
        }
    }

    @Test
    void controller_calls_are_cached_and_back_end_is_invoked_once() {
        // stub current and previous year endpoints used by the controller/service
        wm.stubFor(get(urlEqualTo("/PublicHolidays/2025/GB"))
                .willReturn(okJson("[{\"date\":\"2025-01-01\",\"name\":\"New Year's Day\",\"localName\":\"New Year's Day\"}]")));

        wm.stubFor(get(urlEqualTo("/PublicHolidays/2024/GB"))
                .willReturn(okJson("[{\"date\":\"2024-12-25\",\"name\":\"Christmas Day\",\"localName\":\"Christmas Day\"}]")));

        // First call should trigger backend requests
        webClient.get()
                .uri("/api/holidays/last-3/GB")
                .exchange()
                .expectStatus().isOk();

        // Second call with same args should be served from cache (no additional backend calls)
        webClient.get()
                .uri("/api/holidays/last-3/GB")
                .exchange()
                .expectStatus().isOk();

        // Verify WireMock received exactly 1 request per year endpoint
        WireMock.verify(1, getRequestedFor(urlEqualTo("/PublicHolidays/2025/GB")));
        WireMock.verify(1, getRequestedFor(urlEqualTo("/PublicHolidays/2024/GB")));
    }
}

