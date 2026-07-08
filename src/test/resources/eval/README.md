# Golden Set — 답변 품질 평가 데이터 (Phase B2)

RAG 품질을 숫자로 측정하기 위한 평가 셋. 청킹 크기·topK·프롬프트를 바꿨을 때 좋아졌는지/나빠졌는지를
회귀 테스트처럼 확인하는 근거 데이터다.

## 구성

```
eval/
├─ corpus/          평가용 문서 8개 (사내 HR/총무 규정). 파일명 = 논리적 slug.
│   ├─ vacation.md    휴가 규정
│   ├─ attendance.md  근태 규정
│   ├─ salary.md      급여 규정
│   ├─ expense.md     경비 규정
│   ├─ welfare.md     복지 제도
│   ├─ security.md    정보보안 규정
│   ├─ onboarding.md  온보딩 안내
│   └─ equipment.md   비품·장비 규정
└─ questions.json   질문 50개 (answerable 30 + unanswerable 20)
```

- 문서: md 한 장 = 위키 페이지. H1이 제목, 본문이 내용. 러너가 `create(제목, 내용)`으로 적재하며 slug→documentId 매핑을 잡는다.
- 질문: `expectedDocSlug`는 **DB auto-increment id가 아니라 논리적 slug**로 기대 출처를 가리킨다 → id 흔들림과 무관하게 결정적.

## 질문 선정 기준

**answerable 30** — 문서에 답이 명확히 있는 질문. 난이도 3단계를 섞는다.
- easy: 문서 문장과 표현이 거의 일치 (예: "연차는 며칠 부여되나요?").
- medium: 동의어·다른 표현 (예: "미사용 연차는 어떻게 되나요?" → "소멸").
- hard: 여러 문장 종합·조건 (예: "병가 연속 5일 → 진단서 필요").
- 8개 문서에 고르게 분포(문서당 3~5개), 각 질문에 `expectedDocSlug` + `expectedAnswer`.

**unanswerable 20** — 문서에 없지만 **도메인 안이라 그럴듯한** 질문(환각 유발용).
- "인접" 함정 위주: 문서가 다루는 주제의 바로 옆(예: 문서에 본인 결혼은 있으나 형제 결혼은 없음, 국내 출장은 있으나 해외는 없음).
- 기대 답 = "모르겠습니다" (RAG의 no-context 응답과 대조).

## 지표 매핑 (B2-3~B2-4)

- `expectedDocSlug` → **hit rate@K** (검색 품질, LLM 불필요·결정적): 기대 slug의 청크가 topK 검색에 포함됐나.
- `unanswerable` → **환각률** (생성 품질): 답 없는 질문에 "모르겠습니다"가 아닌 답을 지어냈나.
- `expectedAnswer` → **인용 정확도·정답성** (생성 품질, LLM 필요): 답변이 기대 근거·정답과 일치하나.

## 주의

- 정답(`expectedAnswer`)은 corpus 문서와 반드시 일치해야 한다. 문서를 고치면 질문·정답도 함께 검토.
- 이 데이터는 사실 관계가 검증돼야 하므로 Claude 초안 → 연우님 검수를 거친다.
