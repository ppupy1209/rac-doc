package com.yeonwoo.ragdoc.bench;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * study ① (Virtual Threads) 측정 전용 엔드포인트.
 *
 * 실제 /api/ask 로 측정하면 Ollama(LLM)가 병목이라 스레드 효과가 가려진다.
 * 여기서는 "느린 외부 I/O 대기"만 순수하게 재현하려고 delayMs 동안 blocking sleep 한다.
 * 이렇게 변수를 격리해야 플랫폼 스레드(기본 200개) vs 가상 스레드의 처리량 차이가 선명하게 보인다.
 */
@RestController
@RequestMapping("/api/bench")
public class BenchController {

    @GetMapping("/io")
    public String simulateIo(@RequestParam(defaultValue = "200") long delayMs) throws InterruptedException {
        Thread.sleep(delayMs); // 외부 I/O 대기를 흉내 (blocking)
        return "ok";
    }
}
