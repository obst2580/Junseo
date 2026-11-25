# 신화리 온라인

충남 논산시 연무읍 신화리를 배경으로 한 블랙코미디 온라인 게임(브라우저, 3인칭) 작업 저장소.

- `DESIGN.md` — 기획·결정 사항 전체
- `lookdev2/` — 승인된 그래픽 룩데브 (three.js, 아티팩트로 바로 배포 가능한 형태)
- `blender/` — Blender 헤드리스 빌드 스크립트 (룩데브 씬, MakeHuman 인물 `build_human.py`)
- `humans/` — 인물 패킹(`pack.py`), 피부 디테일 생성(`skin_detail.py`), 뷰어(`pub/`)
- `characters/` — 주민 명부(캐릭터 바이블)
- `mapdata/` — 맵 참고 데이터

인물 빌드: Blender 4.2 + MPFB 2(MakeHuman, CC0 에셋) →
`blender -b --factory-startup -P blender/build_human.py -- <preset> humans/<preset>/<preset>.gltf` →
`python3 humans/pack.py humans/<preset> <preset> humans/pub`
