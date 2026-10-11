--!strict
-- 지형 격자 디코더. tools/mapdata/terrain.py의 encode_grid와 같은 규칙을 따른다.
--
-- base64를 풀고, 행마다 토큰(가변 길이 정수)을 읽는다.
--   토큰이 짝수: 리터럴. 차분 = 지그재그 복원(토큰 / 2), 값 = 이전 값 + 차분
--   토큰이 홀수: 반복. 이전 값을 (토큰 - 1) / 2 + 2번 반복
-- 각 행은 이전 값 0에서 시작한다.
--
-- 로블록스 서버와 맵 생성기(Lune)가 함께 쓴다.

local GridCodec = {}

local ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
local LOOKUP: { [number]: number } = {}
for i = 1, #ALPHABET do
	LOOKUP[string.byte(ALPHABET, i)] = i - 1
end
LOOKUP[string.byte("=")] = 0

function GridCodec.base64Decode(s: string): buffer
	local len = #s
	local pad = 0
	if len > 0 and string.sub(s, len, len) == "=" then
		pad += 1
	end
	if len > 1 and string.sub(s, len - 1, len - 1) == "=" then
		pad += 1
	end
	local outLen = (len // 4) * 3 - pad
	local out = buffer.create(outLen)
	local o = 0
	for i = 1, len, 4 do
		local a, b, c, d = string.byte(s, i, i + 3)
		local n = LOOKUP[a] * 262144 + LOOKUP[b] * 4096 + LOOKUP[c :: number] * 64 + LOOKUP[d :: number]
		if o < outLen then
			buffer.writeu8(out, o, n // 65536)
		end
		if o + 1 < outLen then
			buffer.writeu8(out, o + 1, (n // 256) % 256)
		end
		if o + 2 < outLen then
			buffer.writeu8(out, o + 2, n % 256)
		end
		o += 3
	end
	return out
end

export type Grid = {
	nx: number,
	nz: number,
	kind: "i16" | "u8",
	data: buffer,
}

-- kind가 "i16"이면 값마다 2바이트(부호 있음), "u8"이면 1바이트
-- yieldFn을 주면 64줄마다 불러서 오래 걸리는 작업 중에 양보한다
function GridCodec.decodeGrid(s: string, nx: number, nz: number, kind: "i16" | "u8", yieldFn: (() -> ())?): Grid
	local src = GridCodec.base64Decode(s)
	local size = if kind == "i16" then 2 else 1
	local out = buffer.create(nx * nz * size)
	local pos = 0
	local idx = 0
	for row = 1, nz do
		if yieldFn and row % 64 == 0 then
			yieldFn()
		end
		local prev = 0
		local i = 0
		while i < nx do
			local v = 0
			local mul = 1
			while true do
				local byte = buffer.readu8(src, pos)
				pos += 1
				v += (byte % 128) * mul
				if byte < 128 then
					break
				end
				mul *= 128
			end
			if v % 2 == 1 then
				local run = (v - 1) // 2 + 2
				for _ = 1, run do
					if size == 2 then
						buffer.writei16(out, idx * 2, prev)
					else
						buffer.writeu8(out, idx, prev)
					end
					idx += 1
				end
				i += run
			else
				local z = v // 2
				local d = if z % 2 == 0 then z // 2 else -((z + 1) // 2)
				prev += d
				if size == 2 then
					buffer.writei16(out, idx * 2, prev)
				else
					buffer.writeu8(out, idx, prev)
				end
				idx += 1
				i += 1
			end
		end
	end
	assert(pos == buffer.len(src), "지형 데이터가 손상되었습니다 (남은 바이트가 있음)")
	return { nx = nx, nz = nz, kind = kind, data = out }
end

function GridCodec.get(grid: Grid, ix: number, iz: number): number
	if ix < 0 then
		ix = 0
	elseif ix >= grid.nx then
		ix = grid.nx - 1
	end
	if iz < 0 then
		iz = 0
	elseif iz >= grid.nz then
		iz = grid.nz - 1
	end
	local idx = iz * grid.nx + ix
	if grid.kind == "i16" then
		return buffer.readi16(grid.data, idx * 2)
	end
	return buffer.readu8(grid.data, idx)
end

return GridCodec
