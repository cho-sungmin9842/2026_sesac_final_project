package com.moviepick.backend.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.booking.BookingService;
import com.moviepick.backend.booking.dto.BookingDto;
import com.moviepick.backend.booking.dto.BookingRequest;
import com.moviepick.backend.chat.dto.ChatConversationDto;
import com.moviepick.backend.chat.dto.ChatMessageDto;
import com.moviepick.backend.chat.dto.ChatRequestDto;
import com.moviepick.backend.chat.dto.ChatResponseDto;
import com.moviepick.backend.chat.dto.SeatStatusDto;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.movie.MovieService;
import com.moviepick.backend.movie.dto.ActorDto;
import com.moviepick.backend.movie.dto.MovieDetailDto;
import com.moviepick.backend.movie.dto.MovieSummaryDto;
import com.moviepick.backend.screening.Screening;
import com.moviepick.backend.screening.ScreeningRepository;
import com.moviepick.backend.screening.Seat;
import com.moviepick.backend.screening.SeatRepository;
import com.moviepick.backend.screening.SeatStatus;
import com.moviepick.backend.screening.dto.SeatDto;
import com.moviepick.backend.wishlist.WishlistService;
import com.moviepick.backend.wishlist.dto.WishlistRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    // 사용자가 개수를 안 밝혔을 때 기본으로 보여줄 개수(화면 카드 그리드 3열에 맞춰 둘 줄 꽉 차는 값).
    private static final int DEFAULT_RECOMMENDATIONS = 6;
    // 사용자가 숫자로 개수를 콕 집어 말했을 때 허용하는 최대치(그 이상은 과도한 KMDB 조회/카드 렌더링
    // 부담이 있어 여기서 자릅니다).
    private static final int MAX_RECOMMENDATIONS = 10;
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

    // 대화 제목(사이드바 표시용)은 그 대화의 첫 메시지를 이 길이로 줄입니다(ChatGPT 사이드바와 비슷한 길이).
    private static final int TITLE_MAX_LENGTH = 24;

    private final GeminiClient geminiClient;
    private final MovieService movieService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final WishlistService wishlistService;
    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;
    private final BookingService bookingService;

    public ChatService(
            GeminiClient geminiClient,
            MovieService movieService,
            ChatMessageRepository chatMessageRepository,
            ChatConversationRepository chatConversationRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            WishlistService wishlistService,
            ScreeningRepository screeningRepository,
            SeatRepository seatRepository,
            BookingService bookingService
    ) {
        this.geminiClient = geminiClient;
        this.movieService = movieService;
        this.chatMessageRepository = chatMessageRepository;
        this.chatConversationRepository = chatConversationRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.wishlistService = wishlistService;
        this.screeningRepository = screeningRepository;
        this.seatRepository = seatRepository;
        this.bookingService = bookingService;
    }

    // AI 추천 사이드바 - 이 사용자의 대화방 목록을 최근 대화가 위로 오도록 돌려줍니다.
    public List<ChatConversationDto> listConversations(Long userId) {
        return chatConversationRepository.findByUserIdOrderByLastMessageAtDesc(userId).stream()
                .map(c -> new ChatConversationDto(c.getId(), c.getTitle(), c.getCreatedAt(), c.getLastMessageAt()))
                .toList();
    }

    // 사이드바에서 과거 대화방을 클릭했을 때 그 대화방의 메시지 내역을 시간순으로 돌려줍니다. 추천 메시지는
    // movie_ids로 KMDB 상세를 다시 조회해 추천 카드까지 그대로 복원합니다. 좌석 현황 답변은 screening_id로
    // 다시 조회하는데, 그 안에서 Screening.theater를 읽어야 해서(지연 로딩) 트랜잭션 안에서 실행해야 합니다.
    @Transactional(readOnly = true)
    public List<ChatMessageDto> historyForConversation(Long userId, Long conversationId) {
        ChatConversation conversation = requireOwnedConversation(userId, conversationId);
        return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(message -> new ChatMessageDto(
                        message.getRole(), message.getContent(), moviesFrom(message.getMovieIds()),
                        seatStatusFrom(message.getScreeningId())))
                .toList();
    }

    // AI 추천 사이드바에서 대화방을 우클릭 삭제했을 때 - 메시지는 FK ON DELETE CASCADE로 함께 지워집니다.
    public void deleteConversation(Long userId, Long conversationId) {
        ChatConversation conversation = requireOwnedConversation(userId, conversationId);
        chatConversationRepository.delete(conversation);
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

    // 새 대화의 첫 메시지(request.conversationId()가 없음)부터 메시지 저장까지 한 트랜잭션 안에서
    // 처리해야 "대화방 생성 -> 메시지 저장 -> last_message_at 갱신"이 중간에 끊기지 않습니다
    // (open-in-view: false라 트랜잭션 밖에서 엔티티를 다루면 "No EntityManager with actual transaction
    // available" 류의 오류가 날 수 있습니다).
    @Transactional
    public ChatResponseDto reply(Long userId, ChatRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));

        ChatConversation conversation = request.conversationId() == null
                ? startConversation(user, request.message())
                : requireOwnedConversation(userId, request.conversationId());

        List<ChatMessage> fullHistory = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());

        // "찜해줘"는 Gemini 판단이 필요 없는 결정적 동작이라, 조건 추출 자체를 거치지 않고 바로 처리합니다.
        // 다만 "찜 안 하고 싶어"처럼 부정/취소 표현이 섞여 있으면 이 결정적 분기를 건너뛰고 일반 조건
        // 추출로 넘깁니다(그래야 진짜 의도에 맞게 답할 수 있습니다).
        if (WISHLIST_HINT.matcher(request.message()).find() && !WISHLIST_NEGATION_HINT.matcher(request.message()).find()) {
            return handleWishlistRequest(user, conversation, request.message(), fullHistory);
        }

        // "지금 상영중인 영화" 요청도 Gemini의 ExtractedFilter(연도 전체 단위 필터만 있고 "최근 개봉일자
        // 범위" 개념이 없음)로는 제대로 답할 수 없어서, 예매 화면과 같은 실제 "상영중" 로직(MovieService.
        // getNowShowing, 최근 4주 개봉작)으로 바로 처리합니다. 다만 "상영 중인 영화 아가미의 상영정보를
        // 알려줘"처럼 특정 영화를 콕 집어 그 영화의 상영 스케줄을 물어본 거라면(SCREENING_QUESTION_HINT도
        // 함께 매치), 이 "전체 목록" 분기가 가로채지 않고 아래 movie_question 경로로 흘러가야 합니다 -
        // 안 그러면 특정 영화 질문인데도 항상 "지금 상영중인 영화 N편" 목록으로 엉뚱하게 답하는 버그가
        // 있었습니다.
        if (NOW_SHOWING_HINT.matcher(request.message()).find()
                && !SCREENING_QUESTION_HINT.matcher(request.message()).find()) {
            return handleNowShowingRequest(user, conversation, request.message());
        }

        // "10/11(일) 2관: 20:05 예매 좌석 현황을 알려줘"처럼 직전에 안내한 특정 회차의 좌석 현황을 물어보는
        // 요청도 날짜/상영관/시간이 전부 숫자 패턴이라 Gemini 없이 정규식으로 바로 처리합니다.
        if (SEAT_STATUS_HINT.matcher(request.message()).find()) {
            return handleSeatStatusRequest(user, conversation, request.message(), fullHistory);
        }

        // "성인 2명 D열 2,3번을 예매해줘"처럼 인원 구분과 좌석을 직접 말하며 예매를 요청하면, 바로 위에서
        // 안내한(또는 좌석 현황을 물어봤던) 회차를 기준으로 Gemini 없이 바로 실제 예매를 생성합니다.
        if (BOOKING_REQUEST_HINT.matcher(request.message()).find()) {
            return handleBookingRequest(user, conversation, request.message(), fullHistory);
        }

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
            String reply = pickOffTopicReply();
            return persistTurn(user, conversation, request.message(), reply, List.of());
        }

        // "영화 추천해줘"가 아니라 "그 영화 장르/배우 알려줘"처럼 이미 나온(또는 대화 맥락 속) 특정 영화에 대한
        // 질문이면, 추천 목록을 다시 만드는 대신 그 영화 한 편의 정보로 답합니다. 그중에서도 "상영정보/상영
        // 시간/몇 시에 하는지"처럼 실제 상영 스케줄을 물어본 거라면, 장르/감독/배우 같은 일반 정보가 아니라
        // screenings 테이블의 실제 이번 주 상영 스케줄로 답해야 합니다(전에는 둘을 구분하지 않고 항상 일반
        // 정보만 줘서, "상영 정보 알려줘"라고 물어도 장르/감독/배우만 나오는 버그가 있었습니다).
        if ("movie_question".equals(filter.intent()) && filter.query() != null) {
            if (SCREENING_QUESTION_HINT.matcher(request.message()).find()) {
                return answerScreeningQuestion(user, conversation, request.message(), filter.query());
            }
            return answerMovieQuestion(user, conversation, request.message(), filter.query());
        }

        // "이 영화와 같은 장르로"처럼 이전에 나온 영화를 기준 삼으라고 했으면, Gemini에게 그 영화의 장르를
        // 다시 "기억해내라"고 시키는 대신 그 영화를 직접 재조회해서 실제 장르를 가져옵니다(훨씬 안정적입니다).
        ExtractedFilter effectiveFilter = resolveReferenceGenre(filter, fullHistory);

        // "같은 장르의 영화들을 추천해줘"처럼 이어가는 요청이 방금 봤던 영화를 그대로 다시 추천해버리는
        // 문제(예: 액션 2편 추천 -> "같은 장르로" -> 그 2편이 6편 중 그대로 다시 포함) - 이번 대화에서
        // 이미 추천했던 영화는 다시 후보에 넣지 않습니다.
        java.util.Set<String> alreadyRecommendedIds = alreadyRecommendedMovieIds(fullHistory);
        List<MovieSummaryDto> candidates = findCandidates(effectiveFilter, alreadyRecommendedIds);
        String reply = candidates.isEmpty()
                ? "말씀하신 조건에 맞는 국내 영화를 찾지 못했어요. 장르나 연도 조건을 조금 완화해서 다시 물어봐주시겠어요?"
                : buildReply(effectiveFilter, candidates);

        return persistTurn(user, conversation, request.message(), reply, candidates);
    }

    // "새 대화 시작" 뒤 첫 메시지 - 대화방을 새로 만들고 제목은 그 메시지를 간단히 줄인 값으로 둡니다.
    private ChatConversation startConversation(User user, String firstMessage) {
        return chatConversationRepository.save(new ChatConversation(user, deriveTitle(firstMessage)));
    }

    private ChatConversation requireOwnedConversation(Long userId, Long conversationId) {
        ChatConversation conversation = chatConversationRepository.findById(conversationId)
                .orElseThrow(() -> new ApiException("대화를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (!conversation.isOwnedBy(userId)) {
            throw new ApiException("본인의 대화만 이용할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        return conversation;
    }

    // Gemini를 따로 부르지 않고(호출 비용 절약), 첫 메시지를 공백만 정리해 적당한 길이로 자릅니다.
    private String deriveTitle(String firstMessage) {
        String cleaned = firstMessage == null ? "" : firstMessage.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return "새 대화";
        }
        return cleaned.length() <= TITLE_MAX_LENGTH ? cleaned : cleaned.substring(0, TITLE_MAX_LENGTH) + "...";
    }

    private ExtractedFilter resolveReferenceGenre(ExtractedFilter filter, List<ChatMessage> fullHistory) {
        if (filter.genre() != null || filter.referenceTitle() == null) {
            return filter;
        }
        String resolvedGenre = resolveGenreFromLastRecommendation(filter.referenceTitle(), fullHistory)
                .orElseGet(() -> resolveGenreByTitleSearch(filter.referenceTitle()));
        if (resolvedGenre == null) {
            return filter;
        }
        return new ExtractedFilter(
                filter.intent(), resolvedGenre, filter.year(), filter.runtimeMaxMinutes(),
                filter.query(), filter.queryField(), filter.referenceTitle(), filter.count());
    }

    // referenceTitle이 직전 AI 추천 목록에 실제로 있었던 영화라면, 그 영화 한 편의 여러 장르 태그 중
    // 아무거나(예: KMDB가 나열한 첫 번째) 고르는 대신, 그 목록 전체가 "공통으로" 가진 장르를 이어갑니다.
    // (예: "액션"으로 추천받았던 "한복 입은 남자"의 KMDB 장르 순서가 "드라마,액션,SF,..."라서, 첫 번째만
    // 보면 엉뚱하게 "드라마"로 새겨버립니다 - 실제로 그 추천에 쓰인 조건은 액션이었는데도.) 추천 목록의
    // 모든 영화에 공통으로 붙어있는 장르는 그 추천이 실제로 사용한 조건일 가능성이 매우 높습니다.
    private java.util.Optional<String> resolveGenreFromLastRecommendation(String referenceTitle, List<ChatMessage> fullHistory) {
        for (int i = fullHistory.size() - 1; i >= 0; i--) {
            ChatMessage message = fullHistory.get(i);
            if (!"ai".equals(message.getRole()) || message.getMovieIds() == null || message.getMovieIds().isBlank()) {
                continue;
            }
            List<MovieSummaryDto> recommended = moviesFrom(message.getMovieIds());
            boolean containsReferenceTitle = recommended.stream()
                    .anyMatch(movie -> titleMatches(movie.title(), referenceTitle));
            if (!containsReferenceTitle) {
                continue;
            }
            java.util.LinkedHashSet<String> commonGenres = null;
            for (MovieSummaryDto movie : recommended) {
                java.util.LinkedHashSet<String> genres = new java.util.LinkedHashSet<>(movie.genres());
                commonGenres = commonGenres == null ? genres : intersect(commonGenres, genres);
            }
            return commonGenres != null && !commonGenres.isEmpty()
                    ? java.util.Optional.of(commonGenres.iterator().next())
                    : java.util.Optional.empty();
        }
        return java.util.Optional.empty();
    }

    private java.util.LinkedHashSet<String> intersect(java.util.LinkedHashSet<String> a, java.util.LinkedHashSet<String> b) {
        a.retainAll(b);
        return a;
    }

    // KMDB 표기 띄어쓰기 차이(예: "퍼펙트게임" vs "퍼펙트 게임")에 영향받지 않도록 공백을 무시하고 비교합니다.
    private boolean titleMatches(String movieTitle, String referenceTitle) {
        if (movieTitle == null || referenceTitle == null) {
            return false;
        }
        return movieTitle.replaceAll("\\s+", "").equalsIgnoreCase(referenceTitle.replaceAll("\\s+", ""));
    }

    // referenceTitle이 직전 추천 목록에 없었을 때(예: movie_question으로 영화 한 편만 얘기하다가 "이 영화와
    // 같은 장르로 추천해줘"라고 이어간 경우)의 기존 방식 - 공통 장르를 계산할 목록 자체가 없으므로, 그 영화를
    // 다시 조회해 첫 번째 장르 태그를 대표 장르로 씁니다.
    private String resolveGenreByTitleSearch(String referenceTitle) {
        List<MovieSummaryDto> matches =
                movieService.search(referenceTitle, List.of(), null, null, "latest", 1, 1, "title").movies();
        if (matches.isEmpty() || matches.get(0).genres().isEmpty()) {
            return null;
        }
        return matches.get(0).genres().get(0);
    }

    // ---- 특정 영화에 대한 질문(장르/배우/감독/줄거리 등) ----

    private ChatResponseDto answerMovieQuestion(User user, ChatConversation conversation, String userMessage, String movieTitle) {
        List<MovieSummaryDto> matches = movieService.search(movieTitle, List.of(), null, null, "latest", 1, 1, "title").movies();
        if (matches.isEmpty()) {
            String reply = "'%s' 영화를 찾지 못했어요. 정확한 제목으로 다시 물어봐주시겠어요?".formatted(movieTitle);
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        String[] idParts = matches.get(0).id().split("_", 2);
        MovieDetailDto detail = movieService.getDetail(idParts[0], idParts[1]);
        String reply = describeMovieDetail(detail);

        return persistTurn(user, conversation, userMessage, reply, List.of(matches.get(0)));
    }

    private static final java.util.regex.Pattern SCREENING_QUESTION_HINT = java.util.regex.Pattern.compile(
            "상영\\s*(정보|시간|스케줄|일정|관)|몇\\s*시에?\\s*(상영|해|하나|하는)|언제\\s*상영|상영표");

    // 실제 상영 스케줄(상영관/날짜/시간)로 답합니다 - 장르/감독/배우 같은 일반 정보(describeMovieDetail)와는
    // 다른 질문이라 구분해서 처리합니다. 예매 화면과 완전히 같은 데이터 소스(screenings 테이블, 매주
    // 월요일 자정 배치가 생성)를 그대로 써서 실제 예매 가능한 회차만 보여줍니다.
    private ChatResponseDto answerScreeningQuestion(User user, ChatConversation conversation, String userMessage, String movieTitle) {
        List<MovieSummaryDto> matches = movieService.search(movieTitle, List.of(), null, null, "latest", 1, 1, "title").movies();
        if (matches.isEmpty()) {
            String reply = "'%s' 영화를 찾지 못했어요. 정확한 제목으로 다시 물어봐주시겠어요?".formatted(movieTitle);
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        MovieSummaryDto movie = matches.get(0);
        List<Screening> screenings = screeningRepository.findByMovieIdOrderByDateAscStartTimeAsc(movie.id());
        if (screenings.isEmpty()) {
            String reply = "'%s'의 이번 주 상영 정보가 없어요. 상영이 종료됐거나 이번 주에는 편성되지 않았을 수 있어요."
                    .formatted(movie.title());
            return persistTurn(user, conversation, userMessage, reply, List.of(movie));
        }

        String reply = "'%s' 상영 정보예요.\n%s".formatted(movie.title(), describeScreenings(screenings));
        return persistTurn(user, conversation, userMessage, reply, List.of(movie));
    }

    private static final String[] KOREAN_WEEKDAYS = {"월", "화", "수", "목", "금", "토", "일"};

    // 같은 날짜+상영관이면 시간만 이어붙여서("10:00, 13:30") 한 줄로 요약합니다(상영관/날짜마다 줄이
    // 따로 생기면 하루에 여러 회차가 있을 때 너무 길어집니다).
    private String describeScreenings(List<Screening> screenings) {
        Map<String, List<Screening>> grouped = new LinkedHashMap<>();
        for (Screening screening : screenings) {
            String key = screening.getDate() + "|" + screening.getTheater().getName();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(screening);
        }

        return grouped.values().stream()
                .map(group -> {
                    Screening first = group.get(0);
                    LocalDate date = first.getDate();
                    String weekday = KOREAN_WEEKDAYS[date.getDayOfWeek().getValue() - 1];
                    String times = group.stream().map(s -> s.getStartTime().toString()).collect(Collectors.joining(", "));
                    return "- %d/%d(%s) %s: %s".formatted(date.getMonthValue(), date.getDayOfMonth(), weekday, first.getTheater().getName(), times);
                })
                .collect(Collectors.joining("\n"));
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

    private ChatResponseDto persistTurn(
            User user, ChatConversation conversation, String userMessage, String reply, List<MovieSummaryDto> movies
    ) {
        return persistTurn(user, conversation, userMessage, reply, movies, null);
    }

    private ChatResponseDto persistTurn(
            User user, ChatConversation conversation, String userMessage, String reply,
            List<MovieSummaryDto> movies, SeatStatusDto seatStatus
    ) {
        Long screeningId = seatStatus == null ? null : seatStatus.screeningId();
        chatMessageRepository.save(new ChatMessage(user, conversation.getId(), "user", userMessage, null, null));
        chatMessageRepository.save(new ChatMessage(user, conversation.getId(), "ai", reply, joinIds(movies), screeningId));
        conversation.touch();
        chatConversationRepository.save(conversation);
        return new ChatResponseDto(conversation.getId(), reply, movies, seatStatus);
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
                  **직전 답변이 특정 영화 하나에 대한 게 아니라 장르 조건으로 여러 편을 추천한 목록이었을
                  때도 마찬가지다** - "같은 장르의 영화들을 추천해줘"처럼 영화 제목을 콕 집지 않고 그냥
                  "같은 장르로" 이어가 달라는 요청이면, 직전 AI 답변이 추천했던 영화 목록의 첫 번째 영화
                  제목을 referenceTitle로 채워라(그 영화를 다시 조회해서 실제 장르를 알아내 이어간다).
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
                - (직전 답변이 액션 장르로 "한복 입은 남자" 등 여러 편을 추천한 목록이었던 상황) "같은
                  장르의 영화들을 추천해줘" -> {"intent":"recommend","referenceTitle":"한복 입은 남자","genre":null,"query":null,"year":null,"count":null}
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

    private List<MovieSummaryDto> findCandidates(ExtractedFilter filter, java.util.Set<String> excludeMovieIds) {
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
            movies = movieService.search("", genres, filter.year(), null, "latest", 1, limit * 3, "title").movies();
        }

        return movies.stream()
                .filter(movie -> matchesRuntime(movie, filter.runtimeMaxMinutes()))
                .filter(movie -> !excludeMovieIds.contains(movie.id()))
                .limit(limit)
                .toList();
    }

    // 이 대화(user)에서 지금까지 AI가 실제로 추천했던 영화 id를 전부 모읍니다(움직이는 창 없이 저장된
    // 전체 기록 기준 - 조건 추출용 history와 달리 "화면에서 이미 본 카드를 또 보여주지 않기" 목적이라
    // 대화가 아무리 길어도 전부 확인해야 합니다).
    private java.util.Set<String> alreadyRecommendedMovieIds(List<ChatMessage> fullHistory) {
        return fullHistory.stream()
                .filter(message -> "ai".equals(message.getRole()) && message.getMovieIds() != null && !message.getMovieIds().isBlank())
                .flatMap(message -> Arrays.stream(message.getMovieIds().split(",")))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());
    }

    private int resolveLimit(Integer requestedCount) {
        if (requestedCount == null || requestedCount < 1) {
            return DEFAULT_RECOMMENDATIONS;
        }
        return Math.min(requestedCount, MAX_RECOMMENDATIONS);
    }

    private List<MovieSummaryDto> searchNonEmpty(String query, List<String> genres, String year, String field, int limit) {
        return movieService.search(query, genres, year, null, "latest", 1, limit * 3, field).movies();
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

    // 매번 똑같은 문구만 나오면 기계적으로 느껴져서, 뜻은 같지만 표현이 다른 몇 가지 중 하나를 무작위로
    // 고릅니다(여기도 Gemini를 또 부르진 않습니다 - 고정 후보 중 고르는 것뿐이라 호출 비용이 없습니다).
    private static final List<String> OFF_TOPIC_REPLIES = List.of(
            "저는 영화 추천을 도와드리는 AI예요 🎬 보고 싶은 장르나 배우, 감독, 소재를 말씀해주시면 국내 영화를 찾아드릴게요!",
            "그건 제가 답하기 어려운 질문이네요 😅 대신 영화 이야기라면 자신 있어요 - 어떤 장르나 배우가 끌리세요?",
            "영화 추천 전문 AI라 그 질문엔 답을 못 드려요 🎬 요즘 보고 싶은 분위기나 소재를 알려주시면 바로 찾아드릴게요!"
    );

    private String pickOffTopicReply() {
        return OFF_TOPIC_REPLIES.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(OFF_TOPIC_REPLIES.size()));
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

    private static final java.util.regex.Pattern WISHLIST_HINT = java.util.regex.Pattern.compile("찜");
    // "찜 안 하고 싶어"/"찜 말고"/"찜 취소해줘"처럼 "찜"이 들어가도 실제로는 찜을 하지 말라는(또는 찜과
    // 무관한) 뜻이면 찜 처리로 바로 새지 않도록, 근처에 부정/취소 표현이 있으면 이 결정적 분기를 건너뛰고
    // 일반 조건 추출(Gemini)로 넘깁니다.
    private static final java.util.regex.Pattern WISHLIST_NEGATION_HINT =
            java.util.regex.Pattern.compile("찜.{0,4}(안|말고|말아|하지\\s*마|취소|빼)|(안|말고|하지\\s*마).{0,4}찜");

    // "이 영화 찜해줘"처럼 직전에 추천받은 영화를 찜 목록에 담아달라는 요청은 판단이 필요한 게 아니라 그냥
    // 실행하면 되는 결정적인 동작이라, Gemini에게 물어보지 않고 정규식으로 바로 처리합니다(API 호출도
    // 아끼고, "장르가 뭐야" 같은 recommend/movie_question 분류로 잘못 새는 것도 원천 차단됩니다).
    private ChatResponseDto handleWishlistRequest(
            User user, ChatConversation conversation, String userMessage, List<ChatMessage> fullHistory
    ) {
        List<MovieSummaryDto> movies = lastRecommendedMovies(fullHistory);
        if (movies.isEmpty()) {
            String reply = "먼저 추천받은 영화가 있어야 찜할 수 있어요! 어떤 영화를 찾아드릴까요?";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }
        for (MovieSummaryDto movie : movies) {
            wishlistService.add(user.getId(), new WishlistRequest(movie.id(), movie.title(), movie.posterUrl()));
        }
        String titles = movies.stream().map(MovieSummaryDto::title).collect(Collectors.joining(", "));
        String reply = "%s %s 찜했습니다."
                .formatted(titles, movies.size() > 1 ? "등 %d편을".formatted(movies.size()) : "을(를)");
        // 찜하기 확인 메시지에는 영화 카드 목록을 다시 붙이지 않습니다 - movies를 그대로 넘기면
        // ChatMovieRecommendation이 방금 봤던 추천 카드 그리드를 통째로 다시 그려서, 화면상 방금 추천
        // 답변과 거의 구분이 안 되는 문제가 있었습니다(사용자가 "안 고쳐졌다"고 재차 신고한 원인).
        return persistTurn(user, conversation, userMessage, reply, List.of());
    }

    private static final java.util.regex.Pattern NOW_SHOWING_HINT = java.util.regex.Pattern.compile("상영\\s*중");

    // "지금 상영중인 영화 찾아줘"도 Gemini의 ExtractedFilter로는 답할 수 없는 요청입니다 - year는 "그 해
    // 전체"만 표현할 수 있지 "최근 개봉일자 범위(상영중)" 개념이 없어서, 이 요청도 조건 추출을 거치지 않고
    // 예매 화면의 "상영중인 영화" 목록과 같은 기준(MovieService.getNowShowing, 최근 4주 개봉작)으로 바로
    // 답합니다.
    private ChatResponseDto handleNowShowingRequest(User user, ChatConversation conversation, String userMessage) {
        List<MovieSummaryDto> movies = movieService.getNowShowing(DEFAULT_RECOMMENDATIONS).movies();
        if (movies.isEmpty()) {
            String reply = "지금 상영중인 국내 영화를 찾지 못했어요.";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }
        String titles = movies.stream().map(MovieSummaryDto::title).collect(Collectors.joining(", "));
        String reply = "지금 상영중인 영화로 %s %s 찾았어요! 마음에 드는 작품을 골라보세요 🍿\n영화 포스터를 클릭하시면 자세한 영화 정보를 보실 수 있습니다."
                .formatted(titles, movies.size() > 1 ? "등 %d편을".formatted(movies.size()) : "을(를)");
        return persistTurn(user, conversation, userMessage, reply, movies);
    }

    private static final java.util.regex.Pattern SEAT_STATUS_HINT = java.util.regex.Pattern.compile(
            "좌석\\s*(현황|상태)|예매\\s*가능한\\s*좌석|빈\\s*(자리|좌석)|남은\\s*좌석");
    private static final java.util.regex.Pattern DATE_FRAGMENT = java.util.regex.Pattern.compile("(\\d{1,2})\\s*/\\s*(\\d{1,2})");
    private static final java.util.regex.Pattern THEATER_FRAGMENT = java.util.regex.Pattern.compile("(\\d+)\\s*관");
    private static final java.util.regex.Pattern TIME_FRAGMENT = java.util.regex.Pattern.compile("(\\d{1,2})\\s*:\\s*(\\d{2})");

    // "10/11(일) 2관: 20:05 예매 좌석 현황을 알려줘"처럼, 직전에 안내한 상영정보 목록에서 특정 회차를
    // 콕 집어 좌석 현황을 물어본 요청입니다. 날짜/상영관/시간이 전부 숫자 패턴이라 Gemini 없이 정규식
    // 으로 바로 그 회차를 찾아 실제 seats 테이블 상태로 답합니다. 어떤 영화인지는 말하지 않는 게 보통이라
    // (바로 위에서 그 영화 얘기를 하고 있었으니까), 직전에 실제로 다뤘던 영화를 그대로 기준으로 삼습니다.
    private ChatResponseDto handleSeatStatusRequest(
            User user, ChatConversation conversation, String userMessage, List<ChatMessage> fullHistory
    ) {
        String lastMovieId = lastRecommendedMovieId(fullHistory);
        if (lastMovieId == null) {
            String reply = "어떤 영화의 좌석 현황인지 알 수 없어요. 먼저 영화 이름을 말씀해주시거나 상영정보를 물어봐주세요.";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        java.util.regex.Matcher dateMatcher = DATE_FRAGMENT.matcher(userMessage);
        java.util.regex.Matcher theaterMatcher = THEATER_FRAGMENT.matcher(userMessage);
        java.util.regex.Matcher timeMatcher = TIME_FRAGMENT.matcher(userMessage);
        if (!dateMatcher.find() || !theaterMatcher.find() || !timeMatcher.find()) {
            String reply = "몇 월 며칠, 몇 관, 몇 시 회차인지 알려주시면 좌석 현황을 확인해드릴게요. (예: \"10/11 2관 20:05 좌석 현황 알려줘\")";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        int month = Integer.parseInt(dateMatcher.group(1));
        int day = Integer.parseInt(dateMatcher.group(2));
        String theaterLabel = theaterMatcher.group(1) + "관";
        String timeLabel = "%02d:%02d".formatted(Integer.parseInt(timeMatcher.group(1)), Integer.parseInt(timeMatcher.group(2)));

        List<Screening> screenings = screeningRepository.findByMovieIdOrderByDateAscStartTimeAsc(lastMovieId);
        Screening match = screenings.stream()
                .filter(s -> s.getDate().getMonthValue() == month && s.getDate().getDayOfMonth() == day)
                .filter(s -> s.getTheater().getName().equals(theaterLabel))
                .filter(s -> s.getStartTime().toString().startsWith(timeLabel))
                .findFirst()
                .orElse(null);

        if (match == null) {
            String reply = "%d/%d %s %s 회차를 찾지 못했어요. 상영정보를 다시 확인해주시겠어요?"
                    .formatted(month, day, theaterLabel, timeLabel);
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        SeatStatusDto seatStatus = toSeatStatus(match);
        String reply = "'%s' %s %s %s 회차 좌석 현황이에요.\n전체 %d석 중 %d석 예매 가능해요(%d석 예매 완료)."
                .formatted(seatStatus.movieTitle(), seatStatus.dateLabel(), seatStatus.theaterName(), seatStatus.timeLabel(),
                        seatStatus.totalSeats(), seatStatus.availableSeats(), seatStatus.totalSeats() - seatStatus.availableSeats());

        return persistTurn(user, conversation, userMessage, reply, List.of(), seatStatus);
    }

    // 저장된 메시지를 다시 불러올 때, screening_id 하나로 좌석 현황을 그대로 복원합니다.
    private SeatStatusDto seatStatusFrom(Long screeningId) {
        if (screeningId == null) {
            return null;
        }
        return screeningRepository.findById(screeningId).map(this::toSeatStatus).orElse(null);
    }

    private SeatStatusDto toSeatStatus(Screening screening) {
        List<Seat> seats = seatRepository.findByScreeningIdOrderByRowLabelAscColNoAsc(screening.getId());
        int totalSeats = seats.size();
        int bookedSeats = (int) seats.stream().filter(seat -> seat.getStatus() == SeatStatus.BOOKED).count();
        List<SeatDto> seatDtos = seats.stream().map(seat -> SeatDto.from(seat, false)).toList();

        return new SeatStatusDto(
                screening.getId(),
                movieTitleOrId(screening.getMovieId()),
                screening.getTheater().getName(),
                formatDateLabel(screening.getDate()),
                screening.getStartTime().toString().substring(0, 5),
                totalSeats,
                totalSeats - bookedSeats,
                seatDtos
        );
    }

    private String movieTitleOrId(String compositeMovieId) {
        String[] parts = compositeMovieId.split("_", 2);
        if (parts.length != 2) {
            return compositeMovieId;
        }
        try {
            return movieService.getDetail(parts[0], parts[1]).title();
        } catch (Exception e) {
            return compositeMovieId;
        }
    }

    private String formatDateLabel(LocalDate date) {
        String weekday = KOREAN_WEEKDAYS[date.getDayOfWeek().getValue() - 1];
        return "%d/%d(%s)".formatted(date.getMonthValue(), date.getDayOfMonth(), weekday);
    }

    private static final java.util.regex.Pattern BOOKING_REQUEST_HINT = java.util.regex.Pattern.compile(
            "예매\\s*(해\\s*줘|해\\s*주세요|좀|할게|할래|하고\\s*싶어|해줄래)|결제\\s*(해\\s*줘|해\\s*주세요)");
    // "D열 2,3번"처럼 한 행(row) 안의 여러 열(column)을 한 번에 가리키는 표현.
    private static final java.util.regex.Pattern SEAT_ROW_GROUP = java.util.regex.Pattern.compile(
            "([A-Ha-h])\\s*열\\s*([0-9]+(?:\\s*,\\s*[0-9]+)*)\\s*번?");
    // "D2", "d 3"처럼 행+열을 붙여 쓰는 표현(여러 행에 걸친 좌석도 이걸로 잡습니다).
    private static final java.util.regex.Pattern SEAT_PAIR = java.util.regex.Pattern.compile("([A-Ha-h])\\s*(\\d{1,2})");
    private static final Map<String, String> TICKET_LABEL_TO_CATEGORY =
            Map.of("성인", "adult", "청소년", "teen", "어린이", "child", "우대", "senior");

    // 직전에 좌석 현황을 안내했던(또는 상영정보로 회차를 짚었던) screening_id를 찾습니다 - "예매해줘"에는
    // 보통 회차를 다시 말하지 않으니, 바로 위 맥락에서 가리키는 회차를 그대로 기준 삼습니다.
    private Long lastSeatStatusScreeningId(List<ChatMessage> fullHistory) {
        for (int i = fullHistory.size() - 1; i >= 0; i--) {
            ChatMessage message = fullHistory.get(i);
            if ("ai".equals(message.getRole()) && message.getScreeningId() != null) {
                return message.getScreeningId();
            }
        }
        return null;
    }

    // "D열 2,3번" 또는 "D2, D3"처럼 메시지에 섞인 좌석 표기를 "D2", "D3" 같은 라벨 목록으로 뽑아냅니다.
    private List<String> parseSeatLabels(String message) {
        java.util.regex.Matcher rowGroupMatcher = SEAT_ROW_GROUP.matcher(message);
        if (rowGroupMatcher.find()) {
            String row = rowGroupMatcher.group(1).toUpperCase(Locale.ROOT);
            List<String> labels = new ArrayList<>();
            for (String colText : rowGroupMatcher.group(2).split(",")) {
                labels.add(row + colText.trim());
            }
            return labels;
        }

        List<String> labels = new ArrayList<>();
        java.util.regex.Matcher pairMatcher = SEAT_PAIR.matcher(message);
        while (pairMatcher.find()) {
            labels.add(pairMatcher.group(1).toUpperCase(Locale.ROOT) + pairMatcher.group(2));
        }
        return labels;
    }

    private ChatResponseDto handleBookingRequest(
            User user, ChatConversation conversation, String userMessage, List<ChatMessage> fullHistory
    ) {
        Long screeningId = lastSeatStatusScreeningId(fullHistory);
        if (screeningId == null) {
            String reply = "어떤 회차를 예매할지 알 수 없어요. 먼저 영화의 상영정보나 좌석 현황을 확인해주세요.";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        Map<String, Integer> ticketCounts = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : TICKET_LABEL_TO_CATEGORY.entrySet()) {
            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern.compile(entry.getKey() + "\\s*(\\d+)\\s*명").matcher(userMessage);
            if (matcher.find()) {
                ticketCounts.put(entry.getValue(), Integer.parseInt(matcher.group(1)));
            }
        }
        List<String> seatLabels = parseSeatLabels(userMessage);

        if (ticketCounts.isEmpty() || seatLabels.isEmpty()) {
            String reply = "예매하실 인원 구분(성인/청소년/어린이/우대)과 좌석을 함께 말씀해주세요. "
                    + "(예: \"성인 2명 D열 2,3번 예매해줘\")";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        List<Seat> seatsOnScreening = seatRepository.findByScreeningIdOrderByRowLabelAscColNoAsc(screeningId);
        Map<String, Long> seatIdByLabel = seatsOnScreening.stream()
                .collect(Collectors.toMap(seat -> seat.getRowLabel() + seat.getColNo(), Seat::getId));

        List<Long> seatIds = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (String label : seatLabels) {
            Long seatId = seatIdByLabel.get(label);
            if (seatId == null) {
                notFound.add(label);
            } else {
                seatIds.add(seatId);
            }
        }
        if (!notFound.isEmpty()) {
            String reply = "존재하지 않는 좌석이에요: " + String.join(", ", notFound);
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        int totalTickets = ticketCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (totalTickets != seatIds.size()) {
            String reply = "인원 수(%d명)와 좌석 수(%d석)가 맞지 않아요. 다시 확인해주시겠어요?"
                    .formatted(totalTickets, seatIds.size());
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        try {
            BookingDto booking = bookingService.create(user.getId(), new BookingRequest(screeningId, seatIds, ticketCounts));
            String reply = "예매와 결제가 완료됐어요! 🎬\n'%s' %s %s %s\n좌석: %s · %s원\n예매 확인 알림을 보내드렸어요."
                    .formatted(
                            booking.movieTitle(), booking.theaterName(), booking.showDate(), booking.showtime(),
                            String.join(", ", booking.seats()), "%,d".formatted(booking.totalPrice()));
            return persistTurn(user, conversation, userMessage, reply, List.of());
        } catch (ApiException e) {
            return persistTurn(user, conversation, userMessage, e.getMessage(), List.of());
        }
    }

    private static final java.util.regex.Pattern MORE_HINT =
            java.util.regex.Pattern.compile("더\\s*추천|더\\s*보여|또\\s*추천|비슷한|같은\\s*장르|다른\\s*(?:걸로|거|영화|것)");

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

    // 가장 최근에 실제로 영화를 추천했던 AI 메시지가 추천한 영화 전체 목록을 찾습니다("이 영화 찜해줘"처럼
    // 방금 추천받은 여러 편을 한꺼번에 가리키는 요청에 씁니다).
    private List<MovieSummaryDto> lastRecommendedMovies(List<ChatMessage> fullHistory) {
        for (int i = fullHistory.size() - 1; i >= 0; i--) {
            ChatMessage message = fullHistory.get(i);
            if ("ai".equals(message.getRole()) && message.getMovieIds() != null && !message.getMovieIds().isBlank()) {
                return moviesFrom(message.getMovieIds());
            }
        }
        return List.of();
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
