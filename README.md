# PortOne + 토스페이먼츠 가상계좌 데모

Spring Boot 4.1.2 / Java 25 / Spring Data JPA / Thymeleaf 기반 로컬 SSR 데모입니다.
PortOne **V2 REST API**로 일반(회전식) 가상계좌를 수기 발급합니다. 브라우저 결제창은 사용하지 않습니다.

## 설정 및 실행

1. PortOne 콘솔에서 **V2 토스페이먼츠(신모듈) 테스트 채널**을 준비합니다. 채널에서 API 가상계좌 발급이 가능한지 확인하세요.
2. 프로젝트 루트의 `application-local.yaml`에 아래 값을 입력합니다. 파일이 없다면 `application-local.example.yaml`을 복사합니다.

   ```yaml
   portone:
     api-secret: "PortOne V2 API Secret"
     store-id: "store-..."
     channel-key: "channel-key-..."
   ```

   API Secret은 **PortOne**에서 발급한 값입니다. 토스페이먼츠 시크릿 키와 다릅니다.
   `iamporttest_3` 같은 PG 상점 ID(MID)는 V2 `storeId`가 아닙니다.
   환경 변수 `PORTONE_API_SECRET`, `PORTONE_STORE_ID`, `PORTONE_CHANNEL_KEY`로도 설정할 수 있습니다.
   동일 키가 로컬 YAML에 있으면 해당 YAML이 우선하므로, 환경 변수만 사용할 때는 로컬 파일을 제거하거나 항목을 생략하세요.
3. PowerShell에서 실행합니다.

   ```powershell
   .\gradlew.bat bootRun
   ```

4. `http://localhost:8080`에 접속합니다. 기본적으로 127.0.0.1:8080에 바인딩됩니다.

키 없이도 화면을 열 수 있으며 발급 버튼은 설정 완료 후 활성화됩니다.
설정 변경 후에는 서버를 재시작합니다. 키와 로컬 DB는 `.gitignore`에 포함되어 있습니다.

## 테스트 순서

1. **가상계좌 발급**에서 주문명, 금액, 구매자 정보, 은행, 입금 기한을 입력합니다.
2. 서버가 주문을 먼저 저장한 후 `POST /payments/{paymentId}/instant`를 호출합니다.
3. `GET /payments/{paymentId}`를 호출하여 계좌번호, 예금주, 입금 기한, 상태를 가져옵니다.
4. **PortOne 콘솔 → 테스트 결제내역 → 해당 결제 상세 → 테스트 입금웹훅통지**로 입금 통지를 테스트할 수 있습니다. 이 동작은 PortOne의 임의 입금 통지이므로 토스페이먼츠의 입금 처리·웹훅 발송까지 검증한 것으로 간주하지 않습니다.
   토스페이먼츠에서 시작하는 입금 이벤트를 확인하려면 **토스페이먼츠 개발자센터 → 테스트 결제내역**에서 해당 결제의 입금을 처리합니다. 테스트 MID에 대한 조회 권한이 필요할 수 있습니다. 공용 테스트 MID라 내역에 접근할 수 없다면 본인 계정의 테스트 채널을 준비하세요.
5. 상세 화면에서 **입금 상태 조회**를 누릅니다. PortOne 조회 결과가 `PAID`이면 **입금 완료**로 표시됩니다.

테스트 가상계좌는 실제 송금할 수 없습니다. 앱 안에 입금 완료를 강제로 만드는 버튼은 없으며, 실제 PortOne 조회 결과만 반영합니다.
포트원/PG 간 테스트 입금 이벤트 전달까지 지연이 있으면 잠시 후 재조회하세요.
현금영수증 신청, 에스크로, 환불, 고정식 계좌는 이 데모의 범위에 포함하지 않았습니다.

## 웹훅 (선택)

입금 알림은 **토스페이먼츠 → PortOne → 데모 서버** 순서로 전달됩니다. 등록 위치마다 수신 URL이 다릅니다.

| 등록 위치 | 받는 서버 | URL / 이벤트 |
| --- | --- | --- |
| 토스페이먼츠 개발자센터의 해당 MID 웹훅 | PortOne | `https://tx-gateway-service.prod.iamport.co/virtual-account/webhook-event/tosspayments` / `DEPOSIT_CALLBACK` |
| PortOne 콘솔 V2 테스트 결제알림 | 이 데모 | `https://공개주소/webhooks/portone` / `Transaction.Paid` 등 |

토스페이먼츠 신모듈의 PG→PortOne 설정은 공식 가상계좌 입금통보 설정 가이드에 따릅니다.
공용 테스트 MID의 설정은 본인 토스페이먼츠 상점의 설정과 다를 수 있습니다. 앱의 실제 채널에 연결된 MID를 확인하세요.
PortOne 콘솔의 임의 테스트 입금은 PortOne→데모 구간을 확인하는 용도로 사용합니다. 토스페이먼츠 전송 기록이 비었다는 사실만으로 데모의 웹훅 장애라고 판단할 수 없습니다.

로컬에서는 상세 화면의 상태 조회 버튼만으로도 확인 가능합니다. 입금 상태를 서버에 자동 반영하려면:

1. 실행 중인 로컬 서버를 공개 HTTPS URL로 노출합니다(예: 사용 중인 터널 도구).
2. PortOne 콘솔의 V2 **테스트** 결제알림 설정에 `https://공개주소/webhooks/portone`을 등록합니다.
3. 웹훅 버전은 **2024-04-25**, Content-Type은 **application/json**을 사용합니다.

