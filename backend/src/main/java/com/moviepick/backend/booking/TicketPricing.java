package com.moviepick.backend.booking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * 연령 구분별 요금표. 주중(월~목)/주말(금~일)·공휴일 요금이 다릅니다.
 * 프론트(bookingData.js)와 값을 맞춰뒀습니다 - 가격을 바꿀 때는 두 군데 다 고쳐야 합니다.
 */
final class TicketPricing {

    record Price(int weekday, int weekendOrHoliday) {
        int forDate(LocalDate date) {
            return isWeekendOrHoliday(date) ? weekendOrHoliday : weekday;
        }
    }

    private static final Map<String, Price> PRICES = Map.of(
            "adult", new Price(14000, 15000),
            "teen", new Price(11000, 12000),
            "child", new Price(7000, 8000),
            "senior", new Price(7000, 7000)
    );

    // 2026년 대한민국 공휴일(대체공휴일 포함). 설날/추석/부처님오신날은 음력 기준이라 매년 날짜가 바뀌므로
    // 2026년 기준으로 채워뒀습니다 - 정부 확정 공고와 차이가 있으면 이 목록만 고치면 됩니다.
    private static final Set<LocalDate> HOLIDAYS_2026 = Set.of(
            LocalDate.of(2026, 1, 1), // 신정
            LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17), LocalDate.of(2026, 2, 18), // 설날 연휴
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2), // 삼일절(일) + 대체공휴일
            LocalDate.of(2026, 5, 5), // 어린이날
            LocalDate.of(2026, 5, 24), LocalDate.of(2026, 5, 25), // 부처님오신날(일) + 대체공휴일
            LocalDate.of(2026, 6, 6), // 현충일
            LocalDate.of(2026, 8, 15), // 광복절
            LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26), // 추석 연휴
            LocalDate.of(2026, 10, 3), // 개천절
            LocalDate.of(2026, 10, 9), // 한글날
            LocalDate.of(2026, 12, 25) // 성탄절
    );

    private TicketPricing() {
    }

    static boolean isWeekendOrHoliday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
                || HOLIDAYS_2026.contains(date);
    }

    static boolean isKnownCategory(String category) {
        return PRICES.containsKey(category);
    }

    static int priceFor(String category, LocalDate date) {
        Price price = PRICES.get(category);
        return price == null ? 0 : price.forDate(date);
    }
}
