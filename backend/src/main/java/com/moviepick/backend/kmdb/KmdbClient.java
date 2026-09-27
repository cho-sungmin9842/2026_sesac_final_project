package com.moviepick.backend.kmdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviepick.backend.common.ApiException;
import com.moviepick.backend.config.KmdbProperties;
import com.moviepick.backend.kmdb.dto.KmdbSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.function.Function;

/**
 * KMDB(한국영상자료원) Open API 호출 담당.
 * <p>
 * 엔드포인트/파라미터명은 공개 문서 기준이며, 실제 서비스키로 호출해보면서 필요 시 조정하세요.
 * genre/releaseDts/releaseDte는 2026-09-21에 실제 키로 호출해 검증된 파라미터입니다.
 * releaseDts/releaseDte는 "제작연도"가 아니라 "개봉일자" 기준이라, 재개봉/특별상영 등으로
 * 화면에 표시되는 연도와 살짝 다른 결과가 섞일 수 있습니다.
 * KMDB는 ServiceKey가 잘못되었거나 파라미터가 이상해도 HTTP 200과 함께 HTML/XML 에러 페이지를
 * 내려주는 경우가 있어, 응답을 문자열로 받은 뒤 직접 JSON으로 파싱해서 그 상황도 걸러냅니다.
 */
@Slf4j
@Component
public class KmdbClient {

    private static final String COLLECTION = "kmdb_new2";
    private static final int RESPONSE_PREVIEW_LENGTH = 300;
    // 해외 영화는 서비스에서 다루지 않으므로, KMDB가 지원하는 nation 파라미터(부분일치, 공동제작도
    // "대한민국"이 포함되어 있으면 걸림)로 검색 단계에서부터 국내 영화만 받아옵니다.
    private static final String DOMESTIC_NATION = "대한민국";

    private final RestClient kmdbRestClient;
    private final KmdbProperties kmdbProperties;
    private final ObjectMapper objectMapper;

    public KmdbClient(RestClient kmdbRestClient, KmdbProperties kmdbProperties, ObjectMapper objectMapper) {
        this.kmdbRestClient = kmdbRestClient;
        this.kmdbProperties = kmdbProperties;
        this.objectMapper = objectMapper;
    }

    public KmdbSearchResponse searchByTitle(
            String title, String genre, String releaseDts, String releaseDte, int listCount, int startCount, String sort
    ) {
        return searchByField("title", title, genre, releaseDts, releaseDte, listCount, startCount, sort);
    }

    // 검색창이 "영화, 배우, 감독 검색"이라 안내하고 있어서, 배우/감독 이름으로도 찾을 수 있어야 합니다.
    // actor/director는 2026-09-21에 실제 키로 검증된 파라미터입니다(예: actor=마동석, director=봉준호).
    public KmdbSearchResponse searchByActor(
            String actor, String genre, String releaseDts, String releaseDte, int listCount, int startCount, String sort
    ) {
        return searchByField("actor", actor, genre, releaseDts, releaseDte, listCount, startCount, sort);
    }

    public KmdbSearchResponse searchByDirector(
            String director, String genre, String releaseDts, String releaseDte, int listCount, int startCount, String sort
    ) {
        return searchByField("director", director, genre, releaseDts, releaseDte, listCount, startCount, sort);
    }

    // 제목/배우/감독 어디에도 안 걸리는 "좀비", "시간여행" 같은 소재·테마 검색용. KMDB가 영화별로 태깅해둔
    // keywords 필드를 대상으로 찾아줍니다(2026-09-25에 실제 키로 확인: keyword=좀비 -> 킹덤, #살아있다 등).
    public KmdbSearchResponse searchByKeyword(
            String keyword, String genre, String releaseDts, String releaseDte, int listCount, int startCount, String sort
    ) {
        return searchByField("keyword", keyword, genre, releaseDts, releaseDte, listCount, startCount, sort);
    }

