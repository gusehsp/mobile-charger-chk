# 갤럭시 충전 모니터 (Galaxy Charging Monitor)

갤럭시 S24 / S25 / S26 시리즈의 실제 배터리 센서(전압·전류·전력·온도)를 실시간으로 측정하고, 유선 충전 기준 안내 페이지를 함께 제공하는 안드로이드 앱 프로젝트입니다.

## APK 파일 만드는 방법 (2가지 중 선택)

### 방법 1. Android Studio 설치 없이 바로 APK 받기 (GitHub Actions)
1. GitHub에 새 저장소(Repository)를 만들고 이 압축 파일의 내용물을 업로드합니다.
2. GitHub 상단 **Actions** 탭 → **Build Android APK** 실행을 확인합니다. (약 2~3분 소요)
3. 완료 후 하단 **Artifacts**에서 `GalaxyChargingMonitor-APK`를 클릭해 `app-debug.apk`를 다운로드하고 스마트폰에 설치합니다.

### 방법 2. Android Studio에서 직접 빌드
1. Android Studio에서 이 폴더(`GalaxyChargingMonitor`)를 엽니다.
2. Gradle 동기화가 끝나면 상단 메뉴 **Build → Build Bundle(s) / APK(s) → Build APK(s)** 클릭.
3. 생성 경로: `app/build/outputs/apk/debug/app-debug.apk`
