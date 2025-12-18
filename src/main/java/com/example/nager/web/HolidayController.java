package com.example.nager.web;

import com.example.nager.model.*; import com.example.nager.service.HolidayService;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.time.LocalDate; import java.util.Arrays; import java.util.List;
import io.swagger.v3.oas.annotations.Operation;

@RestController @RequestMapping("/api/holidays") @Validated
public class HolidayController {
    private static final Logger log = LoggerFactory.getLogger(HolidayController.class);
    private final HolidayService holidayService; public HolidayController(HolidayService holidayService) { this.holidayService = holidayService; }

    @Operation(summary = "Last 3 celebrated holidays")
    @GetMapping("/last-3/{countryCode}")
    public Mono<List<HolidaySummary>> lastThree(@PathVariable @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "Use ISO 3166-1 alpha-2 code") String countryCode) {
        log.info("GET /last-3/{}", countryCode);
        return holidayService.getLastThreeHolidays(countryCode.toUpperCase(), LocalDate.now());
    }

    @Operation(summary = "Weekday holiday counts")
    @GetMapping("/weekday-count")
    public Mono<List<CountryHolidayCount>> weekdayCount(@RequestParam int year,
                                                        @RequestParam("countries") @NotBlank String countriesCsv) {
        List<String> codes = Arrays.stream(countriesCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(String::toUpperCase).toList();
        log.info("GET /weekday-count year={} countries={}", year, codes);
        return holidayService.countWeekdayHolidays(year, codes);
    }

    @Operation(summary = "Common holiday dates")
    @GetMapping("/common-dates")
    public Mono<List<CommonHoliday>> commonDates(@RequestParam int year,
                                                 @RequestParam("countryA") @Pattern(regexp = "^[A-Za-z]{2}$") String countryA,
                                                 @RequestParam("countryB") @Pattern(regexp = "^[A-Za-z]{2}$") String countryB) {
        log.info("GET /common-dates year={} countryA={} countryB={} ", year, countryA, countryB);
        return holidayService.commonDates(year, countryA.toUpperCase(), countryB.toUpperCase());
    }
}
