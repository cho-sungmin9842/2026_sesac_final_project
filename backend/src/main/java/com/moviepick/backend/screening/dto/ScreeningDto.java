package com.moviepick.backend.screening.dto;

import com.moviepick.backend.screening.Screening;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScreeningDto(
        Long id,
        String theaterName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {
    public static ScreeningDto from(Screening screening) {
        return new ScreeningDto(
                screening.getId(),
                screening.getTheater().getName(),
                screening.getDate(),
                screening.getStartTime(),
                screening.getEndTime()
        );
    }
}
