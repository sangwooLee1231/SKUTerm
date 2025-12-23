package com.sku.cart.controller;

import com.sku.cart.dto.CartEnrollRequestDto;
import com.sku.cart.dto.CartEnrollResultDto;
import com.sku.cart.dto.CartItemResponseDto;
import com.sku.cart.dto.CartRequestDto;
import com.sku.cart.service.CartService;
import com.sku.common.dto.ResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    //test

    /**
     * 장바구니 담기
     */
    @PostMapping
    public ResponseEntity<ResponseDto<Map<String, Object>>> addToCart(
            @Valid @RequestBody CartRequestDto request,
            @AuthenticationPrincipal User user
    ) {
        String studentNumber = user.getUsername();

        cartService.addToCart(studentNumber, request.getLectureId());

        return ResponseEntity.ok(
                new ResponseDto<>(
                        HttpStatus.OK.value(),
                        "장바구니에 담겼습니다.",
                        Map.of("lectureId", request.getLectureId())
                )
        );
    }

    /**
     * 장바구니 삭제
     */
    @DeleteMapping("/{lectureId}")
    public ResponseEntity<ResponseDto<Map<String, Object>>> removeFromCart(
            @PathVariable Long lectureId,
            @AuthenticationPrincipal User user
    ) {
        String studentNumber = user.getUsername();

        cartService.removeFromCart(studentNumber, lectureId);

        return ResponseEntity.ok(
                new ResponseDto<>(
                        HttpStatus.OK.value(),
                        "장바구니에서 삭제되었습니다.",
                        Map.of("lectureId", lectureId)
                )
        );
    }

    /**
     *  장바구니 목록 조회
     */
    @GetMapping("/my")
    public ResponseEntity<ResponseDto<Map<String, Object>>> getMyCart(
            @AuthenticationPrincipal User user
    ) {
        String studentNumber = user.getUsername();

        List<CartItemResponseDto> items = cartService.getMyCart(studentNumber);

        return ResponseEntity.ok(
                new ResponseDto<>(
                        HttpStatus.OK.value(),
                        "나의 장바구니 목록 조회 성공",
                        Map.of("cartItems", items)
                )
        );
    }

    /**
     * 장바구니 → 수강신청
     */
    @PostMapping("/enroll")
    public ResponseEntity<ResponseDto<Map<String, Object>>> enrollFromCart(
            @Valid @RequestBody CartEnrollRequestDto request,
            @AuthenticationPrincipal User user
    ) {
        String studentNumber = user.getUsername();

        List<CartEnrollResultDto> results =
                cartService.enrollFromCart(studentNumber, request.getLectureIds());

        return ResponseEntity.ok(
                new ResponseDto<>(
                        HttpStatus.OK.value(),
                        "장바구니에서 수강신청 처리 완료",
                        Map.of("results", results)
                )
        );
    }
}
