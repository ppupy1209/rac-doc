package com.yeonwoo.ragdoc.bench;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * study ① (Virtual Threads) 측정용 엔드포인트.
 *
 * downstream: 실제 백엔드에서 가장 흔한 I/O 패턴 = "내 API가 다른 서비스를 HTTP로 호출하고 응답을 기다림".
 *   go-httpbin(/delay)을 실제 네트워크로 호출하며, 호출 스레드는 응답이 올 때까지 소켓에서 blocking 한다.
 *   → 플랫폼 스레드(기본 200) vs 가상 스레드의 처리량 차이가 현실적으로 드러난다.
 * io: 네트워크 없이 순수 대기만 흉내내는 단순 비교용.
 */
@RestController
@RequestMapping("/api/bench")
public class BenchController {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String downstreamUrl;

    public BenchController(@Value("${DOWNSTREAM_URL:http://httpbin:8080}") String downstreamUrl) {
        this.downstreamUrl = downstreamUrl;
    }

    /** 순수 대기 (비교용) */
    @GetMapping("/io")
    public String simulateIo(@RequestParam(defaultValue = "200") long delayMs) throws InterruptedException {
        Thread.sleep(delayMs); // 외부 I/O 대기를 흉내 (blocking)
        return "ok";
    }

    /** 실제 다운스트림 HTTP 호출 (현실적 I/O) */
    @GetMapping("/downstream")
    public String callDownstream(@RequestParam(defaultValue = "200") long delayMs) throws Exception {
        double seconds = delayMs / 1000.0;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downstreamUrl + "/delay/" + seconds)) // 다운스트림이 seconds 뒤 응답
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        // 동기 send: 응답이 올 때까지 이 스레드가 blocking (가상 스레드면 그동안 캐리어 반납)
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return "downstream status: " + response.statusCode();
    }
}
