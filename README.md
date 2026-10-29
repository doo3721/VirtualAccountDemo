# PortOne + 토스페이먼츠 가상계좌 데모

Spring Boot, Spring Data JPA, Thymeleaf로 구현한 로컬 SSR 테스트 애플리케이션입니다.
PortOne **V2 REST API**를 통해 토스페이먼츠의 일반(회전식) 가상계좌를 수기 발급하고,
PortOne 테스트 입금 웹훅을 수신하면 결제 정보를 검증한 뒤 DB를 **입금 완료**로 자동 변경합니다.

## 기술 구성과 주요 기능

- Java 25 / Spring Boot 4.1.2 / Gradle Wrapper
- Spring MVC + Thymeleaf: 발급 폼, 최근 50건 목록, 결제 상세 화면
- Spring Data JPA + H2 파일 DB: 서버 재시작 후에도 결제 내역 유지
- Spring RestClient: PortOne 가상계좌 발급 및 결제 단건 조회
- Spring Security: 폼 CSRF 보호, 웹훅 경로만 CSRF 예외
- 중복 발급 방지, 발급 타임아웃 복구, 웹훅 재전송 처리
- 상세 화면에 웹훅 반영 시각과 이벤트 표시

현금영수증 신청, 에스크로, 환불, 고정식 계좌는 구현 범위에 포함하지 않습니다.
로그인 없이 사용하는 로컬 데모이므로 공개 운영에는 사용자 인증과 주문 접근 권한 등의 보완이 필요합니다.

## 1. PortOne 설정

PortOne 콘솔에 **V2 토스페이먼츠(신모듈) 테스트 채널**을 준비합니다.
프로젝트 루트에 `application-local.example.yaml`을 `application-local.yaml`로 복사한 뒤 값을 입력합니다.
기존 파일이 있다면 필요한 항목만 수정합니다.

```yaml
portone:
  api-secret: "PortOne V2 API Secret"
  store-id: "store-..."
  channel-key: "channel-key-..."
  webhook-url: "https://공개주소/webhooks/portone"
```

| 항목 | 설명 | 환경 변수 |
| --- | --- | --- |
| `api-secret` | PortOne V2 API Secret. 토스페이먼츠 시크릿 키와 다름 | `PORTONE_API_SECRET` |
| `store-id` | PortOne 상점 ID (`store-...`) | `PORTONE_STORE_ID` |
| `channel-key` | 해당 상점의 토스페이먼츠 테스트 채널 키 | `PORTONE_CHANNEL_KEY` |
| `webhook-url` | 공개 HTTPS 웹훅 수신 주소. 비우면 PortOne 콘솔 설정 사용 | `PORTONE_WEBHOOK_URL` |

`iamporttest_3` 같은 토스페이먼츠 PG 상점 ID(MID)는 PortOne의 `store-id`가 아닙니다.
공용 테스트 MID의 거래내역·웹훅 설정 권한은 본인의 토스페이먼츠 상점과 다를 수 있습니다.

`application-local.yaml`은 프로젝트 루트를 기준으로 불러옵니다.
같은 항목을 로컬 YAML에 직접 입력하면 위 환경 변수 대신 YAML 값이 적용되므로,
환경 변수로만 설정할 때는 해당 YAML 항목을 생략하세요.
설정을 변경한 뒤에는 서버를 재시작합니다.
비밀 키 파일, `.env`, 로컬 DB는 `.gitignore`에 포함되어 있습니다. `.env` 자동 로딩 기능은 없습니다.

## 2. 실행

### Gradle로 실행

Java 25를 준비한 뒤 프로젝트 루트에서 실행합니다.

```powershell
.\gradlew.bat bootRun
```

