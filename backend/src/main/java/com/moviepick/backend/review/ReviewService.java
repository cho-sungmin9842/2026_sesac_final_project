package com.moviepick.backend.review;

import com.moviepick.backend.auth.User;
import com.moviepick.backend.auth.UserRepository;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.config.UploadProperties;
import com.moviepick.backend.movie.MovieTitleResolver;
import com.moviepick.backend.notification.NotificationService;
import com.moviepick.backend.review.dto.ReviewDeletionLogDto;
import com.moviepick.backend.review.dto.ReviewDto;
import com.moviepick.backend.review.dto.ReviewHelpfulResponse;
import com.moviepick.backend.review.dto.ReviewReportDetailDto;
import com.moviepick.backend.review.dto.ReviewRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReviewService {

    // 리뷰 사진으로 받아줄 이미지 MIME 타입과 용량 상한(application.yml의 spring.servlet.multipart와는
    // 별개로, "이미지가 아닌 파일"을 걸러내기 위한 애플리케이션 레벨 검증입니다).
    private static final long MAX_PHOTO_SIZE_BYTES = 5L * 1024 * 1024;

    private final ReviewRepository reviewRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final ReviewHelpfulVoteRepository reviewHelpfulVoteRepository;
    private final ReviewHelpfulNotificationRepository reviewHelpfulNotificationRepository;
    private final ReviewDeletionLogRepository reviewDeletionLogRepository;
    private final UserRepository userRepository;
    private final MovieTitleResolver movieTitleResolver;
    private final NotificationService notificationService;
    private final UploadProperties uploadProperties;

    public ReviewService(
            ReviewRepository reviewRepository,
            ReviewReportRepository reviewReportRepository,
            ReviewHelpfulVoteRepository reviewHelpfulVoteRepository,
            ReviewHelpfulNotificationRepository reviewHelpfulNotificationRepository,
            ReviewDeletionLogRepository reviewDeletionLogRepository,
            UserRepository userRepository,
            MovieTitleResolver movieTitleResolver,
            NotificationService notificationService,
            UploadProperties uploadProperties
    ) {
        this.reviewRepository = reviewRepository;
        this.reviewReportRepository = reviewReportRepository;
        this.reviewHelpfulVoteRepository = reviewHelpfulVoteRepository;
        this.reviewHelpfulNotificationRepository = reviewHelpfulNotificationRepository;
        this.reviewDeletionLogRepository = reviewDeletionLogRepository;
        this.userRepository = userRepository;
        this.movieTitleResolver = movieTitleResolver;
        this.notificationService = notificationService;
        this.uploadProperties = uploadProperties;
    }

    // viewerUserId: 영화 상세 화면에서 로그인한 사용자 - 리뷰별 "내가 이미 추천했는지"를 알려줘야 하니
    // 받습니다(다른 목록 메서드는 본인 리뷰만 보여주거나 추천 UI가 없어 필요 없습니다).
    public List<ReviewDto> list(String movieId, Long viewerUserId) {
        return toDtos(reviewRepository.findByMovieIdOrderByCreatedAtDescIdDesc(movieId), viewerUserId);
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

    // 관리자 "리뷰 관리" 화면 - DB에 저장된 모든 사용자의 리뷰를 전부 조회하고, 리뷰마다 "신고 N건"
    // 뱃지를 달 수 있도록 신고 수도 같이 내려줍니다(0건이면 신고 안 된 리뷰).
    public List<ReviewDto> listAllForAdmin(Long adminUserId) {
        requireAdmin(adminUserId);
        List<ReviewDto> dtos = toDtos(reviewRepository.findAllByOrderByCreatedAtDescIdDesc());
        List<Long> reviewIds = dtos.stream().map(ReviewDto::id).toList();
        Map<Long, Long> reportCounts = reviewIds.isEmpty() ? Map.of() : reviewReportRepository.countByReviewIds(reviewIds).stream()
                .collect(Collectors.toMap(ReviewReportRepository.ReportCountRow::getReviewId, ReviewReportRepository.ReportCountRow::getCnt));
        return dtos.stream()
                .map(dto -> new ReviewDto(
                        dto.id(), dto.movieId(), dto.movieTitle(), dto.userId(), dto.authorNickname(),
                        dto.score(), dto.content(), dto.photoUrl(), dto.photoOriginalName(), dto.createdAt(), dto.updatedAt(),
                        dto.helpfulCount(), dto.helpfulByMe(), reportCounts.getOrDefault(dto.id(), 0L)
                ))
                .toList();
    }

    // "리뷰 관리" 테이블의 "신고 N건" 뱃지 - 이 리뷰에 달린 신고 전부(사유/신고자/시각)를 보여줍니다.
    public List<ReviewReportDetailDto> getReportsForReview(Long adminUserId, Long reviewId) {
        requireAdmin(adminUserId);
        return reviewReportRepository.findByReviewIdOrderByCreatedAtDesc(reviewId).stream()
                .map(ReviewReportDetailDto::from)
                .toList();
    }

    // 관리자는 작성자가 아니어도 부적절한 리뷰를 삭제할 수 있어야 하므로, 본인 리뷰로만 제한하는
    // requireOwnedReview()와 별도로 둡니다.
    @Transactional
    public void adminDelete(Long reviewId, Long adminUserId) {
        User admin = requireAdmin(adminUserId);
        adminDeleteInternal(reviewId, admin);
    }

    // "리뷰 관리" 테이블의 일괄 삭제 - 선택한 리뷰를 하나씩 지우되, 하나가 실패(이미 삭제됨 등)해도
    // 나머지는 계속 처리합니다. 실패한 건은 "reviewId: 사유" 형태로 모아 돌려줘서 화면에 안내할 수 있게 합니다.
    @Transactional
    public List<String> adminDeleteMany(List<Long> reviewIds, Long adminUserId) {
        User admin = requireAdmin(adminUserId);
        List<String> failures = new ArrayList<>();
        for (Long reviewId : reviewIds) {
            try {
                adminDeleteInternal(reviewId, admin);
            } catch (ApiException e) {
                failures.add(reviewId + ": " + e.getMessage());
            }
        }
        return failures;
    }

    // adminDelete/adminDeleteMany의 공통 로직 - 이 리뷰를 신고했던 사람이 있다면 "삭제 처리됐다"고
    // 알려줍니다(지금까지 신고자는 "접수되었습니다"까지만 알고 그 이후 결과를 알 방법이 없었습니다).
    // 작성자 본인에게도 어떤 영화의 리뷰가, 어떤 사유로 삭제됐는지 알리고, 삭제 이력도 남깁니다 -
    // 리뷰/신고는 영영 사라지지만 "누가 언제 왜 지웠는지"는 review_deletion_logs에 스냅샷으로 남습니다.
    private void adminDeleteInternal(Long reviewId, User admin) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ApiException("리뷰를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        // review_reports는 review_id가 ON DELETE CASCADE라 리뷰를 지우면 신고 기록도 같이 사라집니다 -
        // 누구에게 알려야 하는지, 어떤 사유였는지는 지우기 전에 미리 알아둬야 합니다.
        List<User> reporters = reviewReportRepository.findReportersByReviewId(reviewId);
        String reasonLabels = reviewReportRepository.findReasonCodesByReviewId(reviewId).stream()
                .map(code -> ReviewReportReason.fromCode(code).label())
                .distinct()
                .collect(Collectors.joining(", "));
        String movieTitle = review.getMovieTitle();
        User author = review.getUser();
        reviewDeletionLogRepository.save(new ReviewDeletionLog(
                review.getId(), movieTitle, author.getNickname(), review.getContent(), review.getScore(),
                reasonLabels, admin.getNickname()
        ));
        deletePhotoFileIfPresent(review);
        reviewRepository.delete(review);
        reporters.forEach(reporter -> notificationService.notifyReviewReportResolved(reporter, movieTitle));
        notificationService.notifyReviewDeletedForAuthor(author, movieTitle, reasonLabels);
    }

    // "리뷰 관리" 화면의 "삭제 이력" - 최근 삭제된 것부터 보여줍니다.
    public List<ReviewDeletionLogDto> getDeletionLogs(Long adminUserId) {
        requireAdmin(adminUserId);
        return reviewDeletionLogRepository.findAllByOrderByDeletedAtDesc().stream()
                .map(ReviewDeletionLogDto::from)
                .toList();
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
        deletePhotoFileIfPresent(review);
        reviewRepository.delete(review);
    }

    // 리뷰 쓰기/수정 화면의 사진 첨부 - 본인 리뷰에만 첨부할 수 있고, 다시 올리면 이전 사진을 대체합니다.
    public ReviewDto uploadPhoto(String movieId, Long reviewId, Long userId, MultipartFile photo) {
        Review review = requireOwnedReview(movieId, reviewId, userId);
        if (photo == null || photo.isEmpty()) {
            throw new ApiException("첨부할 사진을 선택해주세요.", HttpStatus.BAD_REQUEST);
        }
        String contentType = photo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ApiException("이미지 파일만 첨부할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        if (photo.getSize() > MAX_PHOTO_SIZE_BYTES) {
            throw new ApiException("사진 용량은 5MB 이하만 가능합니다.", HttpStatus.BAD_REQUEST);
        }

        deletePhotoFileIfPresent(review);
        String savedFileName = saveReviewPhotoFile(photo);
        review.updatePhoto("/uploads/reviews/" + savedFileName, photo.getOriginalFilename());
        Review saved = reviewRepository.save(review);
        return ReviewDto.from(saved, movieTitleResolver.titleFor(saved.getMovieId(), saved.getMovieTitle()));
    }

    // 리뷰 수정 화면에서 "사진 첨부(선택)" 입력을 다시 열었다가 취소해 미리보기를 비운 경우 - 새 사진을
    // 올리는 게 아니라 기존에 붙어있던 사진을 떼어내는 요청입니다(WriteReviewDialog의 cancel 이벤트 참고).
    public ReviewDto removePhoto(String movieId, Long reviewId, Long userId) {
        Review review = requireOwnedReview(movieId, reviewId, userId);
        deletePhotoFileIfPresent(review);
        review.updatePhoto(null, null);
        Review saved = reviewRepository.save(review);
        return ReviewDto.from(saved, movieTitleResolver.titleFor(saved.getMovieId(), saved.getMovieTitle()));
    }

    private String saveReviewPhotoFile(MultipartFile photo) {
        try {
            Path reviewsDir = Path.of(uploadProperties.dir(), "reviews");
            Files.createDirectories(reviewsDir);
            String originalName = photo.getOriginalFilename();
            String extension = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf('.'))
                    : "";
            String fileName = UUID.randomUUID() + extension;
            Files.copy(photo.getInputStream(), reviewsDir.resolve(fileName));
            return fileName;
        } catch (IOException e) {
            log.error("리뷰 사진 저장에 실패했습니다.", e);
            throw new UncheckedIOException("리뷰 사진을 저장하지 못했습니다.", e);
        }
    }

    // photoUrl은 "/uploads/reviews/<파일명>" 형태라, 맨 앞 "/uploads/"를 떼어내면 app.upload.dir
    // 기준 상대 경로가 됩니다. 삭제 실패(이미 없는 파일 등)는 리뷰 삭제 자체를 막을 이유가 아니라 무시합니다.
    private void deletePhotoFileIfPresent(Review review) {
        String photoUrl = review.getPhotoUrl();
        if (photoUrl == null || !photoUrl.startsWith("/uploads/")) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(uploadProperties.dir(), photoUrl.substring("/uploads/".length())));
        } catch (IOException e) {
            log.warn("리뷰 사진 파일 삭제에 실패했습니다: {}", photoUrl, e);
        }
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

    // 신고 상세 다이얼로그의 "리뷰 삭제 취소" - adminDelete(리뷰 자체 삭제)와 달리 리뷰는 그대로 두고, 이
    // 신고 건만 없앱니다. 신고자에게는 "삭제되었습니다"와 다른 문구로 처리 결과를 알립니다.
    @Transactional
    public void cancelReviewDeletion(Long adminUserId, Long reportId) {
        requireAdmin(adminUserId);
        ReviewReport report = reviewReportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException("신고 내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        User reporter = report.getReporter();
        String movieTitle = report.getReview().getMovieTitle();
        reviewReportRepository.delete(report);
        notificationService.notifyReviewDeletionCancelled(reporter, movieTitle);
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

    private User requireAdmin(Long userId) {
        User user = requireUser(userId);
        if (!user.isAdmin()) {
            throw new ApiException("관리자만 이용할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        return user;
    }
}