새로 발급하는 계좌는 `portone.webhook-url` (환경 변수 `PORTONE_WEBHOOK_URL`)을 설정하면
발급 요청의 `noticeUrls`에 공개 수신 주소를 명시합니다. 비우면 기존처럼 PortOne 콘솔 설정을 따릅니다.
주소는 `https://공개주소/webhooks/portone` 전체를 입력합니다. 터널 주소가 바뀌면 함께 수정하세요.
이 설정을 바꿔도 기존 결제의 수신 주소가 자동 변경되지는 않으므로 기존 건은 콘솔 설정/재전송 대상 URL도 확인하세요.

### 테스트 입금 → 서버 입금 완료 확인

1. 공개 수신 주소와 콘솔의 V2 테스트 웹훅 버전/JSON 설정을 확인하고 서버를 재시작합니다.
2. 앱에서 계좌를 발급한 뒤 PortOne 콘솔의 같은 결제에 테스트 입금 통지를 실행합니다.
3. 서버는 `Transaction.Paid`를 수신하면 API로 재조회하고, 검증한 `PAID` 상태와 입금 시각을 DB에 저장합니다. 수동 상태 조회 버튼은 누를 필요가 없습니다.
4. 상세 화면을 새로고침하면 **입금 완료**, **마지막 웹훅 반영**, **웹훅 이벤트: Transaction.Paid**가 표시됩니다.

수동 조회로만 갱신한 경우에는 웹훅 반영 시각을 기록하지 않습니다.
콘솔 로그의 `PortOne 웹훅 수신` / `처리 완료` / `무시` / `처리 실패`로 수신 여부와 원인을 구분합니다.
입금 통지 직후 API 조회가 아직 입금 대기이거나 조회/검증이 실패하면 502로 응답하여 재전송을 요청합니다.
이후 취소된 결제는 최신 조회 상태인 취소로 유지합니다. 중복/늦은 알림도 항상 최신 API 상태를 기준으로 처리합니다.

`localhost`와 `127.0.0.1`은 PortOne 서버가 이 PC로 접근할 수 있는 주소가 아닙니다. 터널의 공개 URL을 등록해야 합니다.
등록 후 PortOne 콘솔에서 호출 테스트 및 해당 결제의 웹훅 전송 결과를 확인하세요. 수신 성공은 204이며, 실제 주문의 알림 처리에 성공하면 DB 상태가 갱신됩니다.
일반 호출 테스트의 임의 주문 ID는 이 데모에서 무시될 수 있으므로, 실제 발급한 결제 건으로 상태 반영을 확인하세요.

이 데모는 공식 가이드의 검증 전략 중 **웹훅 본문을 신뢰하지 않고 PortOne API를 재조회하는 방식**을 사용합니다. 별도 웹훅 시크릿은 사용하지 않습니다.
알림에 있는 상점/주문이 자체 DB와 일치할 때만 조회하며, API 응답의 ID·상점·토스 채널·금액·KRW·가상계좌 정보를 검증합니다.
웹훅의 `Transaction.Paid` 문자열만으로 완료 처리하지 않습니다. 알 수 없는 이벤트와 주문은 204로 무시합니다.
조회나 검증 실패는 502를 반환하여 PortOne 재전송을 허용합니다.
웹훅은 DB만 갱신하므로 화면에는 새로고침 후 반영됩니다.

## 저장 및 오류 복구

- H2 파일 DB `./data/virtual-account-demo`를 사용하므로 서버 재시작 후에도 내역이 유지됩니다.
- 발급 전에 주문을 커밋하므로 타임아웃이나 웹훅 선도착에도 주문을 찾을 수 있습니다.
- 같은 발급 폼의 중복 제출은 동일한 결제 ID를 사용하며 발급 API를 다시 호출하지 않습니다.
- 발급 오류/타임아웃은 확정 실패로 간주하지 않고 **발급 확인 필요**로 남깁니다. 새로 발급하기 전에 해당 주문의 상태를 먼저 조회하세요. 프로세스가 중단되어 **발급 요청 중**으로 남은 경우도 조회로 복구합니다.
- 갱신 시 DB 잠금과 PortOne `updatedAt` 비교로 오래된 응답의 상태 덮어쓰기를 막습니다.
- 가상계좌 기한이 지났다는 이유만으로 입금 완료/실패를 자체 추정하지 않습니다. 최신 상태와 기한을 함께 확인하세요.
- 기본 폼에는 Spring Security CSRF 보호가 적용되며, 웹훅 경로만 예외입니다.
- 로그인 없는 로컬 테스트 앱입니다. 공개 서버로 운영하려면 사용자 인증·주문 접근 권한·요청 제한을 추가해야 합니다.

## 검증

```powershell
.\gradlew.bat test
.\gradlew.bat bootJar
```

자동 테스트는 격리된 H2 메모리 DB와 모의 PortOne 응답을 사용하며 실제 결제 API나 로컬 키를 사용하지 않습니다.
SSR 렌더링, CSRF, 입력 검증, 중복 발급 방지, 요청/응답 매핑, 입금 동기화, 위조 웹훅,
금액/상점/통화/채널 불일치, 재전송, 타임아웃 복구, 오래된 응답 무시를 확인합니다.

## 공식 문서

- [PortOne V2 결제 API: 수기 결제 및 결제 단건 조회](https://developers.portone.io/api/rest-v2/payment)
- [PortOne 토스페이먼츠 연동 가이드](https://developers.portone.io/opi/ko/integration/pg/v2/tosspayments)
- [PortOne 웹훅 연동 및 API 재조회 검증](https://developers.portone.io/opi/ko/integration/webhook/readme-v2)
- [PortOne 가상계좌 테스트 입금 통지](https://help.portone.io/content/vbank-test-guide)
- [PG별 가상계좌 입금통보 설정](https://developers.portone.io/opi/ko/integration/virtual-account/readme)
- [토스페이먼츠 FAQ: 테스트 가상계좌 입금](https://docs.tosspayments.com/resources/faq)