접속 주소: [http://localhost:8080](http://localhost:8080)

기본 바인딩은 `127.0.0.1:8080`입니다. `SERVER_ADDRESS`, `SERVER_PORT` 환경 변수로 변경할 수 있습니다.
키 없이도 화면은 열리며, 필수 연동 정보가 설정되면 발급 버튼이 활성화됩니다.

### IntelliJ에서 실행

1. 프로젝트를 Gradle 프로젝트로 열고 Project SDK와 Gradle JVM을 Java 25로 설정합니다.
2. Gradle 도구 창에서 **Reload All Gradle Projects**를 실행합니다.
3. 실행 구성의 Working directory를 프로젝트 루트로 설정합니다.
4. `VirtualAccountDemoApplication.main()`을 실행합니다.

기본 DB인 H2는 `implementation 'com.h2database:h2'`로 main 모듈의 컴파일·실행 클래스패스에 포함했습니다.
`ClassNotFoundException: org.h2.Driver`가 발생하면 Gradle 새로고침 후 다시 실행하세요.
이미 Gradle이나 JAR로 서버를 실행 중이라면 먼저 종료합니다. 동일 포트나 H2 파일을 두 프로세스가 동시에 사용하지 않도록 합니다.

## 3. 공개 웹훅 주소 연결

PortOne이 로컬 서버에 알림을 보내려면 외부에서 접근할 수 있는 **HTTPS 주소**가 필요합니다.
PortOne 콘솔에 등록한 `localhost`나 `127.0.0.1` 주소로는 이 PC에 도달할 수 없습니다.

ngrok을 설치하고 계정 설정을 마쳤다면 별도 터미널에서 실행합니다.

```powershell
ngrok http 8080
```

발급된 HTTPS 도메인 뒤에 `/webhooks/portone`을 붙입니다.
이 데모에서 실제 연결을 확인했던 수신 주소는 다음과 같습니다.

```text
https://prong-yelp-dripping.ngrok-free.dev/webhooks/portone
```

이 주소를 `portone.webhook-url`에 설정하고 서버를 재시작합니다.
사용 중인 터널의 도메인이 다르거나 변경됐다면 새 주소를 사용하세요. 앱과 ngrok은 모두 실행 중이어야 합니다.

PortOne 콘솔의 결제알림(Webhook) 설정도 다음과 같이 맞춥니다.

- 결제 모듈: **V2**
- 모드: **테스트**
- 웹훅 버전: **2024-04-25**
- Content-Type: **application/json**
- Endpoint URL: `https://공개주소/webhooks/portone`

`portone.webhook-url`이 설정되면 새 계좌 발급 요청의 `noticeUrls`에 해당 주소를 전달하여 콘솔 수신 주소를 덮어씁니다.
비워 두면 콘솔 설정을 사용합니다. 이 값을 바꿔도 기존 결제의 발급 요청이 수정되지는 않습니다.
기존 결제를 다시 테스트할 때는 해당 결제의 웹훅 대상 URL과 재전송 설정을 확인하세요.

## 4. 가상계좌 발급과 테스트 입금

1. 데모의 **가상계좌 발급** 화면에서 주문명, 금액, 구매자 정보, 은행, 입금 기한을 입력합니다.
2. 서버는 주문을 먼저 DB에 커밋한 뒤 PortOne의 `POST /payments/{paymentId}/instant`를 호출합니다.
3. PortOne의 `GET /payments/{paymentId}`로 계좌 정보를 조회·검증하고 **입금 대기** 상태를 저장합니다.
4. PortOne 콘솔에서 **테스트 결제내역 → 같은 결제 ID의 상세 → 테스트 입금 통지(테스트 입금웹훅통지)**를 실행합니다.
5. PortOne이 보낸 `Transaction.Paid`를 서버가 수신하면 API로 해당 결제를 재조회합니다.
6. 조회 결과가 `PAID`이고 검증에 성공하면 **입금 완료**, 입금 시각, 웹훅 반영 시각을 DB에 저장합니다.
7. 상세 화면을 새로고침하여 **입금 완료**, **마지막 웹훅 반영**, **웹훅 이벤트: Transaction.Paid**를 확인합니다.

웹훅이 도착하면 서버 상태는 자동 변경되므로 **입금 상태 조회** 버튼을 누를 필요가 없습니다.
화면 자체는 실시간으로 갱신되지 않으므로 새로고침해야 합니다.
버튼을 이용한 수동 조회도 가능하지만, 수동 조회는 웹훅 반영 시각과 이벤트를 기록하지 않습니다.
테스트 가상계좌에는 실제로 송금하지 않습니다.

### 일반 웹훅 호출 테스트와 실제 결제 통지의 차이

콘솔의 일반 **웹훅 호출 테스트**는 연결 확인용 샘플 상점 ID와 결제 ID를 보낼 수 있습니다.
앱에 설정된 상점과 다르거나 DB에 없는 주문이면 서버는 `204`로 응답하고 무시합니다.
따라서 HTTP `204`만으로 입금 완료 반영까지 성공했다고 판단하지 않습니다.
**데모에서 발급한 실제 결제 ID의 상세 화면**에서 테스트 입금 통지를 실행해야 합니다.

### 토스페이먼츠 웹훅과의 관계

PG에서 시작하는 일반적인 입금 알림 경로는 다음과 같습니다.

```text
토스페이먼츠 → PortOne → 데모 서버 → PortOne API 재조회 → DB 갱신
```

| 등록 위치 | 수신 서버 | URL / 이벤트 |
| --- | --- | --- |
| 토스페이먼츠 개발자센터의 해당 MID 웹훅 | PortOne | `https://tx-gateway-service.prod.iamport.co/virtual-account/webhook-event/tosspayments` / `DEPOSIT_CALLBACK` |
| PortOne 콘솔 V2 테스트 결제알림 | 데모 서버 | `https://공개주소/webhooks/portone` / `Transaction.Paid` 등 |

PortOne 콘솔의 테스트 입금은 임의 통지 기능이며, **PortOne → 데모 서버** 구간을 확인하는 데 사용할 수 있습니다.
이 동작만으로 토스페이먼츠의 입금 처리·웹훅 발송까지 검증한 것으로 간주하지 않습니다.
토스페이먼츠 전송 기록이 비었다는 사실만으로 데모 서버의 수신 장애라고 판단할 수 없습니다.
PG부터 확인하려면 토스페이먼츠 개발자센터의 해당 MID 테스트 결제내역에서 입금을 처리하고,
공식 가이드에 따라 PG→PortOne 입금 통보 설정도 확인합니다.

## 5. 웹훅 검증과 오류 확인

수신 경로는 `POST /webhooks/portone`입니다.
이 데모는 별도 웹훅 시크릿으로 서명을 검증하는 대신, **PortOne API 재조회 결과를 검증하는 전략**을 사용합니다.

- 알림의 상점 ID가 설정과 일치하고 결제 ID가 자체 DB에 있는지 먼저 확인합니다.
- API 응답의 결제 ID, 상점, 토스 채널, 금액, KRW 통화, 가상계좌 정보를 검증합니다.
- 알림에 `Transaction.Paid`라고 적혀 있다는 이유만으로 완료 처리하지 않습니다.
- 중복 통지 시에도 최신 결제 상태를 조회하고, DB 잠금과 `updatedAt` 비교로 오래된 응답의 덮어쓰기를 방지합니다.
- 이미 취소된 결제라면 최신 API 상태인 취소를 반영합니다.

| 응답 / 로그 | 의미와 확인할 사항 |
| --- | --- |
| `204` + `PortOne 웹훅 처리 완료` | 조회·검증 후 DB 반영 완료. 상세 화면의 웹훅 정보 확인 |
| `204` + `설정된 상점 ID와 불일치` | 샘플 알림인지, 상점·채널이 올바른지 확인 |
| `204` + `이 서버에 저장되지 않은 결제` | 앱에서 발급한 결제인지, 같은 DB를 사용하는지 확인 |
| `204` + `지원 대상이 아닌 이벤트` 또는 `data 없음` | 처리 대상이 아니므로 무시 |
| `502` + `PortOne 웹훅 처리 실패` | API 조회·검증 실패 또는 Paid 알림 직후 조회가 아직 입금 대기. 원인을 확인하고 재전송 |
| 수신 로그 자체가 없음 | ngrok 실행 상태, 포트, 콘솔의 테스트 모드·Endpoint URL·실제 전송 대상 확인 |

PortOne 콘솔의 웹훅 전송 결과와 ngrok 검사 화면을 함께 확인하면 수신 구간을 구분할 수 있습니다.
기본 ngrok 검사 화면은 [http://127.0.0.1:4040](http://127.0.0.1:4040)입니다.
서버는 API Secret, 요청 헤더, PG 원본 응답을 로그에 출력하지 않습니다.

## 6. 저장 및 발급 오류 복구

- H2 파일 DB: `./data/virtual-account-demo`, 스키마 설정: `ddl-auto: update`
- 같은 발급 폼의 중복 제출은 동일 결제 ID를 사용하며 발급 API를 다시 호출하지 않습니다.
- 발급 요청 전에 주문을 저장하므로 타임아웃이나 웹훅 선도착에도 주문을 찾을 수 있습니다.
- 발급 오류·타임아웃은 **발급 확인 필요**로 남깁니다. 새로 발급하기 전에 같은 주문의 상태를 먼저 조회합니다.
- 프로세스 중단으로 **발급 요청 중**에 머물러도 상태 조회로 복구할 수 있습니다.
- 입금 기한이 지났다는 이유만으로 자체적으로 결제 완료·실패를 추정하지 않습니다.

## 7. 로컬 HTTP 경로

| 메서드 | 경로 | 기능 |
| --- | --- | --- |
| GET | `/` | 발급 내역으로 이동 |
| GET | `/payments` | 최근 50건 목록 |
| GET | `/payments/new` | 가상계좌 발급 폼 |
| POST | `/payments` | 주문 저장 및 가상계좌 발급 |
| GET | `/payments/{id}` | 계좌·결제·웹훅 반영 정보 |
| POST | `/payments/{id}/sync` | 수동 상태 조회 및 DB 갱신 |
| POST | `/webhooks/portone` | PortOne 웹훅 수신 및 DB 자동 갱신 |

## 공식 문서

- [PortOne V2 결제 API](https://developers.portone.io/api/rest-v2/payment)
- [PortOne 토스페이먼츠 연동 가이드](https://developers.portone.io/opi/ko/integration/pg/v2/tosspayments)
- [PortOne 웹훅 연동 및 API 재조회 검증](https://developers.portone.io/opi/ko/integration/webhook/readme-v2)
- [PortOne 가상계좌 테스트 입금 통지](https://help.portone.io/content/vbank-test-guide)
- [PG별 가상계좌 입금통보 설정](https://developers.portone.io/opi/ko/integration/virtual-account/readme)
- [토스페이먼츠 FAQ](https://docs.tosspayments.com/resources/faq)
