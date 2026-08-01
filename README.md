# 전시기록 Android MVP

전시 관람일, 포스터, 한줄평, 상세 감상, 작품 사진, 작가, 태그를 로컬에 저장하는 Android 앱 프로젝트다.

## 구현된 범위

- 전시 등록 및 최근 전시 목록
- 관람일 기준 월간 달력
- 전시 상세와 작품 빠른 등록
- 작가 및 태그 데이터 구조
- 전시·작가·태그 아카이브
- 전시 검색
- Room 로컬 데이터베이스
- 갤러리 이미지의 앱 내부 저장
- M4A 녹음을 위한 recorder 기반 코드
- JSON ZIP 백업 및 전체 교체 복원
- Hilt, Compose, Room 기반 구조

## 아직 보완할 부분

- 실제 녹음 UI와 재생 UI 연결
- 카메라 직접 촬영
- 작품 상세 수정/삭제 화면
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

Gradle Wrapper JAR는 이 생성 환경에서 내려받지 못했으므로, Android Studio가 사용하는 Gradle로 먼저 동기화하거나 `gradle wrapper --gradle-version 8.13`을 실행해 생성한다.

## 패키지

`com.example.exhibitionarchive`

## 주의

현재 코드는 첫 실행 가능한 MVP 초안이다. 이 환경에는 Android SDK가 없어 실제 APK 빌드까지 검증하지 못했다. 첫 Gradle Sync에서 의존성 버전 충돌이 발생하면 Android Studio가 제안하는 호환 버전으로 맞춰야 한다.
