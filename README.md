# 전시기록 Android MVP

전시 관람일, 포스터, 한줄평, 상세 감상, 작품 사진, 작가, 태그를 로컬에 저장하는 Android 앱 프로젝트다.

## 구현된 범위

- 전시 등록 및 최근 전시 목록
- 관람일 기준 월간 달력
- 전시 상세와 작품 빠른 등록
- 전시 수정·삭제 및 날짜 선택기
- 작품 상세, 수정·삭제와 여러 사진 관리
- 작가 및 태그 데이터 구조
- 전시·작가·태그 아카이브
- 전시 검색
- Room 로컬 데이터베이스
- 갤러리 이미지의 앱 내부 저장
- M4A 녹음을 위한 recorder 기반 코드
- JSON ZIP 백업 및 전체 교체 복원
- 앱 내부 파일의 교체·삭제 수명주기 관리
- Room 1→2 마이그레이션과 스키마 이력
- Hilt, Compose, Room 기반 구조

## 아직 보완할 부분

- 실제 녹음 UI와 재생 UI 연결
- 카메라 직접 촬영
- 백업에 이미지·음성 바이너리 포함
- 병합 복원
- DAO/UI 자동화 테스트 확대
- 화면 디자인 다듬기

## 실행

1. Android Studio에서 이 폴더를 연다.
2. JDK 17을 선택한다.
3. Android SDK 36을 설치한다.
4. Gradle Sync를 실행한다.
5. Android 8.0(API 26) 이상 기기 또는 에뮬레이터에서 실행한다.

명령줄에서는 JDK와 Android SDK 경로를 설정한 후 `./gradlew testDebugUnitTest assembleDebug`로 검증할 수 있다.

## 패키지

`com.example.exhibitionarchive`

## 주의

현재 버전은 `0.2.0`이다. 단위 테스트와 debug APK 빌드를 검증했으며 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성된다. 기존 `0.1.0` 데이터베이스는 Room 마이그레이션을 통해 보존된다.
