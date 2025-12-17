package com.example.nager.service;
import com.example.nager.client.NagerDateReactiveClient;
import com.example.nager.config.WeekendProperties;
import com.example.nager.model.CommonHoliday;
import com.example.nager.model.CountryHolidayCount;
import com.example.nager.model.HolidaySummary;
import com.example.nager.model.PublicHoliday;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux; import reactor.core.publisher.Mono;
import java.time.DayOfWeek; import java.time.LocalDate; import java.util.*; import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Service
public class HolidayService {
    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);
    private final NagerDateReactiveClient client; private final WeekendProperties weekendProps;
    public HolidayService(NagerDateReactiveClient client, WeekendProperties weekendProps) { this.client = client; this.weekendProps = weekendProps; }

    @Cacheable(cacheNames = "lastThree", key = "#today + ':' + #countryCode")
    public Mono<List<HolidaySummary>> getLastThreeHolidays(String countryCode, LocalDate today) {
        int year = today.getYear();
        Mono<List<PublicHoliday>> current = client.getPublicHolidays(year, countryCode);
        Mono<List<PublicHoliday>> prev = client.getPublicHolidays(year - 1, countryCode);
        return Flux.merge(current.flatMapMany(Flux::fromIterable), prev.flatMapMany(Flux::fromIterable))
            .filter(h -> !h.getDate().isAfter(today))
            .sort(Comparator.comparing(PublicHoliday::getDate).reversed())
            .map(h -> new HolidaySummary(h.getDate(), h.getName()))
            .take(3)
            .collectList()
            .doOnNext(list -> log.info("Last-3 computed for {} -> {} entries", countryCode, list.size()));
    }

    @Cacheable(cacheNames = "weekdayCounts", key = "#year + ':' + #countryCodes")
    public Mono<List<CountryHolidayCount>> countWeekdayHolidays(int year, List<String> countryCodes) {
        List<Mono<CountryHolidayCount>> monos = new ArrayList<>();
        for (String cc : countryCodes) {
            monos.add(client.getPublicHolidays(year, cc).map(list -> {
                Set<DayOfWeek> weekend = weekendFor(cc);
                long weekdayCount = list.stream().filter(h -> !weekend.contains(h.getDate().getDayOfWeek())).count();
                return new CountryHolidayCount(cc, (int) weekdayCount);
            }));
        }
        return Flux.mergeSequential(monos)
            .collectList()
            .map(list -> { Collections.sort(list); return list; })
            .doOnNext(list -> log.info("Weekday counts computed for {} countries", list.size()));
    }

    @Cacheable(cacheNames = "commonDates", key = "T(String).format('%d:%s:%s', #year, #countryA, #countryB)")
    public Mono<List<CommonHoliday>> commonDates(int year, String countryA, String countryB) {
        Mono<List<PublicHoliday>> a = client.getPublicHolidays(year, countryA);
        Mono<List<PublicHoliday>> b = client.getPublicHolidays(year, countryB);
        return Mono.zip(a, b).map(tuple -> {
            Map<LocalDate, String> byDateA = tuple.getT1().stream().collect(Collectors.toMap(PublicHoliday::getDate, PublicHoliday::getLocalName, (h1, h2) -> h1));
            Map<LocalDate, String> byDateB = tuple.getT2().stream().collect(Collectors.toMap(PublicHoliday::getDate, PublicHoliday::getLocalName, (h1, h2) -> h1));
            Set<LocalDate> intersection = new TreeSet<>(byDateA.keySet());
            intersection.retainAll(byDateB.keySet());
            return intersection.stream().sorted().map(d -> new CommonHoliday(d, byDateA.get(d), byDateB.get(d))).toList();
        });
    }

    private Set<DayOfWeek> weekendFor(String countryCode) {
        List<String> configured = weekendProps.getOverrides().getOrDefault(countryCode.toUpperCase(), weekendProps.getDefault());
        Set<DayOfWeek> s = new HashSet<>();
        for (String d : configured) s.add(DayOfWeek.valueOf(d));
        return s;
    }
}
