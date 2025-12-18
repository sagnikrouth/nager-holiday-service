package com.example.nager.service;

import com.example.nager.client.NagerDateReactiveClient;
import com.example.nager.config.WeekendProperties;
import com.example.nager.model.PublicHoliday;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(HolidayServiceCachingIT.TestConfig.class)
public class HolidayServiceCachingIT {

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        NagerDateReactiveClient client() {
            return mock(NagerDateReactiveClient.class);
        }

        @Bean
        WeekendProperties weekendProperties() {
            WeekendProperties wp = new WeekendProperties();
            wp.setDefault(List.of(DayOfWeek.SATURDAY.name(), DayOfWeek.SUNDAY.name()));
            Map<String, List<String>> overrides = new HashMap<>();
            overrides.put("AE", List.of(DayOfWeek.FRIDAY.name(), DayOfWeek.SATURDAY.name()));
            wp.setOverrides(overrides);
            return wp;
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("lastThree", "weekdayCounts", "commonDates", "publicHolidays");
        }

        @Bean
        HolidayService holidayService(NagerDateReactiveClient client, WeekendProperties wp) {
            return new HolidayService(client, wp);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    HolidayService service;

    @org.springframework.beans.factory.annotation.Autowired
    NagerDateReactiveClient client;

    private PublicHoliday ph(String isoDate, String name) {
        PublicHoliday h = new PublicHoliday();
        h.setDate(LocalDate.parse(isoDate));
        h.setName(name);
        h.setLocalName(name);
        return h;
    }

    private NagerDateReactiveClient targetMock() {
        try {
            if (AopUtils.isAopProxy(client) && client instanceof Advised) {
                Object target = ((Advised) client).getTargetSource().getTarget();
                if (target instanceof NagerDateReactiveClient) return (NagerDateReactiveClient) target;
            }
        } catch (Exception ignored) {}
        return client;
    }

    @Test
    void lastThree_cache_prevents_second_call_to_client() {
        NagerDateReactiveClient mock = targetMock();
        when(mock.getPublicHolidays(2021, "GB")).thenReturn(Mono.just(List.of(
                ph("2021-12-31", "NYE")
        )));
        when(mock.getPublicHolidays(2020, "GB")).thenReturn(Mono.just(List.of(
                ph("2020-12-25", "XMas")
        )));

        // First call — should invoke client for current and previous year
        service.getLastThreeHolidays("GB", LocalDate.of(2021, 12, 31)).block();
        // Second call with same args — should be served from cache and not call the client again
        service.getLastThreeHolidays("GB", LocalDate.of(2021, 12, 31)).block();

        verify(mock, times(1)).getPublicHolidays(2021, "GB");
        verify(mock, times(1)).getPublicHolidays(2020, "GB");
    }

    @Test
    void commonDates_cache_prevents_second_call_to_client() {
        NagerDateReactiveClient mock = targetMock();
        when(mock.getPublicHolidays(2021, "GB")).thenReturn(Mono.just(List.of(ph("2021-12-25", "Christmas"))));
        when(mock.getPublicHolidays(2021, "FR")).thenReturn(Mono.just(List.of(ph("2021-12-25", "Noel"))));

        service.commonDates(2021, "GB", "FR").block();
        service.commonDates(2021, "GB", "FR").block();

        verify(mock, times(1)).getPublicHolidays(2021, "GB");
        verify(mock, times(1)).getPublicHolidays(2021, "FR");
    }

    @Test
    void weekdayCounts_cache_prevents_second_call_to_client() {
        NagerDateReactiveClient mock = targetMock();
        when(mock.getPublicHolidays(2021, "GB")).thenReturn(Mono.just(List.of(ph("2021-07-05", "Mon"))));
        when(mock.getPublicHolidays(2021, "FR")).thenReturn(Mono.just(List.of(ph("2021-07-05", "Lun"))));

        service.countWeekdayHolidays(2021, List.of("GB", "FR")).block();
        service.countWeekdayHolidays(2021, List.of("GB", "FR")).block();

        verify(mock, times(1)).getPublicHolidays(2021, "GB");
        verify(mock, times(1)).getPublicHolidays(2021, "FR");
    }
}
