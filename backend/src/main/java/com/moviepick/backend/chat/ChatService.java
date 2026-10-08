package com.moviepick.backend.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.auth.UserService;
import com.moviepick.backend.auth.dto.PreferredGenresDto;
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
import com.moviepick.backend.review.ReviewService;
import com.moviepick.backend.review.dto.ReviewDto;
import com.moviepick.backend.review.dto.ReviewRequest;
import com.moviepick.backend.screening.Screening;
import com.moviepick.backend.screening.ScreeningRepository;
import com.moviepick.backend.screening.Seat;
import com.moviepick.backend.screening.SeatRepository;
import com.moviepick.backend.screening.SeatStatus;
import com.moviepick.backend.screening.dto.SeatDto;
import com.moviepick.backend.watched.WatchedMovieService;
import com.moviepick.backend.watched.dto.WatchedMovieDto;
import com.moviepick.backend.watched.dto.WatchedMovieRequest;
import com.moviepick.backend.wishlist.WishlistService;
import com.moviepick.backend.wishlist.dto.WishlistDto;
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
    // "상영중인 영화들 중에서 좀비 영화를 알려줘"처럼 상영중 제한과 장르/키워드 조건이 함께 온 요청은,
    // findCandidates가 평소처럼 "최신순 상위 몇 개"만 가져오면 그 몇 개가 하필 지금 상영중인 영화와 하나도
    // 안 겹쳐서 실제로는 맞는 영화가 있는데도 "못 찾았다"고 답하는 문제가 있었습니다(2026-10-08). 그래서
    // 상영중 제한이 있을 땐 거르기 전 후보 풀 자체를 훨씬 넉넉히 가져옵니다.
    private static final int NOW_SHOWING_SEARCH_POOL = 300;

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
    private final ReviewService reviewService;
    private final WatchedMovieService watchedMovieService;
    private final UserService userService;

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
            BookingService bookingService,
            ReviewService reviewService,
            WatchedMovieService watchedMovieService,
            UserService userService
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
        this.reviewService = reviewService;
        this.watchedMovieService = watchedMovieService;
        this.userService = userService;
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

        // 바로 직전 AI 메시지가 리뷰의 평점/내용 중 하나를 되물은 상태라면, 지금 이 메시지는 무슨 내용이든
        // (장르 이름이 섞여 있든 "찜"이 들어있든) 전부 그 답으로 받아야 하므로, 다른 어떤 분기보다도 먼저
        // 확인합니다 - 안 그러면 답변이 우연히 다른 트리거 단어와 겹쳤을 때 엉뚱한 분기로 새서 리뷰가
        // 저장되지 않는 문제가 생깁니다.
        PendingReviewState pendingReviewState = pendingReviewState(fullHistory);
        if (pendingReviewState != PendingReviewState.NONE) {
            MovieSummaryDto movie = lastRecommendedMovies(fullHistory).stream().findFirst().orElse(null);
            if (movie != null) {
                String lastAiMessage = fullHistory.get(fullHistory.size() - 1).getContent();
                return handlePendingReviewReply(user, conversation, request.message(), movie, pendingReviewState, lastAiMessage);
            }
        }

        // 의도 분류 + 조건/슬롯 추출을 한 번의 Gemini 구조화 출력 호출로 전부 처리합니다(예전엔 찜/리뷰/
        // 시청완료/선호장르/상영중/좌석/예매마다 별도 정규식 게이트가 ~20개 있었는데, 비슷한 의미의 새
        // 표현을 못 잡는 버그가 기능마다 반복돼서 의도 분류 자체를 Gemini에 맡기도록 통합했습니다
        // - 2026-10-08). 좌석/날짜/인원수처럼 형식이 고정된 값은 여전히 각 핸들러 내부에서 정규식으로
        // 직접 파싱합니다(이 부분은 이번 세션 내내 버그가 난 적이 없습니다).
        List<GeminiClient.ChatTurn> history = toGeminiTurns(lastN(fullHistory, MAX_HISTORY_TURNS_FOR_EXTRACTION));
        ExtractedFilter filter = extractFilter(history, request.message());
        // 대화가 어떤 영화 얘기로 길게 이어진 뒤라, "배우 OOO 출연작 알려줘"처럼 명백한 새 요청도 Gemini가
        // 가끔 놓치는 경우가 실제로 있었습니다. 아래 안전망들은 intent가 recommend/movie_question이고
        // 다른 조건이 전부 비어있을 때만 동작하므로, 새로 추가된 다른 intent에는 영향이 없습니다.
        filter = applyNameHintFallback(filter, request.message());
        filter = applyMovieQuestionHintFallback(filter, request.message());
        filter = applyCountHintFallback(filter, request.message());
        filter = applyMoreHintFallback(filter, request.message(), fullHistory);

        String intent = filter.intent() == null ? "" : filter.intent();
        switch (intent) {
            case "wishlist_add":
                return handleWishlistRequest(user, conversation, request.message(), filter, fullHistory);
            case "wishlist_remove":
                return handleWishlistRemoveRequest(user, conversation, request.message(), filter);
            case "wishlist_list":
                return handleWishlistListRequest(user, conversation, request.message());
            case "review_add":
                return handleReviewRequest(user, conversation, request.message(), filter, fullHistory);
            case "review_list":
                return handleReviewListRequest(user, conversation, request.message());
            case "review_delete":
                return handleReviewDeleteRequest(user, conversation, request.message(), filter);
            case "watched_add":
                return handleWatchedAddRequest(user, conversation, request.message(), filter, fullHistory);
            case "watched_remove":
                return handleWatchedDeleteRequest(user, conversation, request.message(), filter);
            case "watched_list":
                return handleWatchedListRequest(user, conversation, request.message());
            case "genre_pref_view":
                return handleGenrePreferenceViewRequest(user, conversation, request.message());
            case "genre_pref_update":
                return handleGenrePreferenceUpdateRequest(user, conversation, request.message(), filter);
            case "booking_list":
                return handleBookingListRequest(user, conversation, request.message());
            case "booking_create":
                return handleBookingRequest(user, conversation, request.message(), fullHistory);
            case "seat_status":
                return handleSeatStatusRequest(user, conversation, request.message(), fullHistory);
            case "off_topic":
                // 영화 추천/특정 영화 정보와 전혀 상관없는 질문(날씨, 잡담 등)이면 KMDB를 뒤져 억지로
                // 영화를 끼워 맞추지 않고, 서비스 용도를 안내하는 답으로 바로 응답합니다.
                return persistTurn(user, conversation, request.message(), pickOffTopicReply(), List.of());
            default:
                // recommend, movie_question(query 있을 때), 그리고 그 외 전부 아래 공용 파이프라인으로.
        }

        // "영화 추천해줘"가 아니라 "그 영화 장르/배우 알려줘"처럼 이미 나온(또는 대화 맥락 속) 특정 영화에 대한
        // 질문이면, 추천 목록을 다시 만드는 대신 그 영화 한 편의 정보로 답합니다. 그중에서도 "상영정보/상영
        // 시간/몇 시에 하는지"처럼 실제 상영 스케줄을 물어본 거라면, 장르/감독/배우 같은 일반 정보가 아니라
        // screenings 테이블의 실제 이번 주 상영 스케줄로 답해야 합니다.
        if ("movie_question".equals(filter.intent()) && filter.query() != null) {
            if (SCREENING_QUESTION_HINT.matcher(request.message()).find()) {
                return answerScreeningQuestion(user, conversation, request.message(), filter.query());
            }
            // "줄거리/스토리 알려줘"처럼 줄거리 자체를 물어본 거라면, 장르/감독/배우를 섞어 주는
            // describeMovieDetail 대신 KMDB plot 값으로 바로 답합니다.
            if (PLOT_QUESTION_HINT.matcher(request.message()).find()) {
                return answerPlotQuestion(user, conversation, request.message(), filter.query());
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
        // "상영중인 영화들 중에서 좀비 영화를 알려줘"처럼 상영중 제한과 장르/키워드 조건이 함께 온 복합
        // 요청이면, 후보 풀 자체를 "지금 상영중인" 영화로 제한해서 찾습니다(찾은 뒤 거르면 상위 몇 개만
        // 보다가 모두 상영 종료작이라 놓치는 문제가 있어, findCandidates 안에서 처리합니다).
        boolean restrictToNowShowing = Boolean.TRUE.equals(filter.restrictToNowShowing());
        java.util.Set<String> nowShowingIds = restrictToNowShowing
                ? movieService.getOrCreateTodayShowingMovies().stream().map(MovieSummaryDto::id).collect(Collectors.toSet())
                : null;
        List<MovieSummaryDto> candidates = findCandidates(effectiveFilter, alreadyRecommendedIds, nowShowingIds);
        String reply = candidates.isEmpty()
                ? (restrictToNowShowing
                        ? "현재 상영중인 영화 중에 말씀하신 조건에 맞는 영화는 없습니다."
                        : "말씀하신 조건에 맞는 국내 영화를 찾지 못했어요. 장르나 연도 조건을 조금 완화해서 다시 물어봐주시겠어요?")
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
        return withCore(
                filter, filter.intent(), resolvedGenre, filter.year(), filter.runtimeMaxMinutes(),
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

    // "레이디 두아 찜해줘"처럼 사용자 메시지 안에 추천 목록 중 한 편(또는 "여름 너머, 취준생 찜해줘"처럼
    // 여러 편)의 제목이 그대로 들어있으면 그 영화들을 콕 집은 것으로 봅니다(공백 차이는 titleMatches와
    // 같은 이유로 무시). 언급된 제목을 전부 찾아 반환합니다(2026-10-08, 예전엔 findFirst()로 첫 번째
    // 일치만 반환해서 두 편을 동시에 찜해달라고 해도 한 편만 찜되는 버그가 있었습니다).
    private List<MovieSummaryDto> findMentionedMovies(String userMessage, List<MovieSummaryDto> movies) {
        String normalizedMessage = userMessage.replaceAll("\\s+", "");
        return movies.stream()
                .filter(movie -> movie.title() != null && titleMentioned(normalizedMessage, movie.title()))
                .toList();
    }

    // 제목이 "범죄도시4"처럼 공통 접두어+숫자로 끝나는 속편일 때, "범죄도시 2,3,4"처럼 접두어 뒤에 번호를
    // 쉼표로 묶어 여러 편을 한 번에 가리키는 표현까지 알아봅니다. 제목이 그대로 안 들어있으면("범죄도시4"가
    // 문자 그대로 없으면) 접두어("범죄도시")+숫자 목록 패턴을 찾아, 그 목록에 이 제목의 번호가 있는지 봅니다
    // (2026-10-08, 예전엔 "범죄도시 2,3,4를 찜 해제해줘"라고 해도 "범죄도시2"만 지워지고 3/4는 그대로
    // 남는 버그가 있었습니다 - "범죄도시2,3,4"라는 문자열 안에 "범죄도시3"/"범죄도시4"가 그대로 들어있지
    // 않기 때문입니다).
    private static final java.util.regex.Pattern TITLE_TRAILING_NUMBER = java.util.regex.Pattern.compile("(.+?)(\\d+)$");

    private boolean titleMentioned(String normalizedMessage, String title) {
        String normalizedTitle = title.replaceAll("\\s+", "");
        if (normalizedMessage.contains(normalizedTitle)) {
            return true;
        }
        java.util.regex.Matcher suffixMatcher = TITLE_TRAILING_NUMBER.matcher(normalizedTitle);
        if (!suffixMatcher.matches()) {
            return false;
        }
        String prefix = suffixMatcher.group(1);
        String number = suffixMatcher.group(2);
        java.util.regex.Matcher listMatcher = java.util.regex.Pattern
                .compile(java.util.regex.Pattern.quote(prefix) + "(\\d+(?:,\\d+)*)")
                .matcher(normalizedMessage);
        while (listMatcher.find()) {
            if (Arrays.asList(listMatcher.group(1).split(",")).contains(number)) {
                return true;
            }
        }
        return false;
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

    private static final java.util.regex.Pattern PLOT_QUESTION_HINT = java.util.regex.Pattern.compile(
            "줄거리|스토리|시놉시스|무슨\\s*내용|내용이\\s*뭐");

    // 줄거리 자체를 물어본 질문 전용입니다 - 스포일러 없는 요약(영화 상세 페이지의 "AI 줄거리 요약" 버튼)과는
    // 다르게, KMDB가 내려주는 plot 값을 가공 없이 그대로 보여줍니다. plot이 비어있는 영화(제작 정보만 있고
    // 줄거리는 등록 안 된 경우)는 빈 메시지 대신 안내 문구로 답합니다.
    private ChatResponseDto answerPlotQuestion(User user, ChatConversation conversation, String userMessage, String movieTitle) {
        List<MovieSummaryDto> matches = movieService.search(movieTitle, List.of(), null, null, "latest", 1, 1, "title").movies();
        if (matches.isEmpty()) {
            String reply = "'%s' 영화를 찾지 못했어요. 정확한 제목으로 다시 물어봐주시겠어요?".formatted(movieTitle);
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        MovieSummaryDto movie = matches.get(0);
        String[] idParts = movie.id().split("_", 2);
        MovieDetailDto detail = movieService.getDetail(idParts[0], idParts[1]);
        String plot = detail.plot();
        String reply = plot == null || plot.isBlank()
                ? "해당 영화는 줄거리가 정보가 없습니다."
                : "'%s' 줄거리예요.\n%s".formatted(detail.title(), plot);

        return persistTurn(user, conversation, userMessage, reply, List.of(movie));
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
            String query, String queryField, String referenceTitle, Integer count,
            // ---- 아래부터는 찜/리뷰/시청완료/선호장르/예매/좌석/상영중 의도를 위한 슬롯입니다 ----
            // 사용자가 자유 텍스트로 콕 집어 말한 영화 제목(들). 지시어("이 영화")뿐이거나 특정 제목이
            // 없으면 빈 리스트(또는 null) - 그 경우 서버가 직전 추천/조회 목록 전체를 대상으로 봅니다.
            List<String> movieTitles,
            // "모든/전부/싹 다" 류로 특정 제목 없이 전체를 가리켰는지(찜/시청완료 전체 해제용).
            Boolean targetAll,
            // "이 리뷰"/"방금 쓴 것"/"마지막 것"처럼 특정 제목 없이 가장 최근 항목을 가리켰는지
            // (리뷰 삭제/시청완료 삭제용 - 목록이 최신순이라 0번째가 "가장 최근"입니다).
            Boolean targetMostRecent,
            // 리뷰 작성 요청에서 사용자가 적은 리뷰 본문 자유 텍스트(평점 언급은 서버가 별도 정규식으로
            // 더 엄격하게 검증하므로 여기엔 포함하지 않음).
            String reviewContent,
            // 선호 장르를 이걸로 바꿔달라고 말한 장르 목록(KNOWN_GENRES 중에서만).
            List<String> genresToSet,
            // "상영중인 영화들 중에서 좀비 영화 알려줘"처럼 recommend 등 다른 의도와 같이 올 수 있는
            // 독립 플래그입니다 - "상영중" 제한이 있었는지.
            Boolean restrictToNowShowing
    ) {
    }

    // "아무 조건도 못 뽑았을 때" 돌려주는 빈 필터입니다(파싱 실패 시 등).
    private static ExtractedFilter emptyFilter() {
        return new ExtractedFilter(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    // resolveReferenceGenre/applyXxxFallback처럼 기존 8개 필드(recommend/movie_question용)만 바꾸고
    // 나머지(찜/리뷰/시청완료/장르/상영중 슬롯)는 원래 filter 값 그대로 보존합니다.
    private ExtractedFilter withCore(
            ExtractedFilter base, String intent, String genre, String year, Integer runtimeMaxMinutes,
            String query, String queryField, String referenceTitle, Integer count
    ) {
        return new ExtractedFilter(
                intent, genre, year, runtimeMaxMinutes, query, queryField, referenceTitle, count,
                base.movieTitles(), base.targetAll(), base.targetMostRecent(), base.reviewContent(),
                base.genresToSet(), base.restrictToNowShowing());
    }

    private ExtractedFilter extractFilter(List<GeminiClient.ChatTurn> history, String message) {
        String systemInstruction = """
                너는 한국 영화 추천 서비스 AI 채팅의 의도 분류기 겸 조건 추출기다. 사용자의 마지막 메시지를,
                바로 위 대화 맥락과 함께 다시 판단해서 의도와 조건을 JSON으로 뽑아라(이전 메시지의 조건을
                그대로 재사용하지 말고 이번 메시지 기준으로 매번 새로 판단해라). 오늘 날짜는 %s이다.

                ## intent (아래 값 중 정확히 하나)

                - "recommend": "추천해줘"가 들어간 요청 전부, 그리고 "이 영화와 같은 장르로", "비슷한 걸로
                  더" 처럼 이전 영화를 기준으로만 새 후보를 찾아달라는 요청. "상영중인 영화들 중에서"처럼
                  현재 상영중인 영화로 제한하는 recommend도 포함(이땐 restrictToNowShowing만 true로 추가).
                - "movie_question": "이 영화 장르가 뭐야", "감독/줄거리/러닝타임/상영정보 알려줘"처럼 영화
                  제목 "한 편 자체"의 정보를 묻는 질문("추천"이 없고 특정 제목 하나만 물어보면 이쪽).
                - "off_topic": 날씨/시간/잡담처럼 영화 추천·정보와 전혀 관련 없는 메시지.
                - "wishlist_add": 영화를 찜 목록에 추가해달라는 요청("찜해줘", "찜 좀", "찜할래").
                  "찜 안 하고 싶어"/"찜 말고"처럼 순수 부정 표현이면 wishlist_add가 아니라 off_topic이나
                  recommend 등 실제 의도로 분류해라.
                - "wishlist_remove": 찜 목록에서 빼달라는 요청("찜 해제해줘", "찜 취소", "찜 목록에서 빼줘").
                - "wishlist_list": 찜한 영화 목록을 보여달라는 조회 요청("내가 찜한 영화 알려줘", "찜 목록 보여줘").
                - "review_add": 영화 리뷰(평점+내용)를 작성/추가해달라는 요청("리뷰 써줘", "리뷰 남겨줘",
                  "리뷰를 추가해줘").
                - "review_list": 작성한 리뷰 목록을 조회해달라는 요청("내가 쓴 리뷰 알려줘").
                - "review_delete": 작성한 리뷰를 삭제해달라는 요청("이 리뷰 삭제해줘", "OOO 리뷰 지워줘").
                - "watched_add": 영화를 시청완료 목록에 추가해달라는 요청.
                - "watched_remove": 시청완료 목록에서 빼달라는 요청.
                - "watched_list": 시청완료 목록을 조회해달라는 요청.
                - "genre_pref_view": 저장된 선호 장르를 조회해달라는 요청.
                - "genre_pref_update": 선호 장르를 바꿔달라는 요청.
                - "booking_list": 본인이 예매한 내역을 조회해달라는 요청("내가 예매한 영화들을 알려줘").
                - "booking_create": 상영 회차에 좌석을 지정해 실제 예매를 요청("성인 2명 D열 2,3번 예매해줘").
                - "seat_status": 특정 회차의 좌석 현황(빈 자리)을 물어보는 요청.

                ## 기존 recommend/movie_question 필드 (의미는 전과 동일)

                - referenceTitle: "이 영화와 같은 장르로", "그거랑 비슷하게"처럼 이전에 나온 특정 영화를
                  기준으로 삼으라는 요청이면 그 영화의 실제 제목(대명사를 이전 메시지에서 찾아 채워라).
                  직전 답변이 장르 조건으로 여러 편을 추천한 목록이었을 때도("같은 장르의 영화들을
                  추천해줘") 그 목록의 첫 번째 영화 제목을 채운다. 기준으로 삼을 영화가 없으면 null.
                  genre 값 자체를 여기서 추측하지 말고 제목만 채워라.
                - genre: recommend이고 사용자가 장르명을 "직접" 말했을 때만 채운다(목록 중 하나).
                  referenceTitle로 대신 처리하는 경우엔 null.
                - year: 4자리 개봉연도 문자열. "최근"/"요즘"처럼 특정 연도가 아니면 null. 이전 메시지
                  조건은 재사용하지 말고 이번 메시지 기준으로만 판단.
                - runtimeMaxMinutes: 상영시간 상한(분). 언급 없으면 null.
                - query: movie_question이면 그 영화 제목. recommend면 특정 제목/배우/감독 이름 또는 장르
                  목록에 없는 소재·테마(좀비, 시간여행, 스포츠 등)를 "새로" 콕 집었을 때만 채운다.
                - queryField: movie_question이면 항상 "title". recommend면 title/actor/director/keyword.
                - count: "2개", "3편"처럼 추천 개수를 직접 숫자로 말했으면 그 정수, 아니면 null.

                아주 중요: 이번 메시지에 "배우 OOO", "OOO 감독", 특정 영화 제목처럼 새로운 이름/제목이
                명확히 나오면, 이전 대화 주제와 상관없어 보여도 반드시 그 이름을 query에 채워라.

                ## 찜/리뷰/시청완료/선호장르/상영중 공통 슬롯

                - movieTitles: 사용자가 자유 텍스트로 콕 집어 말한 영화 제목들(배열). "범죄도시 2,3,4를
                  찜해줘"처럼 접두어+번호 나열이면 ["범죄도시2","범죄도시3","범죄도시4"]처럼 개별 제목으로
                  풀어서 채워라(원문 띄어쓰기와 무관하게 "범죄도시"처럼 붙여 써라). "이 영화"/"그 영화"처럼
                  지시어뿐이거나 특정 제목이 없으면 빈 배열([]).
                - targetAll: "모든/전부/싹 다" 류로 특정 제목 없이 전체를 가리켰으면 true(찜 전체 해제,
                  시청완료 전체 삭제에서만 의미 있음). 특정 제목을 말했으면 false.
                - targetMostRecent: "이 리뷰"/"방금 쓴 것"/"마지막 것"/"최근 본 영화"처럼 특정 제목 없이
                  가장 최근 항목을 가리켰으면 true(리뷰 삭제, 시청완료 삭제에서만 의미 있음).
                - reviewContent: review_add일 때 사용자가 적은 리뷰 본문 자유 텍스트(평점 숫자/"평점 N점"
                  표현은 제외하고 실제 감상평 문장만). 리뷰 본문이 없으면 null.
                - genresToSet: genre_pref_update일 때 사용자가 선호 장르로 바꿔달라고 말한 장르들(배열,
                  genre와 같은 장르 목록 중에서만). 언급 없으면 빈 배열([]).
                - restrictToNowShowing: "상영중인 영화들 중에서"처럼 지금 상영중인 영화로만 제한해달라는
                  뜻이 있으면 true, 아니면 false. recommend 등 다른 intent와 함께 올 수 있다.

                ## 예시

                (직전 답변이 '군체'라는 영화에 대한 것이었던 상황)
                - "이 영화와 같은 장르로 추천해줘" -> {"intent":"recommend","referenceTitle":"군체","genre":null,"query":null,"year":null,"count":null}
                - "감독이 누구야" -> {"intent":"movie_question","query":"군체","queryField":"title"}
                - "오늘 날씨 어때?" -> {"intent":"off_topic"}
                - "현재 상영중인 영화들 중에서 좀비나 야구 관련 영화를 알려줘" ->
                  {"intent":"recommend","query":"좀비","queryField":"keyword","restrictToNowShowing":true}
                - "지금 상영중인 영화 찾아줘" -> {"intent":"recommend","restrictToNowShowing":true}
                - "범죄 도시 2,3,4를 찜 해제해줘" ->
                  {"intent":"wishlist_remove","movieTitles":["범죄도시2","범죄도시3","범죄도시4"],"targetAll":false}
                - "내가 찜한 모든 영화들을 찜 해제해줘" -> {"intent":"wishlist_remove","movieTitles":[],"targetAll":true}
                - "내가 찜한 모든 영화들을 알려줘" -> {"intent":"wishlist_list"}
                - "이 영화 찜해줘" -> {"intent":"wishlist_add","movieTitles":[]}
                - "찜 안 하고 싶어" -> {"intent":"off_topic"}
                - "영화 퍼펙트 게임의 리뷰를 다음과 같이 추가해줘. 평점-5 리뷰 내용- 롯데의 최동원vs해태의 선동열" ->
                  {"intent":"review_add","movieTitles":["퍼펙트 게임"],"reviewContent":"롯데의 최동원vs해태의 선동열"}
                - "내가 쓴 모든 리뷰를 알려줘" -> {"intent":"review_list"}
                - "방금 쓴 리뷰 삭제해줘" -> {"intent":"review_delete","movieTitles":[],"targetMostRecent":true}
                - "영화 OOO을 시청 완료 목록에 추가해줘" -> {"intent":"watched_add","movieTitles":["OOO"]}
                - "내 모든 시청 완료 목록을 삭제해줘" -> {"intent":"watched_remove","movieTitles":[],"targetAll":true}
                - "내 선호 장르 알려줘" -> {"intent":"genre_pref_view"}
                - "선호 장르를 액션, 공포로 바꿔줘" -> {"intent":"genre_pref_update","genresToSet":["액션","공포"]}
                - "내가 예매한 영화들을 알려줘" -> {"intent":"booking_list"}
                - "성인 2명 D열 2,3번을 예매해줘" -> {"intent":"booking_create"}
                - "10/11 2관 20:05 좌석 현황 알려줘" -> {"intent":"seat_status"}

                위 예시에 없는 필드는 전부 null(또는 배열이면 빈 배열)로 채워라.
                """.formatted(LocalDate.now());

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "OBJECT");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode intentProp = properties.putObject("intent");
        intentProp.put("type", "STRING");
        ArrayNode intentEnum = intentProp.putArray("enum");
        List.of(
                "recommend", "movie_question", "off_topic",
                "wishlist_add", "wishlist_remove", "wishlist_list",
                "review_add", "review_list", "review_delete",
                "watched_add", "watched_remove", "watched_list",
                "genre_pref_view", "genre_pref_update",
                "booking_list", "booking_create", "seat_status"
        ).forEach(intentEnum::add);

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

        ObjectNode movieTitlesProp = properties.putObject("movieTitles");
        movieTitlesProp.put("type", "ARRAY");
        movieTitlesProp.put("nullable", true);
        movieTitlesProp.putObject("items").put("type", "STRING");

        ObjectNode targetAllProp = properties.putObject("targetAll");
        targetAllProp.put("type", "BOOLEAN");
        targetAllProp.put("nullable", true);

        ObjectNode targetMostRecentProp = properties.putObject("targetMostRecent");
        targetMostRecentProp.put("type", "BOOLEAN");
        targetMostRecentProp.put("nullable", true);

        ObjectNode reviewContentProp = properties.putObject("reviewContent");
        reviewContentProp.put("type", "STRING");
        reviewContentProp.put("nullable", true);

        ObjectNode genresToSetProp = properties.putObject("genresToSet");
        genresToSetProp.put("type", "ARRAY");
        genresToSetProp.put("nullable", true);
        ObjectNode genresToSetItems = genresToSetProp.putObject("items");
        genresToSetItems.put("type", "STRING");
        ArrayNode genresToSetEnum = genresToSetItems.putArray("enum");
        KNOWN_GENRES.forEach(genresToSetEnum::add);

        ObjectNode restrictToNowShowingProp = properties.putObject("restrictToNowShowing");
        restrictToNowShowingProp.put("type", "BOOLEAN");
        restrictToNowShowingProp.put("nullable", true);

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
                    node.hasNonNull("count") ? node.get("count").asInt() : null,
                    textArrayOrNull(node.get("movieTitles")),
                    boolOrNull(node.get("targetAll")),
                    boolOrNull(node.get("targetMostRecent")),
                    textOrNull(node.get("reviewContent")),
                    textArrayOrNull(node.get("genresToSet")),
                    boolOrNull(node.get("restrictToNowShowing")));
        } catch (Exception e) {
            log.warn("Gemini 조건 추출 응답을 해석하지 못했습니다. json={}", json, e);
            return emptyFilter();
        }
    }

    private String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    private List<String> textArrayOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray() || node.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String text = item.asText(null);
            if (text != null && !text.isBlank()) {
                values.add(text);
            }
        });
        return values.isEmpty() ? null : values;
    }

    private Boolean boolOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asBoolean();
    }

    // ---- 2) KMDB에서 실제 후보 찾기 ----

    private List<MovieSummaryDto> findCandidates(ExtractedFilter filter, java.util.Set<String> excludeMovieIds) {
        return findCandidates(filter, excludeMovieIds, null);
    }

    // restrictToIds가 있으면("상영중인 영화들 중에서..."처럼 상영중 제한이 함께 온 경우) 후보 풀 자체를
    // 넉넉히 가져온 뒤 그 안에서만 거르고, 그래야 excludeMovieIds/limit로 더 추려도 실제로 상영중인 영화
    // 중 조건에 맞는 게 있으면 제대로 찾아냅니다(바로 limit만큼만 가져온 뒤 거르면 하필 그 몇 개가 전부
    // 상영 종료작이라 멀쩡히 맞는 영화가 있어도 못 찾는 문제가 있었습니다).
    private List<MovieSummaryDto> findCandidates(
            ExtractedFilter filter, java.util.Set<String> excludeMovieIds, java.util.Set<String> restrictToIds
    ) {
        List<String> genres = filter.genre() == null ? List.of() : List.of(filter.genre());
        // 사용자가 "2개", "3편"처럼 개수를 직접 말했으면 그 개수를(최대 MAX_RECOMMENDATIONS까지), 아니면
        // 기본 개수를 씁니다.
        int limit = resolveLimit(filter.count());
        int searchPageSize = restrictToIds != null ? NOW_SHOWING_SEARCH_POOL : limit * 3;

        List<MovieSummaryDto> movies;
        if (filter.query() != null && filter.queryField() != null) {
            // Gemini가 "OOO 감독"처럼 대상이 뭔지 알려준 경우 - 엉뚱하게 title 검색이 먼저 걸려버리는 걸
            // 막기 위해 굳이 다른 필드로 재시도하지 않고 지정된 필드만 그대로 씁니다.
            movies = searchNonEmpty(filter.query(), genres, filter.year(), filter.queryField(), searchPageSize);
        } else if (filter.query() != null) {
            // 필드를 특정 못 했으면 title -> actor -> director 순으로 시도합니다.
            movies = searchNonEmpty(filter.query(), genres, filter.year(), "title", searchPageSize);
            if (movies.isEmpty()) {
                movies = searchNonEmpty(filter.query(), genres, filter.year(), "actor", searchPageSize);
            }
            if (movies.isEmpty()) {
                movies = searchNonEmpty(filter.query(), genres, filter.year(), "director", searchPageSize);
            }
        } else {
            movies = movieService.search("", genres, filter.year(), null, "latest", 1, searchPageSize, "title").movies();
        }

        return movies.stream()
                .filter(movie -> matchesRuntime(movie, filter.runtimeMaxMinutes()))
                .filter(movie -> !excludeMovieIds.contains(movie.id()))
                .filter(movie -> restrictToIds == null || restrictToIds.contains(movie.id()))
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

    private List<MovieSummaryDto> searchNonEmpty(String query, List<String> genres, String year, String field, int pageSize) {
        return movieService.search(query, genres, year, null, "latest", 1, pageSize, field).movies();
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
        // "지금 상영중인 영화 찾아줘"처럼 다른 조건 없이 상영중 제한만 있었으면, 예전 handleNowShowingRequest
        // 전용 분기가 쓰던 문구를 그대로 유지합니다(장르/키워드 등 다른 조건이 같이 있으면 그 조건을
        // 설명하는 일반 문구가 이미 "상영중"의 맥락을 담고 있어 따로 구분하지 않습니다).
        if (condition.isBlank() && Boolean.TRUE.equals(filter.restrictToNowShowing())) {
            return "지금 상영중인 영화로 %s %s 마음에 드는 작품을 골라보세요 🍿\n%s".formatted(titles, suffix, guide);
        }
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
            return withCore(filter, filter.intent(), null, null, null, name, "actor", null, filter.count());
        }

        java.util.regex.Matcher directorMatcher = DIRECTOR_HINT.matcher(message);
        if (directorMatcher.find()) {
            String name = stripTrailingParticle(
                    directorMatcher.group(1) != null ? directorMatcher.group(1) : directorMatcher.group(2));
            return withCore(filter, filter.intent(), null, null, null, name, "director", null, filter.count());
        }

        java.util.regex.Matcher keywordMatcher = KEYWORD_HINT.matcher(message);
        if (keywordMatcher.find()) {
            String keyword = stripTrailingParticle(
                    keywordMatcher.group(1) != null ? keywordMatcher.group(1) : keywordMatcher.group(2));
            return withCore(filter, filter.intent(), null, null, null, keyword, "keyword", null, filter.count());
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
        return withCore(filter, "movie_question", null, null, null, title, "title", null, filter.count());
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
        return withCore(
                filter, filter.intent(), filter.genre(), filter.year(), filter.runtimeMaxMinutes(),
                filter.query(), filter.queryField(), filter.referenceTitle(), count);
    }

    // "제목으로 콕 집어 말한 영화"를 찾습니다(찜추가/시청완료추가/리뷰대상이 공통으로 씀). 1) 직전
    // 추천/조회 목록 중에 메시지에 언급된 제목이 있으면 그걸 그대로 쓰고(가장 흔한 경우), 2) 없으면
    // Gemini가 뽑아준 movieTitles로 실제 카탈로그를 다시 검색합니다("범죄도시3 찜해줘"처럼 지금 막
    // 추천/조회된 영화가 아니라 다른 영화를 콕 집어 말한 경우). 둘 다 못 찾으면 빈 리스트를 반환하고,
    // 그 경우 "지시어만 쓴 경우 전체 후보로 볼지"는 호출부가 각자의 정책대로 결정합니다(찜/시청완료=전체,
    // 리뷰=모호하면 재질문 - 기능마다 정책이 달라 여기서 강제하지 않습니다).
    private List<MovieSummaryDto> resolveNewMovieReferences(
            List<String> movieTitles, String userMessage, List<ChatMessage> fullHistory
    ) {
        List<MovieSummaryDto> candidates = lastRecommendedMovies(fullHistory);
        List<MovieSummaryDto> mentioned = findMentionedMovies(userMessage, candidates);
        if (!mentioned.isEmpty()) {
            return mentioned;
        }
        if (movieTitles == null || movieTitles.isEmpty()) {
            return List.of();
        }
        return movieTitles.stream()
                .map(title -> recoverOriginalSpelling(title, userMessage))
                .flatMap(title -> splitTitleCandidates(title).stream())
                .map(this::searchSingleMovieByTitle)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // Gemini가 돌려준 제목은 가끔 사용자가 실제로 입력한 철자/띄어쓰기를 미묘하게 다시 써버립니다
    // ("7데이즈 7피플" -> "7 데이즈 7 피플"). 그 철자 그대로 KMDB를 검색하면, 검색어와 일치한 구간을
    // !HS/!HE로 표시하는 KMDB가 검색어의 잘못된 띄어쓰기를 그대로 캐시에 반영해버리는 문제가 있습니다
    // (2026-10-08, splitTitleCandidates의 공백 제거로 고쳤던 "범죄 도시2" 버그와 같은 종류가 Gemini
    // 기반 제목 추출에서도 재발). 그래서 Gemini 제목은 "어떤 영화를 찾을지"에 대한 힌트로만 쓰고, 실제
    // 검색어는 사용자 원문에서 같은 글자(공백 무시)가 나오는 구간을 그대로 오려써서 사용자가 입력한
    // 철자를 그대로 유지합니다. 원문에서 못 찾으면(예: Gemini가 완전히 다른 표현으로 요약한 경우)
    // 어쩔 수 없이 Gemini 제목을 그대로 씁니다.
    private String recoverOriginalSpelling(String geminiTitle, String userMessage) {
        String collapsed = geminiTitle.replaceAll("\\s+", "");
        if (collapsed.isBlank()) {
            return geminiTitle;
        }
        StringBuilder pattern = new StringBuilder();
        collapsed.codePoints().forEach(cp -> pattern
                .append(java.util.regex.Pattern.quote(new String(Character.toChars(cp))))
                .append("\\s*"));
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern.toString()).matcher(userMessage);
        return matcher.find() ? matcher.group().trim() : geminiTitle;
    }

    // remove/delete류(찜해제/시청완료삭제/리뷰삭제)가 공통으로 쓰는 "내 기존 목록에서 찾기"입니다. 원문
    // 메시지를 그대로 정규화해 실제 보유 목록(wishlist/watched/reviews - 이미 범위가 좁은 목록이라 KMDB
    // 재검색 없이 substring 대조만으로 충분합니다)과 대조하고, 특정 제목을 못 찾았을 때만 targetAll(전체)
    // 또는 targetMostRecent(가장 최근 항목 - 목록이 최신순일 때만 의미 있습니다)로 대체합니다.
    private <T> List<T> matchAgainstOwnList(
            String userMessage, boolean targetAll, boolean targetMostRecent,
            List<T> ownList, java.util.function.Function<T, String> titleExtractor
    ) {
        String normalizedMessage = userMessage.replaceAll("\\s+", "");
        List<T> matched = ownList.stream()
                .filter(item -> titleExtractor.apply(item) != null && titleMentioned(normalizedMessage, titleExtractor.apply(item)))
                .toList();
        if (!matched.isEmpty()) {
            return matched;
        }
        if (targetAll) {
            return ownList;
        }
        if (targetMostRecent && !ownList.isEmpty()) {
            return List.of(ownList.get(0));
        }
        return List.of();
    }

    // "이 영화 찜해줘"처럼 직전에 추천받은 영화를 찜 목록에 담아달라는 요청입니다.
    private ChatResponseDto handleWishlistRequest(
            User user, ChatConversation conversation, String userMessage, ExtractedFilter filter, List<ChatMessage> fullHistory
    ) {
        List<MovieSummaryDto> resolved = resolveNewMovieReferences(filter.movieTitles(), userMessage, fullHistory);
        // 특정 제목을 못 찾았으면("이 영화 찜해줘"처럼 지시어만 쓴 경우 등) 직전 추천 목록 전체를 찜합니다.
        List<MovieSummaryDto> movies = !resolved.isEmpty() ? resolved : lastRecommendedMovies(fullHistory);
        if (movies.isEmpty()) {
            String reply = "먼저 추천받은 영화가 있어야 찜할 수 있어요! 어떤 영화를 찾아드릴까요?";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }
        return addToWishlist(user, conversation, userMessage, movies);
    }

    // "범죄 도시 2,3,4"(또는 "4를"처럼 조사가 바로 붙은 경우)처럼 접두어 뒤에 번호를 쉼표로 묶어 여러 속편을
    // 가리키는 표현을, 그냥 쉼표로 쪼개면 "2"/"3"/"4를"이 각자 하나의(엉뚱한) 영화 제목처럼 검색돼버립니다
    // (2026-10-08, "3"이나 "4를"을 그대로 KMDB에 검색해서 전혀 상관없는 영화가 찜된 버그가 있었습니다).
    // 그래서 쉼표로 쪼개기 전에 먼저 "접두어 + 숫자,숫자,..." 통째 패턴인지 확인해서, 맞으면 접두어를
    // 각 번호에 붙여 "범죄도시2", "범죄도시3", "범죄도시4"처럼 실제 검색 가능한 제목으로 풀어줍니다.
    // 이 패턴이 아니면("여름 너머, 취준생"처럼 평범한 나열이면) 기존대로 쉼표로만 나눕니다.
    private static final java.util.regex.Pattern SEQUEL_NUMBER_LIST = java.util.regex.Pattern.compile(
            "^(.+?)\\s*(\\d+(?:\\s*,\\s*\\d+)+)\\s*[을를이가은는]?$");

    private List<String> splitTitleCandidates(String text) {
        java.util.regex.Matcher matcher = SEQUEL_NUMBER_LIST.matcher(text);
        if (matcher.matches()) {
            // 접두어 안의 공백은 지우고 붙입니다("범죄 도시" + "2" -> "범죄도시2") - 사용자가 띄어 쓴 그대로
            // "범죄 도시2"로 검색하면 KMDB가 검색어와 일치한 구간을 !HS/!HE로 표시하면서 응답 title 값에도
            // 그 공백이 그대로 남아버려서, 정상 영화를 찾고도 "범죄 도시2"라는 잘못된 제목으로 캐시가 다시
            // 오염되는 2차 버그가 있었습니다(실제 KMDB 정식 표기는 "범죄도시2"처럼 붙어 있습니다).
            String prefix = matcher.group(1).trim().replaceAll("\\s+", "");
            return Arrays.stream(matcher.group(2).split("\\s*,\\s*"))
                    .map(number -> prefix + number)
                    .toList();
        }
        return Arrays.stream(text.split("[,，]"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    // 제목으로 KMDB를 검색해 영화 하나를 찾습니다. 검색 결과의 title 필드를 그대로 쓰지 않고 id로 다시
    // 조회해서 제목을 가져옵니다 - 검색 쿼리 자체의 철자/띄어쓰기가 Gemini가 다시 쓴 제목처럼 KMDB 정식
    // 표기와 다르면("7데이즈 7피플"을 "7 데이즈 7 피플"로), KMDB가 검색어와 일치한 구간을 !HS/!HE로
    // 표시하면서 검색 응답 title 값에 그 철자가 그대로 남아, 찜/리뷰/시청완료에 잘못된 제목이 저장되는
    // 문제가 있었습니다(2026-10-08, splitTitleCandidates의 공백 제거로 고쳤던 것과 같은 종류의 버그가
    // Gemini 기반 제목 추출에서도 재발). id로 다시 조회하면 검색어와 무관한 영화 자체의 정식 제목을
    // 돌려받습니다.
    private MovieSummaryDto searchSingleMovieByTitle(String title) {
        List<MovieSummaryDto> matches = movieService.search(title, List.of(), null, null, "latest", 1, 1, "title").movies();
        if (matches.isEmpty()) {
            return null;
        }
        MovieSummaryDto matched = matches.get(0);
        MovieSummaryDto canonical = toSummaryOrNull(matched.id());
        return canonical != null ? canonical : matched;
    }

    private ChatResponseDto addToWishlist(
            User user, ChatConversation conversation, String userMessage, List<MovieSummaryDto> movies
    ) {
        for (MovieSummaryDto movie : movies) {
            wishlistService.add(user.getId(), new WishlistRequest(movie.id(), movie.title(), movie.posterUrl()));
        }
        String titles = movies.stream().map(MovieSummaryDto::title).collect(Collectors.joining(", "));
        String reply = "영화 %s %s 찜했습니다."
                .formatted(titles, movies.size() > 1 ? "등 %d편을".formatted(movies.size()) : "을(를)");
        // 찜하기 확인 메시지에는 영화 카드 목록을 다시 붙이지 않습니다 - movies를 그대로 넘기면
        // ChatMovieRecommendation이 방금 봤던 추천 카드 그리드를 통째로 다시 그려서, 화면상 방금 추천
        // 답변과 거의 구분이 안 되는 문제가 있었습니다(사용자가 "안 고쳐졌다"고 재차 신고한 원인).
        return persistTurn(user, conversation, userMessage, reply, List.of());
    }

    // "찜 해제"/"찜 취소"/"찜 빼줘"처럼 이미 찜한 영화를 목록에서 지워달라는 요청입니다. 방금 추천받은
    // 영화가 아니라 예전에 찜해둔 영화를 가리키는 경우가 흔해서, 추천 목록이 아니라 실제 사용자 찜 목록을
    // 기준으로 제목을 대조합니다. "모든/전부" 류로 특정 제목 없이 전체를 가리켰으면(filter.targetAll())
    // 전부 지웁니다.
    private ChatResponseDto handleWishlistRemoveRequest(
            User user, ChatConversation conversation, String userMessage, ExtractedFilter filter
    ) {
        List<WishlistDto> wishlist = wishlistService.list(user.getId());
        if (wishlist.isEmpty()) {
            String reply = "찜한 영화가 없어요.";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        List<WishlistDto> matched = matchAgainstOwnList(
                userMessage, Boolean.TRUE.equals(filter.targetAll()), false, wishlist, WishlistDto::movieTitle);

        if (matched.isEmpty()) {
            String reply = "어떤 영화를 찜 해제할지 제목으로 말씀해주시겠어요? (현재 찜한 영화: %s)"
                    .formatted(wishlist.stream().map(WishlistDto::movieTitle).collect(Collectors.joining(", ")));
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        for (WishlistDto item : matched) {
            wishlistService.remove(user.getId(), item.movieId());
        }
        String titles = matched.stream().map(WishlistDto::movieTitle).collect(Collectors.joining(", "));
        boolean removedAll = Boolean.TRUE.equals(filter.targetAll()) && matched.size() == wishlist.size();
        String reply = removedAll
                ? "찜한 영화 %d편을 전부 찜 해제했습니다.".formatted(matched.size())
                : "영화 %s %s 찜 해제했습니다.".formatted(titles, matched.size() > 1 ? "등 %d편을".formatted(matched.size()) : "을(를)");
        return persistTurn(user, conversation, userMessage, reply, List.of());
    }

    // "현재 나의 모든 찜 목록의 영화를 알려줘"처럼 찜해둔 영화 목록 자체를 보여달라는 요청입니다. 추가/
    // 삭제와 달리 실제 사용자 찜 목록을 그대로 카드로 보여주면 되므로, 영화 상세를 다시 조회해 평소
    // 추천/상영중 응답과 같은 형태(movies 카드 목록)로 답합니다.
    private ChatResponseDto handleWishlistListRequest(User user, ChatConversation conversation, String userMessage) {
        List<WishlistDto> wishlist = wishlistService.list(user.getId());
        if (wishlist.isEmpty()) {
            String reply = "아직 찜한 영화가 없어요.";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        List<MovieSummaryDto> movies = wishlist.stream()
                .map(item -> toSummaryOrNull(item.movieId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        String titles = wishlist.stream().map(WishlistDto::movieTitle).collect(Collectors.joining(", "));
        String reply = "현재 찜한 영화는 %d편이에요: %s".formatted(wishlist.size(), titles);
        return persistTurn(user, conversation, userMessage, reply, movies);
    }

    // "평점 4점", "별점 5", "별 3개", "평점-5"처럼 "평점/별점/별" 뒤에 붙은 숫자를 뽑아냅니다("-"/":" 구분자도
    // 허용). 정수가 아니거나(3.3) 1~5 범위를 벗어나면(0, 6 등) 전부 INVALID로 봅니다 - 평점은 반드시
    // 1,2,3,4,5 중 하나여야 합니다.
    private static final java.util.regex.Pattern REVIEW_SCORE_HINT = java.util.regex.Pattern.compile("(?:평점|별점|별)\\s*[-:]?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:점|개)?");
    // 평점을 먼저 물어본 다음 턴처럼, 사용자가 "평점"이라는 단어 없이 숫자만(또는 "4점"처럼) 달랑 답하는
    // 경우를 위한 관대한 패턴입니다 - 메시지 전체가 숫자(+선택적 "점")뿐일 때만 매치합니다.
    private static final java.util.regex.Pattern REVIEW_BARE_SCORE = java.util.regex.Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*점?\\s*$");
    // "'정말 재밌었다' 써줘"처럼 메시지 안에 따옴표로 묶인 내용이 있으면 그 자체를 리뷰 본문으로 봅니다
    // (pendingReviewState 이어받기 때 AI 자신이 쓴 마커 문구에서 content를 다시 뽑아낼 때 씁니다).
    private static final java.util.regex.Pattern REVIEW_QUOTED_CONTENT = java.util.regex.Pattern.compile("['\"“”']([^'\"“”]{2,})['\"“”']");
    // 아래 두 마커는 각각 "평점을 기다리는 중"/"내용을 기다리는 중" 상태를 다음 턴에 알아보기 위한 문구입니다
    // (resolveReviewDraft의 실제 안내 문구에 그대로 들어있어야 합니다).
    private static final String REVIEW_NEED_SCORE_MARKER = "평점 정보를";
    private static final String REVIEW_NEED_CONTENT_MARKER = "리뷰 내용을 입력해주세요";

    private enum ReviewScoreState { ABSENT, INVALID, VALID }

    private record ReviewScoreResult(ReviewScoreState state, Integer value) {
        private static final ReviewScoreResult ABSENT = new ReviewScoreResult(ReviewScoreState.ABSENT, null);
        private static final ReviewScoreResult INVALID = new ReviewScoreResult(ReviewScoreState.INVALID, null);
    }

    private ReviewScoreResult toReviewScoreResult(String raw) {
        if (raw.contains(".")) {
            return ReviewScoreResult.INVALID;
        }
        int value = Integer.parseInt(raw);
        return value >= 1 && value <= 5 ? new ReviewScoreResult(ReviewScoreState.VALID, value) : ReviewScoreResult.INVALID;
    }

    // "평점/별점/별" 접두어가 붙은 형태만 찾습니다(요청 메시지 안에 다른 말과 섞여 있을 때용).
    private ReviewScoreResult parseReviewScore(String message) {
        java.util.regex.Matcher matcher = REVIEW_SCORE_HINT.matcher(message);
        return matcher.find() ? toReviewScoreResult(matcher.group(1)) : ReviewScoreResult.ABSENT;
    }

    // 접두어 없이 숫자만 달랑 온 답변까지 평점으로 받아줍니다(평점을 먼저 물어본 다음 턴 전용).
    private ReviewScoreResult parseReviewScoreLenient(String message) {
        java.util.regex.Matcher bare = REVIEW_BARE_SCORE.matcher(message.trim());
        if (bare.matches()) {
            return toReviewScoreResult(bare.group(1));
        }
        return parseReviewScore(message);
    }

    private String extractQuotedReviewContent(String message) {
        java.util.regex.Matcher matcher = REVIEW_QUOTED_CONTENT.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    // "이 영화 리뷰 써줘"처럼 리뷰 작성을 요청받았을 때의 진입점입니다. 대상 영화는 지시어("이 영화") 또는
    // Gemini가 뽑아준 movieTitles로 직전 추천/조회 목록 또는 실제 카탈로그에서 찾습니다(resolveNewMovieReferences
    // - 찜하기와 같은 공유 로직). 찜/시청완료와 달리 리뷰는 영화 "한 편"이 대상이라, 특정 제목 없이 후보가
    // 여러 편이면(어느 영화인지 모호하면) 추측하지 않고 되묻습니다. 리뷰 본문/평점이 이미 있으면 그만큼
    // 반영하고, 부족한 부분은 resolveReviewDraft가 되물어서 다음 메시지를 기다립니다(아래 PendingReviewState
    // 분기가 그 다음 메시지를 받습니다).
    private ChatResponseDto handleReviewRequest(
            User user, ChatConversation conversation, String userMessage, ExtractedFilter filter, List<ChatMessage> fullHistory
    ) {
        List<MovieSummaryDto> candidates = lastRecommendedMovies(fullHistory);
        List<MovieSummaryDto> resolved = resolveNewMovieReferences(filter.movieTitles(), userMessage, fullHistory);
        MovieSummaryDto movie = !resolved.isEmpty() ? resolved.get(0) : (candidates.size() == 1 ? candidates.get(0) : null);

        if (movie == null) {
            String reply = candidates.isEmpty()
                    ? "어떤 영화의 리뷰를 작성할지 먼저 영화를 찾아주시겠어요?"
                    : "어떤 영화의 리뷰를 작성할지 제목으로 말씀해주시겠어요?";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        // 평점은 Gemini가 아니라 지금처럼 엄격한 정규식으로 검증합니다 - Gemini는 "3.3"을 3으로 반올림해
        // 버릴 수 있어, "1~5 정수가 아니면 무조건 다시 묻는다"는 요구사항을 신뢰할 수 없게 만듭니다.
        ReviewScoreResult score = parseReviewScore(userMessage);
        return resolveReviewDraft(user, conversation, userMessage, movie, filter.reviewContent(), score);
    }

    // 리뷰 내용/평점이 모일 때까지의 모든 상태 전환을 한 곳에서 처리합니다.
    // - 평점이 입력됐지만 1~5 사이 정수가 아니면(3.3, 0, 7 등) 내용 유무와 상관없이 무조건 다시 묻습니다
    //   (요청사항: "평점 정보는 1,2,3,4,5점 중에서만" - 이 경우는 절대 저장하지 않습니다).
    // - 내용과 평점이 둘 다 있어야만 실제로 저장합니다. 하나라도 없으면 그 자리에서 저장하지 않고 남은
    //   쪽만 콕 집어 되묻습니다(이미 받은 값은 안내 문구에 그대로 남겨서 다음 턴에 이어받습니다).
    private ChatResponseDto resolveReviewDraft(
            User user, ChatConversation conversation, String userMessage, MovieSummaryDto movie,
            String content, ReviewScoreResult score
    ) {
        boolean hasContent = content != null && !content.isBlank();

        if (score.state() == ReviewScoreState.INVALID) {
            String reply = hasContent
                    ? "평점 정보를 1, 2, 3, 4, 5점 중에서 다시 입력해주세요. (리뷰 내용: \"%s\")".formatted(content)
                    : "평점 정보를 1, 2, 3, 4, 5점 중에서 다시 입력해주세요.";
            return persistTurn(user, conversation, userMessage, reply, List.of(movie));
        }

        boolean hasScore = score.state() == ReviewScoreState.VALID;
        if (hasContent && hasScore) {
            return saveReview(user, conversation, userMessage, movie, content, score.value());
        }
        if (hasContent) {
            String reply = "평점 정보를 입력해주세요. (리뷰 내용: \"%s\")".formatted(content);
            return persistTurn(user, conversation, userMessage, reply, List.of(movie));
        }
        if (hasScore) {
            String reply = "리뷰 내용을 입력해주세요. (평점 %d점)".formatted(score.value());
            return persistTurn(user, conversation, userMessage, reply, List.of(movie));
        }
        String reply = "'%s' 리뷰로 남길 내용과 평점(1, 2, 3, 4, 5점 중 하나)을 알려주세요.".formatted(movie.title());
        return persistTurn(user, conversation, userMessage, reply, List.of(movie));
    }

    private enum PendingReviewState { NONE, AWAITING_SCORE, AWAITING_CONTENT }

    // 직전 AI 메시지가 평점/내용 중 뭘 기다리는 상태인지 확인합니다. 바로 다음 메시지 하나에만 적용되는
    // 질문이라, 맥락을 계속 거슬러 올라가는 다른 분기들과 달리 fullHistory의 맨 마지막 메시지만 봅니다.
    private PendingReviewState pendingReviewState(List<ChatMessage> fullHistory) {
        if (fullHistory.isEmpty()) {
            return PendingReviewState.NONE;
        }
        ChatMessage last = fullHistory.get(fullHistory.size() - 1);
        if (!"ai".equals(last.getRole()) || last.getContent() == null) {
            return PendingReviewState.NONE;
        }
        if (last.getContent().contains(REVIEW_NEED_SCORE_MARKER)) {
            return PendingReviewState.AWAITING_SCORE;
        }
        if (last.getContent().contains(REVIEW_NEED_CONTENT_MARKER)) {
            return PendingReviewState.AWAITING_CONTENT;
        }
        return PendingReviewState.NONE;
    }

    // pendingReviewState가 NONE이 아닐 때 이어받는 분기입니다. 이미 받아둔 값(내용 또는 평점)은
    // resolveReviewDraft가 직전 AI 안내 문구에 그대로 적어뒀으므로, 그 문구에서 다시 뽑아 이어갑니다.
    private ChatResponseDto handlePendingReviewReply(
            User user, ChatConversation conversation, String userMessage, MovieSummaryDto movie,
            PendingReviewState state, String lastAiMessage
    ) {
        if (state == PendingReviewState.AWAITING_SCORE) {
            String carriedContent = extractQuotedReviewContent(lastAiMessage);
            ReviewScoreResult score = parseReviewScoreLenient(userMessage);
            return resolveReviewDraft(user, conversation, userMessage, movie, carriedContent, score);
        }

        ReviewScoreResult carriedScore = parseReviewScore(lastAiMessage);
        // 평점 표현이 섞여 들어와도 지우고 받습니다(예: "평점 4점, 생각보다 괜찮았어요").
        String content = REVIEW_SCORE_HINT.matcher(userMessage).replaceAll("").trim().replaceAll("^[,.\\s]+", "");
        return resolveReviewDraft(user, conversation, userMessage, movie, content.isBlank() ? null : content, carriedScore);
    }

    private ChatResponseDto saveReview(
            User user, ChatConversation conversation, String userMessage, MovieSummaryDto movie, String content, int score
    ) {
        reviewService.create(movie.id(), user.getId(), new ReviewRequest(movie.title(), score, content));
        String reply = "'%s' 리뷰가 저장됐어요! (평점 %d점)\n\"%s\"".formatted(movie.title(), score, content);
        return persistTurn(user, conversation, userMessage, reply, List.of(movie));
    }

    private ChatResponseDto handleReviewListRequest(User user, ChatConversation conversation, String userMessage) {
        List<ReviewDto> reviews = reviewService.listByUser(user.getId());
        if (reviews.isEmpty()) {
            String reply = "아직 작성한 리뷰가 없어요.";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        String summary = reviews.stream()
                .map(review -> "- %s (평점 %d점): %s".formatted(review.movieTitle(), review.score(), review.content()))
                .collect(Collectors.joining("\n"));
        String reply = "작성하신 리뷰는 총 %d개예요.\n%s".formatted(reviews.size(), summary);
        List<MovieSummaryDto> movies = reviews.stream()
                .map(review -> toSummaryOrNull(review.movieId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        return persistTurn(user, conversation, userMessage, reply, movies);
    }

    // reviewService.listByUser는 최신순(findByUserIdOrderByCreatedAtDesc)이라, 특정 제목 언급 없이
    // "이 리뷰"/"방금 쓴 리뷰"처럼 최근 것을 가리키면(filter.targetMostRecent()) 0번째(가장 최근) 리뷰를
    // 가리키는 걸로 봅니다. 리뷰는 "전체 삭제"가 지원된 적이 없던 기능이라 targetAll은 쓰지 않습니다.
    private ChatResponseDto handleReviewDeleteRequest(User user, ChatConversation conversation, String userMessage, ExtractedFilter filter) {
        List<ReviewDto> reviews = reviewService.listByUser(user.getId());
        if (reviews.isEmpty()) {
            String reply = "삭제할 리뷰가 없어요.";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        List<ReviewDto> matched = matchAgainstOwnList(
                userMessage, false, Boolean.TRUE.equals(filter.targetMostRecent()), reviews, ReviewDto::movieTitle);

        if (matched.isEmpty()) {
            String reply = "어떤 영화의 리뷰를 삭제할지 제목으로 말씀해주시겠어요? (작성하신 리뷰: %s)"
                    .formatted(reviews.stream().map(ReviewDto::movieTitle).collect(Collectors.joining(", ")));
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        for (ReviewDto review : matched) {
            reviewService.delete(review.movieId(), review.id(), user.getId());
        }
        String titles = matched.stream().map(ReviewDto::movieTitle).collect(Collectors.joining(", "));
        String reply = "영화 %s %s 리뷰를 삭제했습니다."
                .formatted(titles, matched.size() > 1 ? "등 %d건의".formatted(matched.size()) : "의");
        return persistTurn(user, conversation, userMessage, reply, List.of());
    }

    // "영화 퍼펙트 게임을 시청 완료 목록에 추가해줘"/"이 영화 시청완료 해줘"처럼 시청완료로 표시해달라는
    // 요청입니다. 대상 영화는 resolveNewMovieReferences(찜하기와 공유하는 로직)로 찾고, 특정 제목을 못
    // 찾았으면("이 영화 시청완료 해줘"처럼 지시어만 쓴 경우 등) 직전 추천 목록 전체를 시청완료 처리합니다.
    private ChatResponseDto handleWatchedAddRequest(
            User user, ChatConversation conversation, String userMessage, ExtractedFilter filter, List<ChatMessage> fullHistory
    ) {
        List<MovieSummaryDto> resolved = resolveNewMovieReferences(filter.movieTitles(), userMessage, fullHistory);
        List<MovieSummaryDto> movies = !resolved.isEmpty() ? resolved : lastRecommendedMovies(fullHistory);
        if (movies.isEmpty()) {
            String reply = "어떤 영화를 시청완료로 표시할지 영화 제목을 말씀해주시겠어요?";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }
        return addToWatched(user, conversation, userMessage, movies);
    }

    private ChatResponseDto addToWatched(
            User user, ChatConversation conversation, String userMessage, List<MovieSummaryDto> movies
    ) {
        for (MovieSummaryDto movie : movies) {
            watchedMovieService.add(user.getId(), new WatchedMovieRequest(movie.id(), movie.title(), movie.posterUrl()));
        }
        String titles = movies.stream().map(MovieSummaryDto::title).collect(Collectors.joining(", "));
        String reply = "영화 %s %s 시청완료 목록에 추가했습니다."
                .formatted(titles, movies.size() > 1 ? "등 %d편을".formatted(movies.size()) : "을(를)");
        return persistTurn(user, conversation, userMessage, reply, List.of());
    }

    // 특정 제목 언급 없이 "모든/전부"면 전체(filter.targetAll()), "이 영화"/"방금"/"최근"처럼 지시어만
    // 쓰면 가장 최근에 시청완료로 표시한 영화를 가리키는 걸로 봅니다(filter.targetMostRecent() -
    // watchedMovieService.list가 최신순이라 0번째가 가장 최근).
    private ChatResponseDto handleWatchedDeleteRequest(User user, ChatConversation conversation, String userMessage, ExtractedFilter filter) {
        List<WatchedMovieDto> watched = watchedMovieService.list(user.getId());
        if (watched.isEmpty()) {
            String reply = "시청완료로 표시된 영화가 없어요.";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        List<WatchedMovieDto> matched = matchAgainstOwnList(
                userMessage, Boolean.TRUE.equals(filter.targetAll()), Boolean.TRUE.equals(filter.targetMostRecent()),
                watched, WatchedMovieDto::movieTitle);

        if (matched.isEmpty()) {
            String reply = "어떤 영화를 시청완료 목록에서 삭제할지 제목으로 말씀해주시겠어요? (현재 목록: %s)"
                    .formatted(watched.stream().map(WatchedMovieDto::movieTitle).collect(Collectors.joining(", ")));
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        for (WatchedMovieDto item : matched) {
            watchedMovieService.remove(user.getId(), item.movieId());
        }
        String titles = matched.stream().map(WatchedMovieDto::movieTitle).collect(Collectors.joining(", "));
        String reply = "영화 %s %s 시청완료 목록에서 삭제했습니다."
                .formatted(titles, matched.size() > 1 ? "등 %d편을".formatted(matched.size()) : "을(를)");
        return persistTurn(user, conversation, userMessage, reply, List.of());
    }

    private ChatResponseDto handleWatchedListRequest(User user, ChatConversation conversation, String userMessage) {
        List<WatchedMovieDto> watched = watchedMovieService.list(user.getId());
        if (watched.isEmpty()) {
            String reply = "아직 시청완료로 표시한 영화가 없어요.";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        List<MovieSummaryDto> movies = watched.stream()
                .map(item -> toSummaryOrNull(item.movieId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        String titles = watched.stream().map(WatchedMovieDto::movieTitle).collect(Collectors.joining(", "));
        String reply = "시청완료로 표시한 영화는 %d편이에요: %s".formatted(watched.size(), titles);
        return persistTurn(user, conversation, userMessage, reply, movies);
    }

    // "선호 장르를 액션, 공포로 바꿔줘"처럼 선호 장르를 바꿔달라는 요청입니다. Gemini가 뽑아준 genresToSet
    // 으로 선호 장르를 통째로 바꿉니다(추가가 아니라 교체입니다 - "바꿔줘"라는 표현 자체가 "이걸로
    // 바꿔라"는 뜻이라 기존 선택은 유지하지 않습니다).
    private ChatResponseDto handleGenrePreferenceUpdateRequest(User user, ChatConversation conversation, String userMessage, ExtractedFilter filter) {
        List<String> mentionedGenres = filter.genresToSet() == null ? List.of() : filter.genresToSet();
        if (mentionedGenres.isEmpty()) {
            String reply = "어떤 장르로 바꾸고 싶으신지 장르 이름으로 말씀해주시겠어요? (예: SF, 공포, 액션)";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        PreferredGenresDto updated = userService.updatePreferredGenres(user.getId(), mentionedGenres);
        String reply = "선호 장르를 %s(으)로 변경했습니다.".formatted(String.join(", ", updated.genres()));
        return persistTurn(user, conversation, userMessage, reply, List.of());
    }

    private ChatResponseDto handleGenrePreferenceViewRequest(User user, ChatConversation conversation, String userMessage) {
        PreferredGenresDto preferences = userService.getPreferredGenres(user.getId());
        String reply = preferences.genres().isEmpty()
                ? "아직 선호 장르를 설정하지 않으셨어요."
                : "현재 선호 장르는 %s(이)에요.".formatted(String.join(", ", preferences.genres()));
        return persistTurn(user, conversation, userMessage, reply, List.of());
    }

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

    // 예매한 영화명/상영관/상영일시/좌석을 그대로 알려줍니다(ticketCounts는 마이페이지 예매 내역과 달리
    // 여기선 좌석 목록만으로 충분히 구체적이라 생략합니다).
    private ChatResponseDto handleBookingListRequest(User user, ChatConversation conversation, String userMessage) {
        List<BookingDto> bookings = bookingService.listByUser(user.getId());
        if (bookings.isEmpty()) {
            String reply = "아직 예매한 영화가 없어요.";
            return persistTurn(user, conversation, userMessage, reply, List.of());
        }

        String summary = bookings.stream()
                .map(booking -> "- %s (%s, %s %s) - 좌석: %s".formatted(
                        booking.movieTitle(),
                        booking.theaterName(),
                        formatDateLabel(booking.showDate()),
                        booking.showtime(),
                        String.join(", ", booking.seats())))
                .collect(Collectors.joining("\n"));
        String reply = "예매하신 영화는 총 %d건이에요.\n%s".formatted(bookings.size(), summary);
        List<MovieSummaryDto> movies = bookings.stream()
                .map(booking -> toSummaryOrNull(booking.movieId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return persistTurn(user, conversation, userMessage, reply, movies);
    }
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
            return withCore(
                    filter, filter.intent(), null, null, null, null, null, title, filter.count());
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
