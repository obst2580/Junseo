"""만든 템플릿을 서버에 올린다 → 그 순간부터 모든 사용자의 「오늘 템플릿」에 뜬다 (앱 업데이트 없이).

  1) make_templates.py 에 새 템플릿을 추가하고 그림을 만든다
       python3 make_templates.py out > out/meta.json
  2) 올린다 (같은 id 로 다시 올리면 그림 · 칸이 통째로 바뀐다)
       python3 upload_template.py --api https://api.junseo.app --token "$JUNSEO_ADMIN_TOKEN" \\
           --dir out --id subway --name "지하철 광고" --color "#151515"
  3) 내리기 (목록에서만 빠진다. 이미 받아 둔 폰에서 깨지지 않게 그림은 남는다)
       python3 upload_template.py --api ... --token ... --id subway --hide

out 폴더에는 <id>.jpg(또는 .png) 바탕, <id>-overlay.png 앞장(없어도 됨), meta.json 이 있어야 한다.
--order 가 크면 목록 앞에 온다 (같으면 최근에 올린 것이 앞).
표준 라이브러리만 쓴다.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
import uuid


def multipart(fields, files):
    boundary = uuid.uuid4().hex
    out = bytearray()
    for name, value in fields.items():
        out += f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n'.encode()
        out += value.encode('utf-8') + b'\r\n'
    for name, (filename, data, mime) in files.items():
        out += f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n'.encode()
        out += data + b'\r\n'
    out += f'--{boundary}--\r\n'.encode()
    return bytes(out), f'multipart/form-data; boundary={boundary}'


def call(method, url, token, body=None, content_type=None):
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header('X-Admin-Token', token)
    if content_type:
        req.add_header('Content-Type', content_type)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {'message': raw.decode('utf-8', 'replace')[:300]}


def main():
    ap = argparse.ArgumentParser(description='템플릿을 서버에 올리거나 내린다')
    ap.add_argument('--api', required=True, help='서버 주소 (예: https://api.junseo.app)')
    ap.add_argument('--token', default=os.environ.get('JUNSEO_ADMIN_TOKEN'), help='관리자 토큰 (기본: $JUNSEO_ADMIN_TOKEN)')
    ap.add_argument('--id', required=True, help='템플릿 id (영문 소문자 · 숫자 · -)')
    ap.add_argument('--hide', action='store_true', help='목록에서 내리기')
    ap.add_argument('--dir', help='make_templates.py 가 그림과 meta.json 을 만든 폴더')
    ap.add_argument('--name', help='앱에 보일 이름 (30자까지)')
    ap.add_argument('--color', help='그림이 뜨기 전 바탕색 #rrggbb')
    ap.add_argument('--order', type=int, default=0, help='클수록 목록 앞')
    a = ap.parse_args()
    if not a.token:
        sys.exit('관리자 토큰이 없어요 (--token 또는 JUNSEO_ADMIN_TOKEN)')
    base = a.api.rstrip('/') + '/api/admin/templates/' + a.id

    if a.hide:
        status, body = call('DELETE', base, a.token)
        print('내렸어요' if status == 204 else f'실패 {status}: {body}')
        sys.exit(0 if status == 204 else 1)

    if not (a.dir and a.name and a.color):
        sys.exit('올릴 때는 --dir, --name, --color 가 필요해요')
    metas = json.load(open(os.path.join(a.dir, 'meta.json'), encoding='utf-8'))
    if a.id not in metas:
        sys.exit(f'meta.json 에 {a.id} 가 없어요 (있는 것: {", ".join(metas)})')
    m = metas[a.id]
    bg_path = next((os.path.join(a.dir, a.id + ext) for ext in ('.jpg', '.png') if os.path.exists(os.path.join(a.dir, a.id + ext))), None)
    if not bg_path:
        sys.exit(f'{a.dir} 에 {a.id}.jpg 바탕 그림이 없어요')
    files = {'background': (os.path.basename(bg_path), open(bg_path, 'rb').read(), 'image/png' if bg_path.endswith('.png') else 'image/jpeg')}
    ov_path = os.path.join(a.dir, a.id + '-overlay.png')
    if os.path.exists(ov_path):
        files['overlay'] = ('overlay.png', open(ov_path, 'rb').read(), 'image/png')
    spec = {'name': a.name, 'width': m['width'], 'height': m['height'], 'backgroundColor': a.color, 'slots': m['slots'], 'sortOrder': a.order}
    body, ctype = multipart({'meta': json.dumps(spec, ensure_ascii=False)}, files)
    status, res = call('PUT', base, a.token, body, ctype)
    if status != 200:
        sys.exit(f'실패 {status}: {res.get("message") if isinstance(res, dict) else res}')
    print(f'올렸어요: {res["name"]} (v{res["version"]}, 칸 {len(res["slots"])}개, 필요 기능 {", ".join(res["requires"]) or "없음"})')
    print(f'  바탕 {a.api.rstrip("/")}{res["backgroundUrl"]}')
    if res.get('overlayUrl'):
        print(f'  앞장 {a.api.rstrip("/")}{res["overlayUrl"]}')


if __name__ == '__main__':
    main()
