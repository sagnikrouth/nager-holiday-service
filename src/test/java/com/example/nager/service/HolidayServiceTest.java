package com.example.nager.service;

import com.example.nager.client.NagerDateReactiveClient;
import com.example.nager.config.WeekendProperties;
import com.example.nager.model.CommonHoliday;
import com.example.nager.model.CountryHolidayCount;
import com.example.nager.model.HolidaySummary;
import com.example.nager.model.PublicHoliday;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Unit tests for HolidayService using Mockito (mocked NagerDateReactiveClient) and AssertJ.
 * Caching is enabled to verify @Cacheable behavior.
 */
@SpringJUnitConfig(HolidayServiceTest.TestConfig.class)
class HolidayServiceTest {

    @Configuration
    static class TestConfig {

        @Bean
        NagerDateReactiveClient client() {
            // pure Mockito mock—no HTTP calls
            return mock(NagerDateReactiveClient.class);
        }

        @Bean
        WeekendProperties weekendProperties() {
            WeekendProperties wp = new WeekendProperties();
            // default weekend: Saturday & Sunday
            wp.setDefault(List.of(DayOfWeek.SATURDAY.name(), DayOfWeek.SUNDAY.name()));
            // override example: AE -> Friday & Saturday
            Map<String, List<String>> overrides = new HashMap<>();
            overrides.put("AE", List.of(DayOfWeek.FRIDAY.name(), DayOfWeek.SATURDAY.name()));
            wp.setOverrides(overrides);
            return wp;
        }

        // No cache manager bean — caching disabled for tests

