"""
OSM (api.openstreetmap.org /map, XML) -> compact city data for the game.
  python3 build_city.py osm.xml ../game_pub/city.json
Local metres: x east, y north, origin at the bbox centre (강남역 사거리 근처).
Data © OpenStreetMap contributors (ODbL).
"""
import sys, json, math, re
import xml.etree.ElementTree as ET
src, dst = sys.argv[1:3]
t = ET.parse(src).getroot()
b = t.find('bounds'); la0, la1, lo0, lo1 = (float(b.get(k)) for k in ('minlat', 'maxlat', 'minlon', 'maxlon'))
LAT0, LON0 = (la0 + la1) / 2, (lo0 + lo1) / 2
KX, KY = math.cos(math.radians(LAT0)) * 111320, 110540
def xy(lat, lon): return [round((lon - LON0) * KX, 2), round((lat - LAT0) * KY, 2)]
nodes, ntags = {}, {}
for n in t.iter('node'):
    nodes[n.get('id')] = xy(float(n.get('lat')), float(n.get('lon')))
    tg = {x.get('k'): x.get('v') for x in n.iter('tag')}
    if tg: ntags[n.get('id')] = tg
ways = []
for w in t.iter('way'):
    tg = {x.get('k'): x.get('v') for x in w.iter('tag')}; pts = [nodes[nd.get('ref')] for nd in w.iter('nd') if nd.get('ref') in nodes]
    ways.append((tg, pts, [nd.get('ref') for nd in w.iter('nd')]))
half = [(lo1 - lo0) / 2 * KX, (la1 - la0) / 2 * KY]
def num(s):
    m = re.match(r'[\d.]+', s or ''); return float(m.group()) if m else None
def area(p):
    return abs(sum(p[i][0] * p[i - 1][1] - p[i - 1][0] * p[i][1] for i in range(len(p)))) / 2
# ---- buildings
out_b = []
LM = {'8311185': 'samsung_c', '8311184': 'samsung_b', '8311183': 'samsung_wing', '504227059': 'samsung_a', '301605795': 'gt', '19640833': 'monaco', '379232116': 'meritz', '19639969': 'aplus'}
for (tg, p, _), wid in zip(ways, [w.get('id') for w in t.iter('way')]):
    if 'building' not in tg or len(p) < 4: continue
    if tg.get('building') in ('roof', 'construction') or tg.get('location') == 'underground' or tg.get('layer', '0').startswith('-'): continue
    ring = p[:-1] if p[0] == p[-1] else p
    A = area(ring)
    if A < 12: continue
    lv = num(tg.get('building:levels')); h = num(tg.get('height'))
    if not h: h = (lv or (3 if A < 150 else 5 if A < 400 else 8)) * 3.4 + 1
    if not lv: lv = max(1, round(h / 3.4))
    kind = 'tower' if h >= 45 else 'office' if h >= 22 else 'shop'
    if tg.get('building') in ('apartments', 'residential') or '오피스텔' in tg.get('name', '') or '아파트' in tg.get('name', ''): kind = 'apart' if h >= 22 else 'villa'
    out_b.append({'p': ring, 'h': round(h, 1), 'lv': int(lv), 'name': tg.get('name', ''), 'kind': kind, 'shops': [], 'id': 'w' + wid, **({'lm': LM[wid]} if wid in LM else {})})
# ---- multipolygon buildings (relations): each closed outer ring becomes a building with the relation's tags
wayby = {w.get('id'): w for w in t.iter('way')}
for r in t.iter('relation'):
    tg = {x.get('k'): x.get('v') for x in r.iter('tag')}
    if tg.get('type') != 'multipolygon' or 'building' not in tg: continue
    rings = []
    for m in r.iter('member'):
        if m.get('type') != 'way' or m.get('role') != 'outer' or m.get('ref') not in wayby: continue
        p = [nodes[nd.get('ref')] for nd in wayby[m.get('ref')].iter('nd') if nd.get('ref') in nodes]
        if len(p) < 4 or p[0] != p[-1]: continue
        if area(p[:-1]) >= 12: rings.append(p[:-1])
    if not rings: continue
    lv = num(tg.get('building:levels')); h = num(tg.get('height'))
    rid = r.get('id')
    if rid in LM:   # a landmark: one entry, footprint = its biggest ring, every outer ring kept for the hand-built model
        rings.sort(key=area, reverse=True)
        out_b.append({'p': rings[0], 'rings': rings, 'h': round(h or 100, 1), 'lv': int(lv or 25), 'name': tg.get('name', ''), 'kind': 'tower', 'shops': [], 'id': 'r' + rid, 'lm': LM[rid]}); continue
    for ring in rings:
        A = area(ring); hh = h or (lv or (3 if A < 150 else 5 if A < 400 else 8)) * 3.4 + 1; ll = lv or max(1, round(hh / 3.4))
        out_b.append({'p': ring, 'h': round(hh, 1), 'lv': int(ll), 'name': tg.get('name', ''), 'kind': 'tower' if hh >= 45 else 'office' if hh >= 22 else 'shop', 'shops': [], 'id': 'r' + rid})
# ---- shops / amenities -> the building they sit in (or the nearest one)
def inside(pt, ring):
    x, y = pt; c = False
    for i in range(len(ring)):
        x1, y1 = ring[i]; x2, y2 = ring[i - 1]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1 + 1e-12) + x1: c = not c
    return c
