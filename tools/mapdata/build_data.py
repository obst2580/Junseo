"""맵 데이터 전체 생성: map/data/ 아래 roads.json, terrain.json, meta.json.

순서: (fetch.py로 원본을 받은 뒤)
  1. roads.py   실제 도로 → 게임용 도로망(2D)
  2. ramps.py   고가 진출입 램프
  3. terrain.py 지형 격자
  4. roadprofile.py 도로 높이(3D) + 게임 좌표
  5. meta.py    경계 벽·구역·랜드마크·스폰

    python build_data.py          # 단계별 캐시가 있으면 재사용
    python build_data.py --fresh  # 캐시를 지우고 처음부터
"""
import hashlib
import json
import os
import sys
import time

import common as C


def main():
    fresh = "--fresh" in sys.argv
    stage = os.path.join(C.CACHE, "stage")
    os.makedirs(stage, exist_ok=True)
    if fresh:
        for f in ("regions.pkl", "raw_ways.pkl", "areas.pkl", "sea.pkl", "terrain.pkl", os.path.join("stage", "network2d.json")):
            p = os.path.join(C.CACHE, f)
            if os.path.exists(p):
                os.remove(p)
    t0 = time.time()
    net_path = os.path.join(stage, "network2d.json")
    if not os.path.exists(net_path):
        import osmload
        import ramps
        import roads

        net = roads.build_network()
        net["ramps"] = ramps.build_ramps(net, osmload.project_ways(osmload.load_raw_ways()))
        json.dump(net, open(net_path, "w"))
        print(f"도로망 {len(net['edges'])}개 간선, 램프 {len(net['ramps'])}개 ({time.time() - t0:.0f}s)")
    net = json.load(open(net_path))

    import terrain

    terr = terrain.load()
    print(f"지형 {terr.nx}x{terr.nz} ({time.time() - t0:.0f}s)")

    import meta
    import roadprofile as prof

    roads3d = prof.build(net, terr)
    enc = terr.encoded()
    # 맵 버전: 지형·도로가 바뀌면 달라진다. 로블록스에서 구운 지형이 최신인지 확인하는 데 쓴다
    h = hashlib.sha1()
    for k in ("height", "waterTop", "material"):
        h.update(enc[k].encode())
    h.update(json.dumps(roads3d, sort_keys=True).encode())
    version = h.hexdigest()[:12]
    enc["version"] = version
    roads3d["version"] = version
    C.write_json("roads.json", roads3d)
    C.write_json("terrain.json", enc)
    m = meta.build(roads3d, terr)
    m["version"] = version
    C.write_json("meta.json", m)
    print("맵 버전", version)
    print(f"완료 ({time.time() - t0:.0f}s)")


if __name__ == "__main__":
    main()
