package com.moviepick.backend.movie;

import com.moviepick.backend.chat.GeminiClient;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.movie.dto.MovieDetailDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 영화 상세 줄거리를 Gemini로 스포일러 없이 요약합니다.
 * 같은 영화를 다시 열어도 Gemini를 또 호출하지 않도록 결과를 메모리에 캐싱합니다(서버가 떠 있는 동안 유지).
 */
@Service
public class AiSummaryService {

    private static final String SYSTEM_INSTRUCTION = """
            너는 영화 줄거리를 스포일러 없이 요약해주는 도우미야.
            아래는 어떤 영화의 전체 줄거리야. 이걸 읽고 결말, 반전, 범인, 생사 여부 등 스포일러가 될 수 있는
            내용은 절대 포함하지 말고, 영화의 배경과 설정만으로 흥미를 돋우는 수준으로 2~3문장, 200자 이내로
            간단히 요약해줘. 다른 설명 없이 요약 문장만 출력해.
            """;

    private final MovieService movieService;
    private final GeminiClient geminiClient;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public AiSummaryService(MovieService movieService, GeminiClient geminiClient) {
        this.movieService = movieService;
        this.geminiClient = geminiClient;
    }

    public String summarize(String id) {
        return cache.computeIfAbsent(id, this::generate);
    }

    private String generate(String id) {
        String[] parts = id.split("_", 2);
        if (parts.length != 2) {
            throw new ApiException("잘못된 영화 id 형식입니다: " + id, HttpStatus.BAD_REQUEST);
        }

        MovieDetailDto detail = movieService.getDetail(parts[0], parts[1]);
        String plot = detail.plot();
        if (plot == null || plot.isBlank()) {
            return "줄거리 정보가 없어 요약할 내용이 없습니다.";
        }

        List<GeminiClient.ChatTurn> turns = List.of(new GeminiClient.ChatTurn("user", plot));
        return geminiClient.generate(SYSTEM_INSTRUCTION, turns, null).trim();
    }
}
