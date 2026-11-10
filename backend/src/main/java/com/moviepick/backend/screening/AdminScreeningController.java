package com.moviepick.backend.screening;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

// 관리자 "예매/상영관 현황" 화면의 "이번 주 상영정보" 상태 확인/수동 생성 전용. 자동 생성(서버 시작/매시간/
// 월요일 자정)이 어떤 이유로 못 돌았을 때 관리자가 직접 만들 수 있게 합니다. 요청자가 관리자인지는
// X-User-Id로 확인합니다.
@RestController
@RequestMapping("/api/admin/screenings")
public class AdminScreeningController {

    public record WeekStatus(LocalDate weekStart, LocalDate weekEnd, boolean exists, long count) {}

    public record GenerateResult(boolean generated, String message, WeekStatus status) {}

    private final ScreeningGenerationService generationService;
    private final ScreeningRepository screeningRepository;
    private final UserRepository userRepository;

    public AdminScreeningController(
            ScreeningGenerationService generationService, ScreeningRepository screeningRepository,
            UserRepository userRepository
    ) {
        this.generationService = generationService;
        this.screeningRepository = screeningRepository;
        this.userRepository = userRepository;
    }

    // 이번 주(월~일) 상영정보가 이미 있는지와 개수.
    @GetMapping("/week-status")
    public WeekStatus getWeekStatus(@RequestHeader("X-User-Id") Long userId) {
        requireAdmin(userId);
        return currentWeekStatus();
    }

    // 이번 주 상영정보가 없을 때만 생성합니다(이미 있으면 아무것도 바꾸지 않음 - 예매된 좌석 보호).
    // KMDB 조회가 포함돼 수 초~수십 초 걸릴 수 있고, 서비스 쪽 synchronized로 자동 생성과 동시에 눌려도
    // 중복 생성되지 않습니다.
    @PostMapping("/generate")
    public GenerateResult generate(@RequestHeader("X-User-Id") Long userId) {
        requireAdmin(userId);
        boolean generated = generationService.generateWeeklyScreenings();
        WeekStatus status = currentWeekStatus();
        if (generated && !status.exists()) {
            // 영화 목록을 못 가져오는 등으로 한 건도 만들지 못한 경우.
            throw new ApiException("상영정보를 생성하지 못했습니다. 영화 데이터(KMDB) 상태를 확인해주세요.",
                    HttpStatus.BAD_GATEWAY);
        }
        String message = generated
                ? "이번 주 상영정보 " + status.count() + "건을 생성했습니다."
                : "이번 주 상영정보가 이미 있어 새로 생성하지 않았습니다.";
        return new GenerateResult(generated, message, status);
    }

    private WeekStatus currentWeekStatus() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        long count = screeningRepository.countByDateBetween(monday, sunday);
        return new WeekStatus(monday, sunday, count > 0, count);
    }

    private void requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));
        if (!user.isAdmin()) {
            throw new ApiException("관리자만 이용할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
    }
}
