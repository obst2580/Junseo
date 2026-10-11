# 서울 인생모드 (로블록스)

서울특별시와 인천을 배경으로 한 로블록스 인생모드(RP) 게임 프로젝트입니다.

- 설계서: [docs/map-design.md](docs/map-design.md)
- 지금 단계: **1단계(맵 뼈대) 완료**. 지형·한강·바다, 맵 경계, 간선도로, 고가 고속도로, 진출입 램프, 한강 다리, 구(區) 구역이 들어 있습니다. 건물은 아직 없습니다.

## 게임 파일 열기

1. [`dist/SeoulRP.rbxl`](dist/SeoulRP.rbxl)을 내려받아 Roblox Studio에서 엽니다.
2. **지형 굽기 (처음 한 번):** Studio 메뉴에서 보기(View) → 명령줄(Command Bar)을 열고 아래 한 줄을 붙여 넣어 실행합니다.
   ```lua
   require(game.ServerScriptService.MapRuntime.TerrainBuilder).build({ yield = true })
   ```
   산·한강·바다 지형을 만들고 도로를 따라 땅을 깎고 채웁니다. 컴퓨터에 따라 1~몇 분 걸리며, 출력(Output) 창(View → Output)에 `[맵] 지형 10%`… 진행 상황이 나옵니다. `[맵] 지형 생성 끝`이 나오면 **저장**하세요.
   - 굽기 전에는 지형이 없어서 도로가 하늘에 떠 있는 것처럼 보입니다.
   - 굽지 않고 바로 실행(Play)해도 서버가 시작할 때 자동으로 만들지만, 그때마다 시간이 걸립니다.
   - 게임 파일을 새로 받으면(맵 버전이 바뀌면) 다시 구워야 합니다.
3. 스폰 위치는 서울시청 앞입니다. 편집 화면의 노란·파란 이름표(구 이름, 랜드마크)는 게임을 실행하면 사라집니다.

## 맵 다시 만들기

필요한 도구: [Rokit](https://github.com/rojo-rbx/rokit)으로 `rokit install` (Rojo, Lune). 지도 원본부터 다시 만들 때는 파이썬 3.11 이상과 `pip install -r tools/mapdata/requirements.txt`.

```sh
scripts/build.sh          # map/data/*.json → dist/SeoulRP.rbxl
scripts/build.sh --data   # 지도 원본을 내려받아 데이터부터 새로 만들기
lune run tests/terrain_builder_mock   # 지형 생성기 검사 (로블록스 없이)
```

## 구조

| 경로 | 내용 |
|---|---|
| `tools/mapdata/` | 지도 원본 받기(`fetch.py`)와 가공(`build_data.py`): 도로 단순화, 램프, 지형 격자, 도로 높이, 경계·구역 |
| `map/data/` | 가공된 맵 데이터 (`roads.json`, `terrain.json`, `meta.json`) |
| `map/generate.luau` | 데이터 → 로블록스 파트·모듈 생성 (Lune) |
| `src/server/` | 로블록스 서버 스크립트: 지형 생성기, 격자 디코더, 시작 스크립트 |
| `preview/` | 브라우저용 3D 미리보기 페이지 |
| `tests/` | 가짜 로블록스 환경에서 지형 생성기를 끝까지 돌려 보는 검사 |
| `dist/SeoulRP.rbxl` | Studio에서 여는 게임 파일 |

## 지도 데이터 출처

- 도로·물·토지이용: © OpenStreetMap contributors (ODbL). 게임 안 스폰 안내판에도 표시합니다.
- 지형 고도: AWS Terrain Tiles (Mapzen Terrarium)
