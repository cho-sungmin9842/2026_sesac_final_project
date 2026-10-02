package com.moviepick.backend.payment;

import com.moviepick.backend.booking.BookingService;
import com.moviepick.backend.booking.dto.BookingDto;
import com.moviepick.backend.booking.dto.BookingRequest;
import com.moviepick.backend.payment.dto.PaymentConfirmRequest;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final TossPaymentsClient tossPaymentsClient;
    private final BookingService bookingService;

    public PaymentService(TossPaymentsClient tossPaymentsClient, BookingService bookingService) {
        this.tossPaymentsClient = tossPaymentsClient;
        this.bookingService = bookingService;
    }

    // 토스페이먼츠 결제 승인이 성공해야만 실제 예매(좌석 확정)를 생성합니다 - 결제 없이 좌석만 선점되는 일이
    // 없도록 반드시 이 순서(결제 승인 -> 예매 생성)로만 동작합니다. 예매 생성 자체는 기존 BookingService를
    // 그대로 재사용하므로, 좌석 중복 검증/요금 재계산 로직이 두 군데로 나뉘지 않습니다.
    public BookingDto confirmAndCreateBooking(Long userId, PaymentConfirmRequest request) {
        tossPaymentsClient.confirm(request.paymentKey(), request.orderId(), request.amount());
        return bookingService.create(userId, new BookingRequest(request.screeningId(), request.seatIds(), request.ticketCounts()));
    }
}
