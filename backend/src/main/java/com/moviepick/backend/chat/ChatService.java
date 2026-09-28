package com.moviepick.backend.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.chat.dto.ChatMessageDto;
import com.moviepick.backend.chat.dto.ChatRequestDto;
import com.moviepick.backend.chat.dto.ChatResponseDto;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.movie.MovieService;
import com.moviepick.backend.movie.dto.ActorDto;
import com.moviepick.backend.movie.dto.MovieDetailDto;
import com.moviepick.backend.movie.dto.MovieSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "영화, 배우, 감독"과 마찬가지로 채팅도 KMDB 영화 데이터는 그대로 쓰고, Gemini 호출은 대화 1턴당 딱 한 번
 * (사용자 문장 -> 장르/연도/러닝타임/검색어 같은 구조화된 조건 추출, JSON 모드)만 합니다. 무료 API 호출 한도를
 * 아끼기 위해, 추천 문구 자체는 그 조건과 MovieService(=KMDB)로 실제 찾은 후보를 바탕으로 서버가 직접 문장을
 * 조립합니다(별도 Gemini 호출 없음) - 그래서 존재하지 않는 영화를 지어낼 걱정도 없습니다.
 * 대화 내용은 DB(chat_messages)에 저장해, 사용자별로 다시 접속해도 이전 대화가 이어집니다.
 */
@Slf4j
@Service
public class ChatService {

    private static final int MAX_RECOMMENDATIONS = 6;
    // 대화가 길어질수록 Gemini가 예전 주제(예: 특정 영화 얘기)에 "고착"돼서, 전혀 새로운 배우/감독 이름을
    // 말해도 못 알아듣는 현상이 실제로 있었습니다. 조건 추출에는 최근 몇 턴만 넘겨서 그 영향을 줄입니다.
    private static final int MAX_HISTORY_TURNS_FOR_EXTRACTION = 6;

    // 사이드바 장르 필터(filterOptions.js)와 동일한 목록입니다. Gemini가 이 중 하나만 고르도록 강제합니다.
    private static final List<String> KNOWN_GENRES = List.of(
            "SF", "가족", "공포", "교육", "기업ㆍ기관ㆍ단체", "동성애", "드라마", "로드무비", "멜로/로맨스",
            "무협", "문화", "뮤직", "미스터리", "범죄", "사회", "사회물(경향)", "스릴러", "스포츠", "시대극/사극",
            "실험", "아동", "액션", "어드벤처", "에로", "역사", "옴니버스", "인권", "인물", "자연ㆍ환경", "재난",
            "전쟁", "지역", "청춘영화", "코메디", "판타지", "하이틴(고교)"
    );

