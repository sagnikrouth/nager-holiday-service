package com.example.nager.web;

import com.example.nager.NagerHolidaysApplication;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(classes = NagerHolidaysApplication.class)
@AutoConfigureWebTestClient
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WireMockIT {
    // Start WireMock early so DynamicPropertySource can reference the port reliably
    static WireMockServer wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    @Autowired WebTestClient webClient;

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
        // set base URL to WireMock host:port (the client will append the API path)
        registry.add("nager.base-url", () -> "http://localhost:" + wm.port());
    }


    @Test
    void lastThree_usesWireMock() throws Exception {
        // Stubs for current & previous year - note paths are relative to base-url (/api/v3)
        wm.stubFor(get(urlEqualTo("/PublicHolidays/2025/GB"))
                .willReturn(okJson("[{\"date\":\"2025-01-01\",\"name\":\"New Year's Day\",\"localName\":\"New Year's Day\"}]")));

        wm.stubFor(get(urlEqualTo("/PublicHolidays/2024/GB"))
                .willReturn(okJson("[{\"date\":\"2024-12-25\",\"name\":\"Christmas Day\",\"localName\":\"Christmas Day\"}]")));

        webClient.get()
                .uri("/api/holidays/last-3/GB")
                .exchange()
                .expectStatus().isOk();
    }

}
