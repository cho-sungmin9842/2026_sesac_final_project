package com.moviepick.backend.payment;

import com.moviepick.backend.booking.dto.BookingDto;
import com.moviepick.backend.payment.dto.PaymentConfirmRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 토스페이먼츠 결제위젯의 successUrl에서 호출합니다. 결제 승인(TossPaymentsClient.confirm)이 성공해야만
     * 예매가 생성됩니다.
     */
    @PostMapping("/confirm-and-book")
    public BookingDto confirmAndBook(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody PaymentConfirmRequest request) {
        return paymentService.confirmAndCreateBooking(userId, request);
    }
}