    private final GeminiClient geminiClient;
    private final MovieService movieService;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ChatService(
            GeminiClient geminiClient,
            MovieService movieService,
            ChatMessageRepository chatMessageRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.geminiClient = geminiClient;
        this.movieService = movieService;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    // 저장된 대화 내역을 시간순으로 돌려줍니다. 추천 메시지는 movie_ids로 KMDB 상세를 다시 조회해
    // 추천 카드까지 그대로 복원합니다.
    public List<ChatMessageDto> history(Long userId) {
        return chatMessageRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(message -> new ChatMessageDto(message.getRole(), message.getContent(), moviesFrom(message.getMovieIds())))
                .toList();
    }

    private List<MovieSummaryDto> moviesFrom(String movieIds) {
        if (movieIds == null || movieIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(movieIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(this::toSummaryOrNull)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private MovieSummaryDto toSummaryOrNull(String compositeId) {
        String[] parts = compositeId.split("_", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            MovieDetailDto detail = movieService.getDetail(parts[0], parts[1]);
            return new MovieSummaryDto(
                    detail.id(), detail.title(), detail.englishTitle(), detail.year(), detail.genres(),
                    detail.posterUrl(), detail.ageRating(), detail.runtimeMinutes(), detail.averageScore(), detail.reviewCount());
        } catch (Exception e) {
            log.warn("저장된 채팅 메시지의 추천 영화({})를 다시 불러오지 못했습니다.", compositeId, e);
            return null;
        }
    }

    public ChatResponseDto reply(Long userId, ChatRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));

        List<ChatMessage> fullHistory = chatMessageRepository.findByUserIdOrderByCreatedAtAsc(userId);
        List<GeminiClient.ChatTurn> history = toGeminiTurns(lastN(fullHistory, MAX_HISTORY_TURNS_FOR_EXTRACTION));
        ExtractedFilter filter = extractFilter(history, request.message());
        // 대화가 어떤 영화 얘기로 길게 이어진 뒤라, "배우 OOO 출연작 알려줘"처럼 명백한 새 요청도 Gemini가
        // 가끔 놓치는 경우가 실제로 있었습니다. "배우 OOO"/"OOO 감독" 패턴은 정규식으로 한 번 더 직접
        // 확인해서, Gemini가 아무 조건도 못 뽑았을 때만 안전망으로 보정합니다.
        filter = applyNameHintFallback(filter, request.message());
        // "영화 OOO에 대해 알려줘"처럼 특정 제목을 콕 집어 물어봤는데도 Gemini가 이걸 movie_question이
        // 아닌 recommend(그것도 아무 조건도 못 뽑은 빈 recommend)로 잘못 분류하는 경우가 있었습니다.
        // 그 상태로 두면 findCandidates()가 조건 없는 "최신순 아무 영화나" 목록을 지어내 완전히 엉뚱한
        // 추천을 하게 되므로, 제목이 뚜렷하게 언급된 경우엔 movie_question으로 바로잡습니다.
        filter = applyMovieQuestionHintFallback(filter, request.message());
        // "2개", "3편"처럼 숫자로 개수를 콕 집었는데 Gemini가 count를 못 뽑은 경우의 안전망입니다.
        filter = applyCountHintFallback(filter, request.message());
        // "비슷한 영화 더 추천해줘"처럼 특정 영화를 다시 언급하지 않고 "더/비슷하게"만 말하면, Gemini가
        // 기준이 없어 엉뚱한 장르를 지어내는 경우가 있었습니다(예: 직전이 "좀비" 키워드 추천이었는데
        // 뜬금없이 "액션" 장르로 답함). 그럴 때는 직전에 실제로 추천했던 영화를 기준(referenceTitle)으로
        // 삼아, 그 영화를 서버가 다시 조회해서 실제 장르로 안정적으로 이어가게 합니다.
        filter = applyMoreHintFallback(filter, request.message(), fullHistory);

        // 영화 추천/특정 영화 정보와 전혀 상관없는 질문(날씨, 잡담 등)이면 KMDB를 뒤져 억지로 영화를
        // 끼워 맞추지 않고, 서비스 용도를 안내하는 답으로 바로 응답합니다.
        if ("off_topic".equals(filter.intent())) {
            String reply = "저는 영화 추천을 도와드리는 AI예요 🎬 보고 싶은 장르나 배우, 감독, 소재를 말씀해주시면 국내 영화를 찾아드릴게요!";
            return persistTurn(user, request.message(), reply, List.of());
        }

        // "영화 추천해줘"가 아니라 "그 영화 장르/배우 알려줘"처럼 이미 나온(또는 대화 맥락 속) 특정 영화에 대한
        // 질문이면, 추천 목록을 다시 만드는 대신 그 영화 한 편의 상세 정보(장르/감독/배우/러닝타임)로 답합니다.
        if ("movie_question".equals(filter.intent()) && filter.query() != null) {
            return answerMovieQuestion(user, request.message(), filter.query());
        }

        // "이 영화와 같은 장르로"처럼 이전에 나온 영화를 기준 삼으라고 했으면, Gemini에게 그 영화의 장르를
        // 다시 "기억해내라"고 시키는 대신 그 영화를 직접 재조회해서 실제 장르를 가져옵니다(훨씬 안정적입니다).
        ExtractedFilter effectiveFilter = resolveReferenceGenre(filter);

        List<MovieSummaryDto> candidates = findCandidates(effectiveFilter);
        String reply = candidates.isEmpty()
                ? "말씀하신 조건에 맞는 국내 영화를 찾지 못했어요. 장르나 연도 조건을 조금 완화해서 다시 물어봐주시겠어요?"
                : buildReply(effectiveFilter, candidates);

        return persistTurn(user, request.message(), reply, candidates);
    }

    private ExtractedFilter resolveReferenceGenre(ExtractedFilter filter) {
        if (filter.genre() != null || filter.referenceTitle() == null) {
            return filter;
        }
        List<MovieSummaryDto> matches =
                movieService.search(filter.referenceTitle(), List.of(), null, "latest", 1, 1, "title").movies();
        if (matches.isEmpty() || matches.get(0).genres().isEmpty()) {
            return filter;
        }
        String resolvedGenre = matches.get(0).genres().get(0);
        return new ExtractedFilter(
                filter.intent(), resolvedGenre, filter.year(), filter.runtimeMaxMinutes(),
                filter.query(), filter.queryField(), filter.referenceTitle(), filter.count());
    }

    // ---- 특정 영화에 대한 질문(장르/배우/감독/줄거리 등) ----

    private ChatResponseDto answerMovieQuestion(User user, String userMessage, String movieTitle) {
        List<MovieSummaryDto> matches = movieService.search(movieTitle, List.of(), null, "latest", 1, 1, "title").movies();
        if (matches.isEmpty()) {
            String reply = "'%s' 영화를 찾지 못했어요. 정확한 제목으로 다시 물어봐주시겠어요?".formatted(movieTitle);
            return persistTurn(user, userMessage, reply, List.of());
        }

        String[] idParts = matches.get(0).id().split("_", 2);
        MovieDetailDto detail = movieService.getDetail(idParts[0], idParts[1]);
        String reply = describeMovieDetail(detail);

        return persistTurn(user, userMessage, reply, List.of(matches.get(0)));
    }

    private String describeMovieDetail(MovieDetailDto detail) {
        String genres = detail.genres().isEmpty() ? "정보 없음" : String.join(", ", detail.genres());
        String directors = detail.directors().isEmpty() ? "정보 없음" : String.join(", ", detail.directors());
        String actors = detail.actors().isEmpty()
                ? "정보 없음"
                : detail.actors().stream().map(ActorDto::name).limit(8).collect(Collectors.joining(", "));
        String runtime = detail.runtimeMinutes() == null ? "정보 없음" : detail.runtimeMinutes() + "분";

        return """
                '%s'(%s) 정보예요.
                - 장르: %s
                - 감독: %s
                - 출연: %s
                - 러닝타임: %s""".formatted(detail.title(), detail.year(), genres, directors, actors, runtime);
    }

    private ChatResponseDto persistTurn(User user, String userMessage, String reply, List<MovieSummaryDto> movies) {
        chatMessageRepository.save(new ChatMessage(user, "user", userMessage, null));
        chatMessageRepository.save(new ChatMessage(user, "ai", reply, joinIds(movies)));
        return new ChatResponseDto(reply, movies);
    }

    private String joinIds(List<MovieSummaryDto> movies) {
        return movies.isEmpty() ? null : movies.stream().map(MovieSummaryDto::id).collect(Collectors.joining(","));
    }

    // ---- 1) 조건 추출 ----

    private record ExtractedFilter(
            String intent, String genre, String year, Integer runtimeMaxMinutes,
            String query, String queryField, String referenceTitle, Integer count
    ) {
    }

    private ExtractedFilter extractFilter(List<GeminiClient.ChatTurn> history, String message) {
        String systemInstruction = """
                너는 한국 영화 추천 서비스의 조건 추출기다. 사용자의 마지막 메시지를, 바로 위 대화 맥락과
                함께 다시 판단해서 조건을 JSON으로 뽑아라(이전 메시지의 조건을 그대로 재사용하지 말고 이번
                메시지 기준으로 매번 새로 판단해라). 오늘 날짜는 %s이다.

                - intent: "이 영화 장르가 뭐야", "배우가 누구야", "감독/줄거리/러닝타임 알려줘",
                  "영화 OOO에 대해 알려줘"/"OOO 정보 알려줘"/"OOO 줄거리 알려줘"처럼 영화 제목 하나를
                  콕 집어 그 영화 "한 편 자체"의 정보를 묻는 질문일 때만 "movie_question"이다(문장에
                  "추천"이라는 말이 없고 특정 제목 하나만 물어보면 movie_question일 가능성이 높다).
                  "추천"이 들어간 요청은 전부 "recommend"다. 특히 "이 영화와 같은 장르로 추천해줘", "그거랑 비슷한 걸로 또
                  추천해줘"처럼 이전에 나온 영화를 "기준"으로만 삼아 새 후보를 찾아달라는 요청도 movie_question이
                  아니라 recommend다. 날씨/시간/잡담/다른 서비스 질문처럼 영화 추천이나 특정 영화 정보와
                  전혀 관련 없는 메시지는 "off_topic"이다(이때는 genre/year/query 등 나머지 필드는 전부
                  null로 둔다).
                - referenceTitle: "이 영화와 같은 장르로", "그거랑 비슷하게"처럼 이전에 나온 특정 영화를
                  기준으로 삼으라는 요청이면 그 영화의 실제 제목(대명사를 이전 메시지에서 찾아 채워라).
                  기준으로 삼을 영화가 없으면 null. genre 값 자체를 여기서 추측하지 말고 제목만 채워라
                  (실제 장르는 서버가 그 영화를 직접 다시 조회해서 알아낸다).
                - genre: recommend이고 사용자가 장르명을 "직접" 말했을 때만 채운다(목록 중 하나).
                  referenceTitle로 대신 처리할 수 있는 경우(이 영화와 같은 장르 등)에는 genre를 null로 두고
                  referenceTitle만 채워라. 장르를 알 수 없거나 언급이 없으면 null.
                - year: 4자리 개봉연도 문자열(예: "2023"). "최근"/"요즘"처럼 특정 연도가 아니면 null.
                  이전 메시지에서 이미 연도 조건을 줬어도, 이번 메시지에서 다시 말하지 않았으면 null로 둔다
                  (연도 조건은 매번 이번 메시지 기준으로만 판단한다).
                - runtimeMaxMinutes: "OO시간 안 넘게", "짧은 영화" 같은 상영시간 상한(분). 언급 없으면 null.
                - query: intent가 movie_question이면 그 특정 영화의 제목(대명사로 가리켜도 이전 메시지에서
                  실제 제목을 찾아 채워라). intent가 recommend이면, 사용자가 특정 영화 제목/배우/감독 이름
                  "또는" 장르 목록에 없는 소재·테마(좀비, 시간여행, 전쟁, 스포츠, 첩보, 복수극 등)를 "새로"
                  검색 대상으로 콕 집었을 때만 채운다 - referenceTitle로 처리하는 경우엔 null로 둔다.
                - queryField: intent가 movie_question이면 항상 "title". recommend일 때 query가 영화 제목이면
                  "title", 배우 이름이면 "actor", 감독 이름이면 "director", 장르 목록에 없는 소재·테마면
                  "keyword"(예: "좀비를 소재로 한 영화", "시간여행 다루는 영화"). query가 null이면 이것도 null.
                - count: recommend일 때 사용자가 "2개", "3편", "다섯 개"처럼 추천 개수를 직접 숫자로
                  말했으면 그 숫자(정수). 개수를 말하지 않았으면 null(서버가 알아서 기본 개수를 쓴다).

                아주 중요: 이번 메시지에 "배우 OOO", "OOO 감독", 특정 영화 제목처럼 새로운 이름/제목이
                명확히 나오면, 이전 대화 주제(예: 다른 영화 얘기)와 상관없어 보여도 반드시 그 이름을
                query에 채워라. 절대로 이전 메시지의 query/장르를 이번 메시지에 이어붙이거나, 새 이름을
                무시한 채 null로 두지 마라.

                예시(직전 답변이 '군체'라는 영화에 대한 것이었던 상황):
                - "이 영화와 같은 장르로 추천해줘" -> {"intent":"recommend","referenceTitle":"군체","genre":null,"query":null,"year":null,"count":null}
                - "같은 영화의 장르 중에 2026년에 개봉한 걸로 2개 추천해줘" ->
                  {"intent":"recommend","referenceTitle":"군체","genre":null,"query":null,"year":"2026","count":2}
                - "영화 군체와 같은 좀비물을 다루는 영화 추천해줘" ->
                  {"intent":"recommend","referenceTitle":"군체","genre":null,"query":null,"year":null,"count":null}
                - "2020년 개봉작으로 추천해줘" -> {"intent":"recommend","referenceTitle":null,"genre":null,"query":null,"year":"2020","count":null}
                - "감독이 누구야" -> {"intent":"movie_question","query":"군체","queryField":"title"}
                - "영화 퍼펙트게임에 대해 알려줘" ->
                  {"intent":"movie_question","referenceTitle":null,"genre":null,"query":"퍼펙트게임","queryField":"title","year":null,"count":null}
                - "배우 송지효가 출연한 영화를 알려줘" (이전 대화 주제가 전혀 다른 영화였어도) ->
                  {"intent":"recommend","referenceTitle":null,"genre":null,"query":"송지효","queryField":"actor","year":null,"count":null}
                - "좀비를 소재로 한 영화를 추천해줘" ->
                  {"intent":"recommend","referenceTitle":null,"genre":null,"query":"좀비","queryField":"keyword","year":null,"count":null}
                - "실제 범죄 내용을 스토리로 한 영화를 2개 추천해줘" ->
                  {"intent":"recommend","referenceTitle":null,"genre":"범죄","query":null,"queryField":null,"year":null,"count":2}
                - "오늘 날씨 어때?" / "너는 이름이 뭐야?" / "1 더하기 1은?" ->
                  {"intent":"off_topic","referenceTitle":null,"genre":null,"query":null,"queryField":null,"year":null,"count":null}
                """.formatted(LocalDate.now());

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "OBJECT");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode intentProp = properties.putObject("intent");
        intentProp.put("type", "STRING");
        ArrayNode intentEnum = intentProp.putArray("enum");
        List.of("recommend", "movie_question", "off_topic").forEach(intentEnum::add);

        ObjectNode referenceTitleProp = properties.putObject("referenceTitle");
        referenceTitleProp.put("type", "STRING");
        referenceTitleProp.put("nullable", true);

        ObjectNode genreProp = properties.putObject("genre");
        genreProp.put("type", "STRING");
        genreProp.put("nullable", true);
        ArrayNode genreEnum = genreProp.putArray("enum");
        KNOWN_GENRES.forEach(genreEnum::add);

        ObjectNode yearProp = properties.putObject("year");
        yearProp.put("type", "STRING");
        yearProp.put("nullable", true);

        ObjectNode runtimeProp = properties.putObject("runtimeMaxMinutes");
        runtimeProp.put("type", "INTEGER");
        runtimeProp.put("nullable", true);

        ObjectNode queryProp = properties.putObject("query");
        queryProp.put("type", "STRING");
        queryProp.put("nullable", true);

        ObjectNode queryFieldProp = properties.putObject("queryField");
        queryFieldProp.put("type", "STRING");
        queryFieldProp.put("nullable", true);
        ArrayNode queryFieldEnum = queryFieldProp.putArray("enum");
        List.of("title", "actor", "director", "keyword").forEach(queryFieldEnum::add);

        ObjectNode countProp = properties.putObject("count");
        countProp.put("type", "INTEGER");
        countProp.put("nullable", true);

        // "required"가 없으면 Gemini가 값을 못 정한 nullable 필드를 아예 통째로 생략해버리는 경우가
        // 있었습니다(예: "액션 영화 추천해줘"에 genre 키 자체가 통째로 빠진 채 응답 - null도 아니고 키가
        // 없어서 결과적으로 아무 조건도 못 뽑은 것과 똑같이 처리돼버림). 모든 필드를 required로 못 박아
        // 두면 값이 없을 때도 명시적으로 null을 채워 넣도록 강제할 수 있습니다(nullable:true라 null 자체는
        // 여전히 허용됩니다).
        ArrayNode required = schema.putArray("required");
        properties.fieldNames().forEachRemaining(required::add);

        List<GeminiClient.ChatTurn> turns = new ArrayList<>(history);
        turns.add(new GeminiClient.ChatTurn("user", message));

        String json = geminiClient.generate(systemInstruction, turns, schema);
        try {
            JsonNode node = objectMapper.readTree(json);
            return new ExtractedFilter(
                    textOrNull(node.get("intent")),
                    textOrNull(node.get("genre")),
                    textOrNull(node.get("year")),
                    node.hasNonNull("runtimeMaxMinutes") ? node.get("runtimeMaxMinutes").asInt() : null,
                    textOrNull(node.get("query")),
                    textOrNull(node.get("queryField")),
                    textOrNull(node.get("referenceTitle")),
                    node.hasNonNull("count") ? node.get("count").asInt() : null);
        } catch (Exception e) {
            log.warn("Gemini 조건 추출 응답을 해석하지 못했습니다. json={}", json, e);
            return new ExtractedFilter(null, null, null, null, null, null, null, null);
        }
    }

    private String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    // ---- 2) KMDB에서 실제 후보 찾기 ----

    private List<MovieSummaryDto> findCandidates(ExtractedFilter filter) {
        List<String> genres = filter.genre() == null ? List.of() : List.of(filter.genre());
        // 사용자가 "2개", "3편"처럼 개수를 직접 말했으면 그 개수를(최대 MAX_RECOMMENDATIONS까지), 아니면
        // 기본 개수를 씁니다.
        int limit = resolveLimit(filter.count());

        List<MovieSummaryDto> movies;
        if (filter.query() != null && filter.queryField() != null) {
            // Gemini가 "OOO 감독"처럼 대상이 뭔지 알려준 경우 - 엉뚱하게 title 검색이 먼저 걸려버리는 걸
            // 막기 위해 굳이 다른 필드로 재시도하지 않고 지정된 필드만 그대로 씁니다.
            movies = searchNonEmpty(filter.query(), genres, filter.year(), filter.queryField(), limit);
        } else if (filter.query() != null) {
            // 필드를 특정 못 했으면 title -> actor -> director 순으로 시도합니다.
            movies = searchNonEmpty(filter.query(), genres, filter.year(), "title", limit);
            if (movies.isEmpty()) {
                movies = searchNonEmpty(filter.query(), genres, filter.year(), "actor", limit);
            }
            if (movies.isEmpty()) {
                movies = searchNonEmpty(filter.query(), genres, filter.year(), "director", limit);
            }
        } else {
            movies = movieService.search("", genres, filter.year(), "latest", 1, limit * 3, "title").movies();
        }

        return movies.stream()
                .filter(movie -> matchesRuntime(movie, filter.runtimeMaxMinutes()))
                .limit(limit)
                .toList();
    }

    private int resolveLimit(Integer requestedCount) {
        if (requestedCount == null || requestedCount < 1) {
            return MAX_RECOMMENDATIONS;
        }
        return Math.min(requestedCount, MAX_RECOMMENDATIONS);
    }

    private List<MovieSummaryDto> searchNonEmpty(String query, List<String> genres, String year, String field, int limit) {
        return movieService.search(query, genres, year, "latest", 1, limit * 3, field).movies();
    }

    private boolean matchesRuntime(MovieSummaryDto movie, Integer runtimeMaxMinutes) {
        if (runtimeMaxMinutes == null) {
            return true;
        }
        return movie.runtimeMinutes() != null && movie.runtimeMinutes() <= runtimeMaxMinutes;
    }

    // ---- 3) 추천 문구 조립 (Gemini 호출 없이 서버가 직접 문장을 만듭니다) ----

    private String buildReply(ExtractedFilter filter, List<MovieSummaryDto> candidates) {
        String condition = describeFilter(filter);
        String titles = candidates.stream().map(MovieSummaryDto::title).collect(Collectors.joining(", "));
        String suffix = candidates.size() > 1 ? "등 %d편을 찾았어요!".formatted(candidates.size()) : "을(를) 찾았어요!";
        String guide = "영화 포스터를 클릭하시면 자세한 영화 정보를 보실 수 있습니다.";
        return condition.isBlank()
                ? "%s %s 마음에 드는 작품을 골라보세요 🍿\n%s".formatted(titles, suffix, guide)
                : "%s 조건에 맞춰 %s %s 마음에 드는 작품을 골라보세요 🍿\n%s".formatted(condition, titles, suffix, guide);
    }

    private String describeFilter(ExtractedFilter filter) {
        List<String> parts = new ArrayList<>();
        if (filter.genre() != null) parts.add(filter.genre());
        if (filter.year() != null) parts.add(filter.year() + "년");
        if (filter.runtimeMaxMinutes() != null) parts.add(filter.runtimeMaxMinutes() + "분 이내");
        if (filter.query() != null) parts.add("'" + filter.query() + "'");
        return String.join(" · ", parts);
    }

    private List<ChatMessage> lastN(List<ChatMessage> messages, int n) {
        return messages.size() <= n ? messages : messages.subList(messages.size() - n, messages.size());
    }

    private static final java.util.regex.Pattern ACTOR_HINT =
            java.util.regex.Pattern.compile("배우\\s*([가-힣]{2,6})");
    private static final java.util.regex.Pattern DIRECTOR_HINT =
            java.util.regex.Pattern.compile("([가-힣]{2,6})\\s*감독|감독\\s*([가-힣]{2,6})");
    private static final java.util.regex.Pattern KEYWORD_HINT =
            java.util.regex.Pattern.compile("([가-힣]{2,6})(?:을|를)?\\s*소재로|([가-힣]{2,6})\\s*(?:을|를)?\\s*다루는");
    private static final java.util.Set<Character> TRAILING_PARTICLES =
            java.util.Set.of('과', '와', '가', '이', '은', '는', '을', '를', '의', '도', '만', '나');

    // Gemini 추출 결과가 완전히 비어있을 때만(장르/연도/검색어/기준영화 전부 null) "배우 OOO"/"OOO 감독"
    // 패턴을 정규식으로 한 번 더 확인합니다 - Gemini가 이미 뭔가 채웠으면 그 판단을 존중하고 손대지 않습니다.
    private ExtractedFilter applyNameHintFallback(ExtractedFilter filter, String message) {
        boolean isEmpty = "recommend".equals(filter.intent())
                && filter.genre() == null && filter.year() == null && filter.runtimeMaxMinutes() == null
                && filter.query() == null && filter.referenceTitle() == null;
        if (!isEmpty) {
            return filter;
        }

        java.util.regex.Matcher actorMatcher = ACTOR_HINT.matcher(message);
        if (actorMatcher.find()) {
            String name = stripTrailingParticle(actorMatcher.group(1));
            return new ExtractedFilter(filter.intent(), null, null, null, name, "actor", null, filter.count());
        }

        java.util.regex.Matcher directorMatcher = DIRECTOR_HINT.matcher(message);
        if (directorMatcher.find()) {
            String name = stripTrailingParticle(
                    directorMatcher.group(1) != null ? directorMatcher.group(1) : directorMatcher.group(2));
            return new ExtractedFilter(filter.intent(), null, null, null, name, "director", null, filter.count());
        }

        java.util.regex.Matcher keywordMatcher = KEYWORD_HINT.matcher(message);
        if (keywordMatcher.find()) {
            String keyword = stripTrailingParticle(
                    keywordMatcher.group(1) != null ? keywordMatcher.group(1) : keywordMatcher.group(2));
            return new ExtractedFilter(filter.intent(), null, null, null, keyword, "keyword", null, filter.count());
        }

        return filter;
    }

    private static final java.util.regex.Pattern MOVIE_QUESTION_HINT = java.util.regex.Pattern.compile(
            "영화\\s*['\"]?([가-힣a-zA-Z0-9:!~\\-\\s]{1,20}?)['\"]?\\s*(?:에\\s*대해서?|의\\s*정보|줄거리|스토리)?\\s*"
                    + "(?:알려줘|알려주세요|말해줘|설명해줘|궁금해|궁금합니다|뭐야|뭔가요|뭔지)");

    // Gemini 추출 결과가 완전히 비어있는 recommend일 때만(장르/연도/검색어/기준영화 전부 null), "영화 OOO에
    // 대해 알려줘"처럼 특정 제목을 콕 집어 물어본 패턴을 정규식으로 한 번 더 확인합니다. 이 패턴은 명백히
    // "영화 한 편 자체"에 대한 질문이라 movie_question으로 바로잡고, 그 제목을 query로 채웁니다.
    private ExtractedFilter applyMovieQuestionHintFallback(ExtractedFilter filter, String message) {
        boolean isEmpty = "recommend".equals(filter.intent())
                && filter.genre() == null && filter.year() == null && filter.runtimeMaxMinutes() == null
                && filter.query() == null && filter.referenceTitle() == null;
        if (!isEmpty) {
            return filter;
        }

        java.util.regex.Matcher matcher = MOVIE_QUESTION_HINT.matcher(message);
        if (!matcher.find()) {
            return filter;
        }
        String title = matcher.group(1).trim();
        if (title.isEmpty()) {
            return filter;
        }
        return new ExtractedFilter("movie_question", null, null, null, title, "title", null, filter.count());
    }

    private static final java.util.regex.Pattern COUNT_HINT = java.util.regex.Pattern.compile("(\\d+)\\s*(?:개|편|가지)");

    // Gemini가 count를 못 뽑았을 때만, "2개"/"3편"처럼 숫자로 명시된 개수를 정규식으로 한 번 더 확인합니다.
    private ExtractedFilter applyCountHintFallback(ExtractedFilter filter, String message) {
        if (filter.count() != null) {
            return filter;
        }
        java.util.regex.Matcher matcher = COUNT_HINT.matcher(message);
        if (!matcher.find()) {
            return filter;
        }
        int count = Integer.parseInt(matcher.group(1));
        return new ExtractedFilter(
                filter.intent(), filter.genre(), filter.year(), filter.runtimeMaxMinutes(),
                filter.query(), filter.queryField(), filter.referenceTitle(), count);
    }

    private static final java.util.regex.Pattern MORE_HINT =
            java.util.regex.Pattern.compile("더\\s*추천|더\\s*보여|또\\s*추천|비슷한|다른\\s*(?:걸로|거|영화|것)");

    // Gemini 추출 결과가 완전히 비어있는데(장르/연도/검색어/기준영화 전부 null) "더 추천해줘"/"비슷한 걸로"처럼
    // 특정 영화를 다시 짚지 않고 이어달라고만 한 경우, 직전에 실제로 추천했던 영화 하나를 referenceTitle로
    // 삼습니다. 그러면 resolveReferenceGenre()가 그 영화를 서버에서 다시 조회해 실제 장르를 알아내므로,
    // Gemini가 맥락만 보고 엉뚱한 장르를 지어내는 걸 막을 수 있습니다.
    private ExtractedFilter applyMoreHintFallback(ExtractedFilter filter, String message, List<ChatMessage> fullHistory) {
        boolean isEmpty = "recommend".equals(filter.intent())
                && filter.genre() == null && filter.year() == null && filter.runtimeMaxMinutes() == null
                && filter.query() == null && filter.referenceTitle() == null;
        if (!isEmpty || !MORE_HINT.matcher(message).find()) {
            return filter;
        }

        String lastMovieId = lastRecommendedMovieId(fullHistory);
        if (lastMovieId == null) {
            return filter;
        }
        String[] parts = lastMovieId.split("_", 2);
        if (parts.length != 2) {
            return filter;
        }
        try {
            String title = movieService.getDetail(parts[0], parts[1]).title();
            if (title == null) {
                return filter;
            }
            return new ExtractedFilter(
                    filter.intent(), null, null, null, null, null, title, filter.count());
        } catch (Exception e) {
            log.warn("'더 추천해줘' 안전망에서 직전 추천 영화({})를 다시 조회하지 못했습니다.", lastMovieId, e);
            return filter;
        }
    }

    // 가장 최근에 실제로 영화를 추천했던 AI 메시지의 첫 번째 영화 id를 찾습니다.
    private String lastRecommendedMovieId(List<ChatMessage> fullHistory) {
        for (int i = fullHistory.size() - 1; i >= 0; i--) {
            ChatMessage message = fullHistory.get(i);
            if ("ai".equals(message.getRole()) && message.getMovieIds() != null && !message.getMovieIds().isBlank()) {
                return message.getMovieIds().split(",")[0].trim();
            }
        }
        return null;
    }

    private String stripTrailingParticle(String name) {
        if (name.length() > 2 && TRAILING_PARTICLES.contains(name.charAt(name.length() - 1))) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    private List<GeminiClient.ChatTurn> toGeminiTurns(List<ChatMessage> history) {
        return history.stream()
                .map(message -> new GeminiClient.ChatTurn("user".equals(message.getRole()) ? "user" : "model", message.getContent()))
                .toList();
    }
}
