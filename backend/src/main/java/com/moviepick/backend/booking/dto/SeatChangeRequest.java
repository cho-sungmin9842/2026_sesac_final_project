package com.moviepick.backend.booking.dto;

import jakarta.validation.constraints.NotNull;

public record SeatChangeRequest(@NotNull Long fromSeatId, @NotNull Long toSeatId) {
}
