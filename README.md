# API KEY 등록 경로(다음 파일에 실제 API KEY를 입력하시오)

backend/src/main/resources/application-local.yml


## KMDB API 키

- 발급/문서: https://www.kmdb.or.kr/info/api/apiDetail/6
- `kmdb.service-key`에 발급받은 ServiceKey를 입력하시오

```yaml
kmdb:
  service-key: KMdb_API_KEY
```

## Gemini API 키

- 발급: https://aistudio.google.com/
- `gemini.api-key`에 발급받은 키를 입력하시오

```yaml
gemini:
  api-key: GEMINI_API_KEY
```
