# Architecture Constraints

DO NOT:
- replace websocket architecture
- remove Redis
- merge AI services
- replace ArcFace
- remove async processing
- rewrite authentication flow
- remove scheduler workflows

Maintain:
- Spring Boot backend
- Python AI services
- Gunicorn workers
- WebSocket streaming
- Redis OTP cache
- Banking transaction workflow

Always:
- analyze before editing
- preserve architecture
- avoid unnecessary refactors
- avoid changing API contracts