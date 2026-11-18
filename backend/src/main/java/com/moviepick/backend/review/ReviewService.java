package com.moviepick.backend.review;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.movie.MovieTitleResolver;
import com.moviepick.backend.notification.NotificationService;
import com.moviepick.backend.review.dto.ReviewDto;
import com.moviepick.backend.review.dto.ReviewHelpfulResponse;
import com.moviepick.backend.review.dto.ReviewReportDetailDto;
import com.moviepick.backend.review.dto.ReviewRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final ReviewHelpfulVoteRepository reviewHelpfulVoteRepository;
    private final ReviewHelpfulNotificationRepository reviewHelpfulNotificationRepository;
    private final UserRepository userRepository;
    private final MovieTitleResolver movieTitleResolver;
    private final NotificationService notificationService;

    public ReviewService(
            ReviewRepository reviewRepository,
            ReviewReportRepository reviewReportRepository,
            ReviewHelpfulVoteRepository reviewHelpfulVoteRepository,
            ReviewHelpfulNotificationRepository reviewHelpfulNotificationRepository,
            UserRepository userRepository,
            MovieTitleResolver movieTitleResolver,
            NotificationService notificationService
    ) {
        this.reviewRepository = reviewRepository;
        this.reviewReportRepository = reviewReportRepository;
        this.reviewHelpfulVoteRepository = reviewHelpfulVoteRepository;
        this.reviewHelpfulNotificationRepository = reviewHelpfulNotificationRepository;
        this.userRepository = userRepository;
        this.movieTitleResolver = movieTitleResolver;
        this.notificationService = notificationService;
    }

    // viewerUserId: 영화 상세 화면에서 로그인한 사용자 - 리뷰별 "내가 이미 추천했는지"를 알려줘야 하니
    // 받습니다(다른 목록 메서드는 본인 리뷰만 보여주거나 추천 UI가 없어 필요 없습니다).
    public List<ReviewDto> list(String movieId, Long viewerUserId) {
        return toDtos(reviewRepository.findByMovieIdOrderByCreatedAtDesc(movieId), viewerUserId);
    }

    public List<ReviewDto> listByUser(Long userId) {
        return toDtos(reviewRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId));
    }

    // 리뷰에 저장된 영화 제목 대신 movies 테이블의 제목으로 보여줍니다(영화 상세/목록/예매 화면과 같은 제목).
    private List<ReviewDto> toDtos(List<Review> reviews) {
        return toDtos(reviews, null);
    }

    private List<ReviewDto> toDtos(List<Review> reviews, Long viewerUserId) {
        Map<String, String> titles = movieTitleResolver.titlesById(
                reviews.stream().map(Review::getMovieId).toList());
        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        Map<Long, Long> helpfulCounts = reviewIds.isEmpty() ? Map.of() : reviewHelpfulVoteRepository.countByReviewIds(reviewIds).stream()
                .collect(Collectors.toMap(ReviewHelpfulVoteRepository.HelpfulCountRow::getReviewId, ReviewHelpfulVoteRepository.HelpfulCountRow::getCnt));
        Set<Long> myVotedReviewIds = viewerUserId == null || reviewIds.isEmpty()
                ? Set.of()
                : reviewHelpfulVoteRepository.findByUserIdAndReviewIdIn(viewerUserId, reviewIds).stream()
                        .map(vote -> vote.getReview().getId())
                        .collect(Collectors.toCollection(HashSet::new));
        return reviews.stream()
                .map(review -> ReviewDto.from(
                        review,
                        titles.getOrDefault(review.getMovieId(), review.getMovieTitle()),
                        helpfulCounts.getOrDefault(review.getId(), 0L),
                        myVotedReviewIds.contains(review.getId())
                ))
                .toList();
    }

    // 관리자 "리뷰 신고 관리" 화면 - DB에 저장된 모든 사용자의 리뷰를 전부 조회합니다.
    public List<ReviewDto> listAllForAdmin(Long adminUserId) {
        requireAdmin(adminUserId);
        return toDtos(reviewRepository.findAllByOrderByCreatedAtDesc());
    }

    // 관리자는 작성자가 아니어도 부적절한 리뷰를 삭제할 수 있어야 하므로, 본인 리뷰로만 제한하는
    // requireOwnedReview()와 별도로 둡니다.
    public void adminDelete(Long reviewId, Long adminUserId) {
        requireAdmin(adminUserId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ApiException("리뷰를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        reviewRepository.delete(review);
    }

    public ReviewDto create(String movieId, Long userId, ReviewRequest request) {
        // 프론트의 "리뷰 쓰기" 버튼은 이미 리뷰를 쓴 영화면 비활성화하지만, AI 채팅처럼 그 버튼을 거치지
        // 않는 경로로는 여전히 중복 작성이 가능했습니다(2026-10-11 실측) - 같은 규칙을 여기서도 강제합니다.
        if (reviewRepository.existsByUserIdAndMovieId(userId, movieId)) {
            throw new ApiException("이미 작성한 리뷰가 있습니다. 기존 리뷰를 수정해주세요.", HttpStatus.CONFLICT);
        }
        User user = requireUser(userId);
        // 제목은 프론트가 보낸 값 대신 movies 기준으로 저장합니다(movies에 없는 영화일 때만 요청 값 사용).
        String movieTitle = movieTitleResolver.titleFor(movieId, request.movieTitle());
        Review review = new Review(movieId, movieTitle, user, request.score(), request.content());
        return ReviewDto.from(reviewRepository.save(review));
    }

    public ReviewDto update(String movieId, Long reviewId, Long userId, ReviewRequest request) {
        Review review = requireOwnedReview(movieId, reviewId, userId);
        review.update(request.score(), request.content());
        Review saved = reviewRepository.save(review);
        return ReviewDto.from(saved, movieTitleResolver.titleFor(saved.getMovieId(), saved.getMovieTitle()));
    }

    public void delete(String movieId, Long reviewId, Long userId) {
        Review review = requireOwnedReview(movieId, reviewId, userId);
        reviewRepository.delete(review);
    }

    // 리뷰 목록의 "신고" 버튼 - 본인 리뷰는 신고할 수 없고(작성자가 직접 "수정/삭제"로 처리하면 됨),
    // 같은 사람이 같은 리뷰를 두 번 신고할 수 없습니다. 신고가 접수되면 관리자 전원에게 알립니다
    // (NotificationService의 "전체 관리자에게 알림" 패턴 재사용 - 예매 완료/취소 알림과 동일).
    @Transactional
    public void reportReview(String movieId, Long reviewId, Long reporterUserId, String reasonCode) {
        User reporter = requireUser(reporterUserId);
        Review review = reviewRepository.findById(reviewId)
                .filter(candidate -> candidate.getMovieId().equals(movieId))
                .orElseThrow(() -> new ApiException("리뷰를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (review.getUser().getId().equals(reporterUserId)) {
            throw new ApiException("본인이 작성한 리뷰는 신고할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if (reviewReportRepository.existsByReviewIdAndReporterId(reviewId, reporterUserId)) {
            throw new ApiException("이미 신고한 리뷰입니다.", HttpStatus.CONFLICT);
        }
        ReviewReportReason reason = ReviewReportReason.fromCode(reasonCode);
        ReviewReport savedReport = reviewReportRepository.save(new ReviewReport(review, reporter, reason.name()));
        notificationService.notifyReviewReported(reporter, review, reason.label(), savedReport.getId());
    }

    // 리뷰 목록의 "도움이 됐어요" 버튼 - 신고(reportReview)와 대칭되는 긍정적 상호작용입니다. 본인
    // 리뷰는 추천할 수 없고, 이미 눌렀으면 다시 눌러서 추천을 취소할 수 있습니다(토글).
    @Transactional
    public ReviewHelpfulResponse toggleHelpful(String movieId, Long reviewId, Long userId) {
        User user = requireUser(userId);
        Review review = reviewRepository.findById(reviewId)
                .filter(candidate -> candidate.getMovieId().equals(movieId))
                .orElseThrow(() -> new ApiException("리뷰를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (review.getUser().getId().equals(userId)) {
            throw new ApiException("본인이 작성한 리뷰는 추천할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        boolean helpfulByMe = reviewHelpfulVoteRepository.findByReviewIdAndUserId(reviewId, userId)
                .map(existing -> {
                    reviewHelpfulVoteRepository.delete(existing);
                    return false;
                })
                .orElseGet(() -> {
                    reviewHelpfulVoteRepository.save(new ReviewHelpfulVote(review, user));
                    // 같은 사람이 같은 리뷰에 설정→해제→설정을 반복해도 알림은 평생 최초 1회만 - 추천
                    // 자체(review_helpful_votes)와 달리 이 이력은 해제해도 지우지 않습니다.
                    if (!reviewHelpfulNotificationRepository.existsByReviewIdAndVoterUserId(reviewId, userId)) {
                        reviewHelpfulNotificationRepository.save(new ReviewHelpfulNotification(reviewId, userId));
                        notificationService.notifyReviewHelpful(review.getUser(), review, user);
                    }
                    return true;
                });

        return new ReviewHelpfulResponse(reviewHelpfulVoteRepository.countByReviewId(reviewId), helpfulByMe);
    }

    // 관리자 알림의 "보러가기" - 리뷰 신고 알림을 클릭했을 때 신고 상세(리뷰 내용/작성자/신고 사유/
    // 신고자)를 보여주는 다이얼로그용입니다.
    public ReviewReportDetailDto getReportDetailForAdmin(Long adminUserId, Long reportId) {
        requireAdmin(adminUserId);
        ReviewReport report = reviewReportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException("신고 내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        return ReviewReportDetailDto.from(report);
    }

    // 영화 목록/검색 결과에 붙일 평균 평점을 movieId별로 한 번에 모아서 돌려줍니다(N+1 방지).
    public Map<String, RatingSummary> getRatingSummaries(Collection<String> movieIds) {
        if (movieIds.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.aggregateRatings(movieIds).stream()
                .collect(Collectors.toMap(
                        ReviewRepository.MovieRatingRow::getMovieId,
                        row -> new RatingSummary(row.getAverageScore(), row.getReviewCount())
                ));
    }

    // 홈 화면 "지금 인기 있는 영화" - 평균 평점이 minScore 이상인 영화를 평점 높은 순으로 돌려줍니다(고정 제목 목록 없음).
    public List<TopRatedMovie> getTopRatedMovies(double minScore) {
        return reviewRepository.findTopRated(minScore).stream()
                .map(row -> new TopRatedMovie(row.getMovieId(), row.getAverageScore(), row.getReviewCount()))
                .toList();
    }

    private Review requireOwnedReview(String movieId, Long reviewId, Long userId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(candidate -> candidate.getMovieId().equals(movieId))
                .orElseThrow(() -> new ApiException("리뷰를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (!review.getUser().getId().equals(userId)) {
            throw new ApiException("본인이 작성한 리뷰만 수정/삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        return review;
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));
    }

    private void requireAdmin(Long userId) {
        if (!requireUser(userId).isAdmin()) {
            throw new ApiException("관리자만 이용할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
    }
}