def cen(r): return [sum(q[0] for q in r) / len(r), sum(q[1] for q in r) / len(r)]
pois = []
for nid, tg in ntags.items():
    cat = tg.get('amenity') or tg.get('shop')
    if not cat or not tg.get('name'): continue
    pt = nodes[nid]; best = None
    for bi, bd in enumerate(out_b):
        if inside(pt, bd['p']): best = bi; break
    if best is None:
        d = [(math.dist(pt, cen(bd['p'])), bi) for bi, bd in enumerate(out_b)]
        if d: dd, bi = min(d); best = bi if dd < 30 else None
    if best is not None and len(out_b[best]['shops']) < 8: out_b[best]['shops'].append([tg['name'], cat])
    pois.append({'xy': pt, 'name': tg['name'], 'cat': cat})
# ---- roads
WIDTH = {'motorway': 3.4, 'trunk': 3.4, 'primary': 3.3, 'secondary': 3.2, 'tertiary': 3.1, 'primary_link': 3.2, 'secondary_link': 3.1, 'busway': 3.4}
roads = []
for tg, p, _ in ways:
    hw = tg.get('highway')
    if not hw or len(p) < 2: continue
    if hw in ('steps', 'platform', 'corridor', 'elevator', 'proposed', 'construction'): continue
    if tg.get('tunnel') == 'yes' or tg.get('layer', '0').startswith('-'): continue
    lanes = num(tg.get('lanes'))
    if hw in WIDTH: w = (lanes or (6 if hw == 'primary' else 2)) * WIDTH[hw]
    elif hw in ('residential', 'unclassified', 'living_street'): w = 6.0
    elif hw == 'service': w = 4.5
    elif hw in ('footway', 'pedestrian', 'path', 'cycleway'): w = 2.5 if hw != 'pedestrian' else 6
    else: w = 4
    kind = 'walk' if hw in ('footway', 'pedestrian', 'path', 'cycleway') else 'road'
    roads.append({'pts': p, 'w': round(w, 1), 'kind': kind, 'hw': hw, 'lanes': int(lanes or 0), 'oneway': tg.get('oneway') == 'yes', 'name': tg.get('name', '')})
# ---- point features
feat = {'crossing': [], 'signals': [], 'bus_stop': [], 'subway': [], 'tree': []}
for nid, tg in ntags.items():
    pt = nodes[nid]
    if tg.get('highway') == 'crossing': feat['crossing'].append(pt)
    elif tg.get('highway') == 'traffic_signals': feat['signals'].append(pt)
    elif tg.get('highway') == 'bus_stop': feat['bus_stop'].append(pt + [tg.get('name', '')])
    elif tg.get('railway') == 'subway_entrance': feat['subway'].append(pt + [tg.get('ref', '')])
    elif tg.get('natural') == 'tree': feat['tree'].append(pt)
landuse = [{'p': p[:-1], 'kind': tg['landuse']} for tg, p, _ in ways if 'landuse' in tg and len(p) > 3]
# ---- crop: a small dense square around the 강남역 crossroads (args: cx cy half), recentred on it
if len(sys.argv) > 3:
    cx, cy, H = map(float, sys.argv[3:6]); M = 150   # the next blocks out stay as scenery (bg: no play there)
    inb = lambda p, m=0: abs(p[0] - cx) <= H + m and abs(p[1] - cy) <= H + m
    sh = lambda p: [round(p[0] - cx, 2), round(p[1] - cy, 2)]
    out_b = [dict(b, p=[sh(q) for q in b['p']], **({'rings': [[sh(q) for q in r] for r in b['rings']]} if 'rings' in b else {}), **({} if inb(cen(b['p'])) else {'bg': 1})) for b in out_b if inb(cen(b['p']), M)]
    def clip(a, b):   # Liang-Barsky against the box grown by M
        x0, y0, x1, y1 = cx - H - M, cy - H - M, cx + H + M, cy + H + M; dx, dy = b[0] - a[0], b[1] - a[1]; t0, t1 = 0, 1
        for p_, q_ in ((-dx, a[0] - x0), (dx, x1 - a[0]), (-dy, a[1] - y0), (dy, y1 - a[1])):
            if p_ == 0:
                if q_ < 0: return None
            else:
                t = q_ / p_
                if p_ < 0: t0 = max(t0, t)
                else: t1 = min(t1, t)
        return None if t0 > t1 else ([a[0] + dx * t0, a[1] + dy * t0], [a[0] + dx * t1, a[1] + dy * t1])
    nr = []
    for rd in roads:
        run = []
        for a_, b_ in zip(rd['pts'], rd['pts'][1:]):
            c_ = clip(a_, b_)
            if not c_: 
                if len(run) > 1: nr.append(dict(rd, pts=[sh(q) for q in run]))
                run = []; continue
            if run and run[-1] == c_[0]: run.append(c_[1])
            else:
                if len(run) > 1: nr.append(dict(rd, pts=[sh(q) for q in run]))
                run = [c_[0], c_[1]]
        if len(run) > 1: nr.append(dict(rd, pts=[sh(q) for q in run]))
    roads = nr
    feat = {k: [sh(v[:2]) + list(v[2:]) for v in vs if inb(v, 10)] for k, vs in feat.items()}
    pois = [dict(p_, xy=sh(p_['xy'])) for p_ in pois if inb(p_['xy'])]
    landuse = []
    half = [H, H]
out = {'origin': [LAT0, LON0], 'half': [round(half[0], 1), round(half[1], 1)], 'buildings': out_b, 'roads': roads, 'features': feat, 'pois': pois[:400], 'landuse': landuse,
       'credit': '지도 데이터 © OpenStreetMap contributors (ODbL)'}
json.dump(out, open(dst, 'w'), ensure_ascii=False, separators=(',', ':'))
print('buildings', len(out_b), 'roads', len(roads), {k: len(v) for k, v in feat.items()}, 'pois', len(pois), 'half', out['half'])
print('tallest', sorted(((bd['h'], bd['name']) for bd in out_b), reverse=True)[:8])
print('shops on buildings', sum(len(bd['shops']) for bd in out_b))
