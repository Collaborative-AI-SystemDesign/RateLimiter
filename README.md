# Rate Limiter

## 📋 프로젝트 개요
- **목표**: 5개의 Rate Limiter 알고리즘을 ServletFilter 기반으로 구현
- **아키텍처**: Factory Pattern + OncePerRequestFilter + Header 기반 사용자 ID 추출
- **핵심 기능**: HTTP 헤더에서 사용자 ID 추출 → Rate Limit 체크 → 허용/거부 응답
