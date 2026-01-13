# PeakGuard

Redis 대기열(Queue) + 동시성 제어(정원/학점/시간표 충돌) 기반의 **수강신청 웹 서비스**입니다.  
대규모 동시 접속 상황에서도 시스템이 안정적으로 동작하도록 **대기열 제어** 와 **수강신청 동시성 처리** 를 중심으로 구현했습니다.



- [1. 프로젝트 소개](#1-프로젝트-소개)
- [주요 구현 포인트](#주요-구현-포인트)
- [2. 아키텍쳐](#2-아키텍쳐)
- [3. 개발 환경](#3-개발-환경)
- [4. 프로젝트 구조](#4-프로젝트-구조)
- [5. UI 구성 및 기능](#5-ui-구성-및-기능)
- [6. 배포(진행 중)](#6-배포진행-중)


<br>

## 1. 프로젝트 소개
- 수강신청은 특정 시간대에 동시 접속이 폭증하며, **DB 락 경합/정원 초과/요청 폭주**로 장애가 쉽게 발생합니다.
- 이를 해결하기 위해 **Redis 기반 대기열(Queue)로 유입을 제어**하고, 수강신청 로직에는 **정원/학점/시간표 충돌**을 검증하는 동시성 안전장치를 적용했습니다.
- 인증은 **JWT 기반**이며, RefreshToken은 **Redis에 저장**하여 재발급/로그아웃 정책을 명확히 했습니다.

<br>


<br>

## 주요 구현 포인트
- **PeakGuard Queue (Redis)**
  - `queue:waiting`(ZSET) / `queue:active`(ZSET) 기반으로 대기 → 입장 상태를 관리합니다.
  - 초당 입장 허용량(throughput)과 최대 활성 사용자 수(max-active-users)로 **트래픽 유입을 제어**합니다.
  - 스케줄러가 대기열 상위 N명을 주기적으로 입장(promote)시키는 구조입니다.

- **Enrollment 동시성 제어 (lock-mode)**
  - 정원 초과 방지: `PESSIMISTIC(lecture row lock)` 또는 `ATOMIC_UPDATE(조건부 업데이트)`로 처리합니다.
  - 학점 제한(기본 20학점) 및 시간표 충돌 검사로 비즈니스 룰을 보호합니다.

- **JWT 인증 + Redis RefreshToken**
  - Access/Refresh 토큰 기반 인증 흐름을 구성하고,
  - RefreshToken은 Redis에 저장하여 로그아웃/재발급 정책을 정리했습니다.

- **DB 제약조건 명문화 + Flyway**
  - `Enrollment(student_id, lecture_id) UNIQUE`, FK, 인덱스 등 코드 전제를 DB로 고정했습니다.

<br>

## 2. 아키텍쳐
### 아키텍쳐
- (추가 예정) 배포 구성도 및 요청 흐름 다이어그램 정리 예정

- **Frontend**: Thymeleaf Template + Vanilla JS(fetch)
- **Backend**: Spring Boot + Spring Security + JWT
- **DB**: MySQL (Flyway Migration)
- **Cache/Queue**: Redis (대기열, RefreshToken 저장)


<br>

## 3. 개발 환경
- **IDE**
  - IntelliJ IDEA

- **Frontends**
  - <img src="https://img.shields.io/badge/html5-E34F26?style=flat-square&logo=html5&logoColor=white"/>
  - <img src="https://img.shields.io/badge/css3-1572B6?style=flat-square&logo=css3&logoColor=white"/>
  - <img src="https://img.shields.io/badge/javascript-F7DF1E?style=flat-square&logo=javascript&logoColor=white"/>

- **Backends**
  - <img src="https://img.shields.io/badge/Java-007396?style=flat-square&logo=java&logoColor=white"/>
  - <img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white"/>
  - <img src="https://img.shields.io/badge/Spring%20Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white"/>
  - <img src="https://img.shields.io/badge/MyBatis-000000?style=flat-square&logoColor=white"/>

- **Database/Cache**
  - <img src="https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white"/>
  - <img src="https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white"/>

- **Migration**
  - <img src="https://img.shields.io/badge/Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white"/>



```md
---
## 4. 프로젝트 구조
📦src/main
┣ 📂java/com/sku
┃ ┣ 📂auth # 로그인/로그아웃/재발급(JWT)
┃ ┣ 📂queue # Redis 대기열(Join/Status/Reset/Scheduler)
┃ ┣ 📂lecture # 강의 조회/필터/시간 정보
┃ ┣ 📂cart # 장바구니 담기/삭제/일괄 신청
┃ ┣ 📂enrollment # 수강신청/취소 + 동시성 제어(lock-mode)
┃ ┣ 📂timetable # 시간표 조회
┃ ┗ 📂common # 예외/응답/인터셉터/시큐리티 공통
┗ 📂resources
┣ 📂templates # Thymeleaf 화면
┣ 📂static # css/js 정적 리소스
┗ 📂db/migration # Flyway 마이그레이션
```

<br>

---

## 5. UI 구성 및 기능

### 기능

#### 로그인
<img width="1211" height="519" alt="image" src="https://github.com/user-attachments/assets/9e844c42-7fe9-4a58-9b3f-487d3095d2a4" />


- 학번/비밀번호 기반 로그인
- 로그인 실패(자격 증명 실패)는 **401**로 내려 프론트에서 “아이디 또는 비밀번호가 올바르지 않습니다.”로 안내하도록 개선했습니다.
- 로그인 후 서비스 이용 시, 대기열 정책에 따라 자동으로 **대기열 진입/강의 조회 페이지 이동** 흐름이 이어집니다.

#### 회원가입
<img width="1212" height="785" alt="image" src="https://github.com/user-attachments/assets/69da1d53-32aa-4023-9fda-a8f38331ddf6" />


- 학생 계정 생성(학번/이름/비밀번호 등)
- 기본 입력값 검증(@Valid 기반) 및 중복 학번 방지(DB UNIQUE)

#### 접속 대기열
<img width="604" height="684" alt="image" src="https://github.com/user-attachments/assets/c099f179-299e-44f5-9b9e-5434c281afd1" />


- 대기열 페이지에서 대기 순번 및 예상 대기 시간을 표시합니다.
- 대기열에 진입하고, 폴링으로 상태를 갱신합니다.
- 대기 순서가 되면 자동으로 **강의 조회 페이지(`/lectures`)로 이동**합니다.

#### 강의 조회
<img width="835" height="801" alt="image" src="https://github.com/user-attachments/assets/0964fd35-c03b-432c-81f6-33dd87a717bc" />


- 강의명/교수명 검색, 분반/학점/요일/시간대 등 조건 필터 제공
- 강의 리스트에서
  - **장바구니 담기**
  기능을 제공합니다.

#### 장바구니
<img width="750" height="844" alt="image" src="https://github.com/user-attachments/assets/4eaabe29-1642-4601-92fc-761243c3f203" />


- 담은 강의 목록 조회 및 선택 삭제
- 선택한 강의를 **일괄 수강신청**할 수 있으며, 강의별 성공/실패 결과를 분리해서 안내합니다.
- 장바구니 합계 학점을 표시하여, 수강신청 전 사전 점검이 가능합니다.

#### 수강신청 내역
<img width="702" height="891" alt="image" src="https://github.com/user-attachments/assets/e59b3e15-fa0a-407d-9927-6cc7bfeba4bc" />


- 내가 신청한 강의 목록 조회
- 수강 취소는 기간 정책(설정값 기반) 내에서만 허용되며, 취소 시 정원이 감소하도록 처리합니다.

#### 내 시간표
<img width="1028" height="884" alt="image" src="https://github.com/user-attachments/assets/7704d47a-76c3-49be-9bfc-4834415c4b07" />


- 신청한 강의의 LectureTime 정보를 기반으로 주간 시간표를 구성합니다.
- 겹치는 시간대는 신청 단계에서 사전에 차단합니다(시간표 충돌 검사).

<br>

### 운영/관리자 기능(API)
> 운영 안전장치 성격의 기능으로, UI에 노출하지 않고 API로만 제어합니다.

- **Queue Reset (ADMIN 전용)**
  - `POST /api/queue/reset`
  - 비로그인: 401 / 일반 유저: 403 / ADMIN: 200

- **Queue Throughput 설정 키 일관성**
  - throughput 설정/조회/리셋 키를 상수로 통일하여 운영 디버깅 비용을 줄였습니다.

- **민감정보 로그 마스킹**
  - queueToken 등 토큰 원문 로그 출력 금지(마스킹 처리)
<br>
 
## 6. 배포(진행 중)

**URL**
- http://20.249.176.158/

**현재 상태**
- 배포는 완료되어 기본 화면/흐름은 접근 가능합니다.
- 보안/운영 안정화 작업을 PR 단위로 순차 반영 중입니다.

**테스트 계정**
- id: admin  
- pwd: 12341234