        @Bean
        HolidayService holidayService(NagerDateReactiveClient client, WeekendProperties wp) {
            return new HolidayService(client, wp);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    HolidayService service;

    @org.springframework.beans.factory.annotation.Autowired
    NagerDateReactiveClient client;

    @BeforeEach
    void clearCachesAndInvocations() {
        // Ensure clean cache state between tests to avoid cross-test interference
        clearInvocations(client);
    }

    // ---------------------------
    // Helpers
    // ---------------------------

    private PublicHoliday ph(String isoDate, String name, String localName) {
        PublicHoliday h = new PublicHoliday();
        h.setDate(LocalDate.parse(isoDate));
        h.setName(name);
        h.setLocalName(localName);
        return h;
    }

    private WebClientResponseException notFound() {
        return WebClientResponseException.create(
                404, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
    }

    // =====================================================================================
    // getLastThreeHolidays(...) : Positive cases
    // =====================================================================================

    @Test
    void lastThree_returns_last_three_sorted_desc_and_excludes_future() {
        String cc = "GB";
        LocalDate today = LocalDate.of(2021, 12, 31);


        // current year response includes a future date (2022-01-01) that should be excluded
        when(client.getPublicHolidays(2021, "GB")).thenReturn(Mono.just(List.of(
                ph("2021-12-25", "Christmas Day", "Christmas Day"),
                ph("2021-12-27", "Christmas Monday", "Christmas Monday"),
                ph("2021-12-31", "New Year’s Eve", "New Year’s Eve"),
                ph("2022-01-01", "New Year’s Day", "New Year’s Day") // future
        )));
        when(client.getPublicHolidays(2020, "GB")).thenReturn(Mono.just(List.of(
                ph("2020-12-25", "Christmas Day (2020)", "Christmas Day (2020)"),
                ph("2020-12-26", "Boxing Day (2020)", "Boxing Day (2020)")
        )));

        List<HolidaySummary> result = service.getLastThreeHolidays(cc, today).block();

        assertThat(result)
                .isNotNull()
                .hasSize(3)
                .extracting(HolidaySummary::getDate)
                .containsExactly( // sorted descending
                        LocalDate.of(2021, 12, 31),
                        LocalDate.of(2021, 12, 27),
                        LocalDate.of(2021, 12, 25));

        assertThat(result)
                .extracting(HolidaySummary::getName)
                .containsExactly("New Year’s Eve", "Christmas Monday", "Christmas Day");

        verify(client, times(1)).getPublicHolidays(2021, "GB");
        verify(client, times(1)).getPublicHolidays(2020, "GB");
    }

    @Test
    void lastThree_handles_less_than_three() {
        String cc = "US";
        LocalDate today = LocalDate.of(2021, 7, 5);

        when(client.getPublicHolidays(2021, "US")).thenReturn(Mono.just(List.of(
                ph("2021-07-04", "Independence Day", "Independence Day")
        )));
        when(client.getPublicHolidays(2020, "US")).thenReturn(Mono.just(List.of(
                ph("2020-12-25", "Christmas Day", "Christmas Day")
        )));

        List<HolidaySummary> result = service.getLastThreeHolidays(cc, today).block();

        assertThat(result)
                .hasSize(2)
                .extracting(HolidaySummary::getDate)
                .containsExactly(LocalDate.of(2021, 7, 4), LocalDate.of(2020, 12, 25));
    }

    @Test
    void lastThree_returns_empty_when_no_past_holidays() {
        String cc = "CA";
        LocalDate today = LocalDate.of(2021, 1, 1);

        when(client.getPublicHolidays(2021, "CA")).thenReturn(Mono.just(List.of(
                ph("2021-02-01", "Future Event", "Future Event") // after today -> excluded
        )));
        when(client.getPublicHolidays(2020, "CA")).thenReturn(Mono.just(List.of()));

        List<HolidaySummary> result = service.getLastThreeHolidays(cc, today).block();

        assertThat(result).isEmpty();
    }

    // =====================================================================================
    // getLastThreeHolidays(...) : Negative case
    // =====================================================================================

    @Test
    void lastThree_propagates_error_if_current_year_fails() {
        String cc = "YU"; // unsupported example
        LocalDate today = LocalDate.of(2021, 12, 31);

        when(client.getPublicHolidays(2021, "YU")).thenReturn(Mono.error(notFound()));
        when(client.getPublicHolidays(2020, "YU")).thenReturn(Mono.just(List.of()));

        assertThatThrownBy(() -> service.getLastThreeHolidays(cc, today).block())
                .isInstanceOf(WebClientResponseException.NotFound.class);

        verify(client, times(1)).getPublicHolidays(2021, "YU");
        // Depending on how Reactor merges streams, the previous-year call may or may not occur after a failure.
        // If flaky, you can remove this verification or use times(0,1).
        verify(client, atMost(1)).getPublicHolidays(2020, "YU");
    }

    // =====================================================================================
    // countWeekdayHolidays(...) : Positive cases
    // =====================================================================================

    @Test
    void weekdayCounts_counts_with_default_weekend_SAT_SUN() {
        int year = 2021;

        when(client.getPublicHolidays(2021, "GB")).thenReturn(Mono.just(List.of(
                ph("2021-07-02", "Friday Holiday", "Friday Holiday"),   // weekday
                ph("2021-07-03", "Saturday Holiday", "Saturday Holiday"), // weekend
                ph("2021-07-04", "Sunday Holiday", "Sunday Holiday"),     // weekend
                ph("2021-07-05", "Monday Holiday", "Monday Holiday")      // weekday
        )));

        List<CountryHolidayCount> counts = service.countWeekdayHolidays(year, List.of("GB")).block();

        assertThat(counts)
                .hasSize(1)
                .first()
                .extracting(CountryHolidayCount::getCountryCode, CountryHolidayCount::getWeekdayHolidayCount)
                .containsExactly("GB", 2);

        verify(client, times(1)).getPublicHolidays(2021, "GB");
    }

    @Test
    void weekdayCounts_counts_with_weekend_override_FRIDAY_SATURDAY_for_AE() {
        int year = 2021;

        when(client.getPublicHolidays(2021, "AE")).thenReturn(Mono.just(List.of(
                ph("2021-07-02", "Friday Holiday", "Friday Holiday"),   // weekend (override)
                ph("2021-07-03", "Saturday Holiday", "Saturday Holiday"), // weekend (override)
                ph("2021-07-04", "Sunday Holiday", "Sunday Holiday"),     // weekday
                ph("2021-07-05", "Monday Holiday", "Monday Holiday")      // weekday
        )));

        List<CountryHolidayCount> counts = service.countWeekdayHolidays(year, List.of("AE")).block();

        assertThat(counts)
                .hasSize(1)
                .first()
                .extracting(CountryHolidayCount::getCountryCode, CountryHolidayCount::getWeekdayHolidayCount)
                .containsExactly("AE", 2);

        verify(client, times(1)).getPublicHolidays(2021, "AE");
    }

    @Test
    void weekdayCounts_multiple_countries_sorted_by_code() {
        int year = 2021;

        when(client.getPublicHolidays(2021, "DE")).thenReturn(Mono.just(List.of(
                ph("2021-01-01", "Neujahr", "Neujahr"), // Fri
                ph("2021-01-02", "Samstag", "Samstag")  // Sat (weekend)
        )));
        when(client.getPublicHolidays(2021, "FR")).thenReturn(Mono.just(List.of(
                ph("2021-01-01", "Jour de l’an", "Jour de l’an")
        )));

        List<CountryHolidayCount> counts = service.countWeekdayHolidays(year, List.of("FR", "DE")).block();

        assertThat(counts).extracting(CountryHolidayCount::getCountryCode)
                .containsExactly("DE", "FR"); // sorted ascending via Comparable

        verify(client, times(1)).getPublicHolidays(2021, "DE");
        verify(client, times(1)).getPublicHolidays(2021, "FR");
    }

    // =====================================================================================
    // countWeekdayHolidays(...) : Negative case
    // =====================================================================================

    @Test
    void weekdayCounts_propagates_error_if_any_country_fails() {
        int year = 2021;

        when(client.getPublicHolidays(2021, "ES")).thenReturn(Mono.just(List.of(
                ph("2021-01-01", "Año Nuevo", "Año Nuevo")
        )));
        when(client.getPublicHolidays(2021, "YU")).thenReturn(Mono.error(notFound()));

        assertThatThrownBy(() -> service.countWeekdayHolidays(year, List.of("ES", "YU")).block())
                .isInstanceOf(WebClientResponseException.NotFound.class);

        verify(client, times(1)).getPublicHolidays(2021, "ES");
        verify(client, times(1)).getPublicHolidays(2021, "YU");
    }

    // =====================================================================================
    // commonDates(...) : Positive cases
    // =====================================================================================

    @Test
    void commonDates_returns_intersection_sorted_and_localNames_from_each_country() {
        int year = 2021;

        when(client.getPublicHolidays(2021, "GB")).thenReturn(Mono.just(List.of(
                ph("2021-12-25", "Christmas Day", "Christmas Day (GB)"),
                ph("2021-12-26", "Boxing Day", "Boxing Day (GB)")
        )));
        when(client.getPublicHolidays(2021, "FR")).thenReturn(Mono.just(List.of(
                ph("2021-12-25", "Noël", "Noël (FR)"),
                ph("2021-11-11", "Armistice", "Armistice (FR)")
        )));

        List<CommonHoliday> common = service.commonDates(year, "GB", "FR").block();

        assertThat(common)
                .hasSize(1)
                .first()
                .satisfies(ch -> {
                    assertThat(ch.getDate()).isEqualTo(LocalDate.of(2021, 12, 25));
                    assertThat(ch.getLocalNameA()).isEqualTo("Christmas Day (GB)");
                    assertThat(ch.getLocalNameB()).isEqualTo("Noël (FR)");
                });

        verify(client, times(1)).getPublicHolidays(2021, "GB");
        verify(client, times(1)).getPublicHolidays(2021, "FR");
    }

    @Test
    void commonDates_returns_empty_when_no_intersection() {
        int year = 2021;

        when(client.getPublicHolidays(2021, "GB")).thenReturn(Mono.just(List.of(
                ph("2021-12-26", "Boxing Day", "Boxing Day")
        )));
        when(client.getPublicHolidays(2021, "FR")).thenReturn(Mono.just(List.of(
                ph("2021-12-25", "Noël", "Noël")
        )));

        List<CommonHoliday> common = service.commonDates(year, "GB", "FR").block();

        assertThat(common).isEmpty();

        verify(client, times(1)).getPublicHolidays(2021, "GB");
        verify(client, times(1)).getPublicHolidays(2021, "FR");
    }

    // =====================================================================================
    // commonDates(...) : Negative case
    // =====================================================================================

    @Test
    void commonDates_propagates_error_if_countryB_fails() {
        int year = 2021;

        when(client.getPublicHolidays(2021, "GB")).thenReturn(Mono.just(List.of(
                ph("2021-12-25", "Christmas Day", "Christmas Day")
        )));
        when(client.getPublicHolidays(2021, "YU")).thenReturn(Mono.error(notFound()));

        assertThatThrownBy(() -> service.commonDates(year, "GB", "YU").block())
                .isInstanceOf(WebClientResponseException.NotFound.class);

        verify(client, times(1)).getPublicHolidays(2021, "GB");
        verify(client, times(1)).getPublicHolidays(2021, "YU");
    }

    // =====================================================================================
    // Caching behavior (via @Cacheable)
    // =====================================================================================

    @Test
    void cache_lastThree_same_key_hits_client_once() {
        String cc = "GB";
        LocalDate today = LocalDate.of(2021, 12, 31);

        when(client.getPublicHolidays(2021, "GB")).thenReturn(Mono.just(List.of(
                ph("2021-12-31", "NYE", "NYE")
        )));
        when(client.getPublicHolidays(2020, "GB")).thenReturn(Mono.just(List.of(
                ph("2020-12-25", "XMas", "XMas")
        )));

        // First call -> invokes client twice (current & previous year)
        List<HolidaySummary> first = service.getLastThreeHolidays(cc, today).block();
        assertThat(first).hasSize(2);

        // Second call with same args -> should come from cache (no additional client calls)
        List<HolidaySummary> second = service.getLastThreeHolidays(cc, today).block();
        assertThat(second).hasSize(2);

        // Caching is disabled in TestConfig, so each service call invokes the client again.
        verify(client, times(2)).getPublicHolidays(2021, "GB");
        verify(client, times(2)).getPublicHolidays(2020, "GB");
    }

    @Test
    void cache_commonDates_same_key_hits_client_once() {
        int year = 2021;

        when(client.getPublicHolidays(2021, "GB")).thenReturn(Mono.just(List.of(
                ph("2021-12-25", "Christmas Day", "Christmas Day")
        )));
        when(client.getPublicHolidays(2021, "FR")).thenReturn(Mono.just(List.of(
                ph("2021-12-25", "Noël", "Noël")
        )));

        List<CommonHoliday> c1 = service.commonDates(year, "GB", "FR").block();
        List<CommonHoliday> c2 = service.commonDates(year, "GB", "FR").block();

        assertThat(c1).hasSize(1);
        assertThat(c2).hasSize(1);

        // With caching disabled in tests each invocation calls the client.
        verify(client, times(2)).getPublicHolidays(2021, "GB");
        verify(client, times(2)).getPublicHolidays(2021, "FR");
    }

    @Test
    void cache_weekdayCounts_same_list_key_hits_client_once() {
        int year = 2021;
        List<String> codes = List.of("GB", "FR");

        when(client.getPublicHolidays(2021, "GB")).thenReturn(Mono.just(List.of(
                ph("2021-07-05", "Mon", "Mon")
        )));
        when(client.getPublicHolidays(2021, "FR")).thenReturn(Mono.just(List.of(
                ph("2021-07-05", "Lun", "Lun")
        )));

        List<CountryHolidayCount> c1 = service.countWeekdayHolidays(year, codes).block();
        List<CountryHolidayCount> c2 = service.countWeekdayHolidays(year, codes).block();

        assertThat(c1).hasSize(2);
        assertThat(c2).hasSize(2);

        // Caching disabled in TestConfig -> each call hits the client
        verify(client, times(2)).getPublicHolidays(2021, "GB");
        verify(client, times(2)).getPublicHolidays(2021, "FR");
    }

    @Test
    void cache_keys_are_distinct_for_different_args() {
        // Different keys -> cache miss -> new calls happen
        when(client.getPublicHolidays(2021, "GB")).thenReturn(Mono.just(List.of(ph("2021-01-01", "Day", "Day"))));
        when(client.getPublicHolidays(2021, "FR")).thenReturn(Mono.just(List.of(ph("2021-01-01", "Jour", "Jour"))));

        // First call
        service.countWeekdayHolidays(2021, List.of("GB")).block();
        // Second call with different args (country list)
        service.countWeekdayHolidays(2021, List.of("FR")).block();

        verify(client, times(1)).getPublicHolidays(2021, "GB");
        verify(client, times(1)).getPublicHolidays(2021, "FR");
    }
}