    private KmdbSearchResponse searchByField(
            String fieldName,
            String fieldValue,
            String genre,
            String releaseDts,
            String releaseDte,
            int listCount,
            int startCount,
            String sort
    ) {
        return call(uriBuilder -> {
            // 기본 골격은 collection/ServiceKey/detail/startCount/listCount/nation/sort 순으로 고정하고,
            // 그 위에 이번 조회가 실제로 쓰는 필터(제목·배우·감독, 장르, 개봉일자)만 덧붙입니다.
            uriBuilder.queryParam("collection", COLLECTION)
                    .queryParam("ServiceKey", kmdbProperties.serviceKey())
                    .queryParam("detail", "Y")
                    .queryParam("startCount", startCount)
                    .queryParam("listCount", listCount)
                    .queryParam("nation", DOMESTIC_NATION);
            // 정렬(최신순 -> prodYear,1 / 이름순 -> title,1)도 KMDB가 직접 지원하는 파라미터입니다.
            if (sort != null && !sort.isBlank()) {
                uriBuilder.queryParam("sort", sort);
            }
            uriBuilder.queryParam(fieldName, fieldValue);
            if (genre != null && !genre.isBlank()) {
                uriBuilder.queryParam("genre", toKmdbGenreParam(genre));
            }
            if (releaseDts != null && !releaseDts.isBlank()) {
                uriBuilder.queryParam("releaseDts", releaseDts);
            }
            if (releaseDte != null && !releaseDte.isBlank()) {
                uriBuilder.queryParam("releaseDte", releaseDte);
            }
            return uriBuilder.build();
        });
    }

    // "멜로/로맨스"처럼 "/"가 낀 장르명을 그대로 넘기면 KMDB가 0건으로 매칭에 실패합니다("/" 앞부분만
    // 보내도 전체 조합과 정확히 같은 결과가 나오는 걸 확인했습니다). 프론트(movieApi.js)에서도 같은 이유로
    // 앞부분만 잘라 보내지만, 이 채팅 기능처럼 백엔드가 직접 genre를 넘기는 경로도 있어 여기서 한 번 더 방어합니다.
    private String toKmdbGenreParam(String genre) {
        int slashIndex = genre.indexOf('/');
        return slashIndex == -1 ? genre : genre.substring(0, slashIndex);
    }

    public KmdbSearchResponse findByMovieId(String movieId, String movieSeq) {
        return call(uriBuilder -> uriBuilder
                .queryParam("collection", COLLECTION)
                .queryParam("ServiceKey", kmdbProperties.serviceKey())
                .queryParam("detail", "Y")
                .queryParam("movieId", movieId)
                .queryParam("movieSeq", movieSeq)
                .build());
    }

    private KmdbSearchResponse call(Function<UriBuilder, URI> uriFunction) {
        requireServiceKey();

        String body;
        try {
            body = kmdbRestClient.get()
                    .uri(uriFunction)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.error("KMDB API 호출 자체가 실패했습니다.", e);
            throw new ApiException("KMDB API 호출에 실패했습니다: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }

        try {
            return objectMapper.readValue(body, KmdbSearchResponse.class);
        } catch (Exception e) {
            log.error("KMDB 응답을 JSON으로 해석하지 못했습니다. body={}", body, e);
            throw new ApiException(
                    "KMDB 응답을 해석할 수 없습니다. ServiceKey가 올바른지, 파라미터가 맞는지 확인해주세요. 응답 일부: "
                            + preview(body),
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private String preview(String body) {
        if (body == null) {
            return "(응답 없음)";
        }
        return body.length() > RESPONSE_PREVIEW_LENGTH ? body.substring(0, RESPONSE_PREVIEW_LENGTH) + "..." : body;
    }

    private void requireServiceKey() {
        if (kmdbProperties.serviceKey() == null || kmdbProperties.serviceKey().isBlank()) {
            throw new ApiException(
                    "KMDB ServiceKey가 설정되지 않았습니다. application-local.yml의 kmdb.service-key를 채워주세요.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
