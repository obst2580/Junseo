-- 도로망 → 파트
--
-- 지상 도로(0층): 아스팔트 판 + 황색 복선 중앙선(+ 간선도로는 흰색 바깥 차선).
--   인도는 파트 대신 지형(보도블록 느낌의 Pavement 재질)으로 만든다.
--   지형 생성기가 Carve 속성을 보고 도로 아래를 채우고 위를 비운다.
-- 다리: 콘크리트 상판(인도 포함) + 아스팔트 + 난간 + 교각
-- 고가 고속도로(1층): 상판 + 양쪽 콘크리트 방호벽 + 중앙분리대 + 교각
-- 램프: 상판 + 방호벽 + 교각
--
-- 굽은 곳은 이음매 파트를 따로 두지 않고, 각 조각을 꺾인 각도만큼 늘려 바깥쪽 틈을 메운다.
-- 겹치는 부분이 깜빡이지 않도록 이웃한 조각끼리 높이를 0.02스터드씩 엇갈리게 둔다.

local roblox = require("@lune/roblox")
local Vector3 = roblox.Vector3
local B = require("./build")

local Roads = {}

local WALK = { primary = 10, secondary = 8, expressway = 0 }
local ROAD_T = 1.2
local BRIDGE_T = 2.4
local DECK_T = 2.6
local RAMP_T = 2.2
local PILLAR_STEP = 110
local RAMP_PILLAR_STEP = 55
local PIER_STEP = 90
local STAGGER = 0.02

local C, M = B.COLOR, B.MAT

local function v3(p)
	return Vector3.new(p[1], p[2], p[3])
end

local function flat(v)
	return Vector3.new(v.X, 0, v.Z)
end

local function turnAngle(a, b, c)
	local d1 = flat(b - a)
	local d2 = flat(c - b)
	if d1.Magnitude < 1e-6 or d2.Magnitude < 1e-6 then
		return 0
	end
	return math.acos(math.clamp(d1.Unit:Dot(d2.Unit), -1, 1))
end

local function lerp(a, b, t)
	return a + (b - a) * t
end

-- 꺾인 각 theta에서 중심선으로부터 halfSpan만큼 떨어진 가장자리 틈을 메우는 데 필요한 연장 길이
local function extension(theta: number, halfSpan: number): number
	if theta < 1e-3 then
		return 0
	end
	return math.min(halfSpan * 2, halfSpan * math.tan(theta / 2)) + 0.05
end

local stats = { parts = 0 }
local function count(n: number?)
	stats.parts += n or 1
end

-- 조각(piece)의 꺾임 각도 표: angles[i] = i번째 점에서 꺾인 각도 (양 끝은 0)
local function anglesOf(pts)
	local angles = table.create(#pts, 0)
	for i = 2, #pts - 1 do
		angles[i] = turnAngle(pts[i - 1], pts[i], pts[i + 1])
	end
	return angles
end

-- p1→p2를 양쪽으로 늘린 점 (높이 엇갈림 포함)
local function extended(p1, p2, ea: number, eb: number, dy: number)
	local d = p2 - p1
	local L = d.Magnitude
	local f = if L > 1e-6 then d / L else Vector3.new(0, 0, -1)
	local up = Vector3.new(0, dy, 0)
	return p1 - f * ea + up, p2 + f * eb + up
end

-- 지상 도로·다리·터널 (0층)
local function buildSurfaceEdge(folder, e, grid, trimA, trimB)
	local w = e.w
	local walk = WALK[e.cls] or 8
	local carveW = w + 2 * walk + 2
	local primary = e.cls == "primary"
	local total = 0
	for _, pc in e.pieces do
		for i = 1, #pc.pts - 1 do
			total += (v3(pc.pts[i + 1]) - v3(pc.pts[i])).Magnitude
		end
	end
	local s = 0
	local k = 0 -- 조각 번호 (높이 엇갈림용)
	for _, pc in e.pieces do
		local pts = {}
		for i, p in pc.pts do
			pts[i] = v3(p)
		end
		local angles = anglesOf(pts)
		local kind = pc.kind
		local pierAcc = PIER_STEP / 2
		for i = 1, #pts - 1 do
			local p1, p2 = pts[i], pts[i + 1]
			local L = (p2 - p1).Magnitude
			if L < 0.05 then
				continue
			end
			k += 1
			local dy = if k % 2 == 0 then STAGGER else 0
			local ta, tb = angles[i], angles[i + 1]
			local lift = 0
			if kind == "bridge" then
				local half = w / 2 + walk
				local a1, a2 = extended(p1, p2, extension(ta, half), extension(tb, half), dy)
				B.slab(folder, "다리상판", a1, a2, w + 2 * walk, BRIDGE_T, C.concrete, M.concrete, 0)
				lift = 0.05
				local r1, r2 = extended(p1, p2, extension(ta, w / 2), extension(tb, w / 2), dy + lift)
				B.slab(folder, "다리노면", r1, r2, w, 0.3, C.asphalt, M.asphalt, 0)
				for _, side in { -1, 1 } do
					local lat = side * (half - 0.4)
					local q1, q2 = extended(p1, p2, extension(ta, half), extension(tb, half), dy)
					B.along(folder, "난간", q1, q2, lat, 1.5, 0.8, 3, C.rail, M.metal, nil, 0)
				end
				count(4)
				local acc = pierAcc
				while acc < L do
					local q = lerp(p1, p2, acc / L)
					local bed = grid:height(q.X, q.Z)
					local _, _, f = B.basis(p1, p2)
					B.column(folder, "교각", q.X, q.Z, bed - 2, q.Y - BRIDGE_T, w * 0.5, 5, B.yawOf(f), C.pillar, M.concrete)
					count()
					acc += PIER_STEP
				end
				pierAcc = acc - L
			else
				local r1, r2 = extended(p1, p2, extension(ta, w / 2), extension(tb, w / 2), dy)
				local part = B.slab(folder, "도로", r1, r2, w, ROAD_T, C.asphalt, M.asphalt, 0)
				count()
				if kind == "tunnel" then
					part:SetAttribute("Carve", "tunnel")
					part:SetAttribute("CarveWidth", w + 6)
				else
					part:SetAttribute("Carve", "surface")
					part:SetAttribute("CarveWidth", carveW)
				end
			end
			-- 차선 (교차로 근처는 비움)
			local s0, s1 = s, s + L
			local a, b = math.max(s0, trimA), math.min(s1, total - trimB)
			if b - a > 2 then
				local q1 = lerp(p1, p2, (a - s0) / L)
				local q2 = lerp(p1, p2, (b - s0) / L)
				local ea = if a <= s0 + 1e-6 then ta else 0
				local eb = if b >= s1 - 1e-6 then tb else 0
				local mdy = dy + lift + 0.03
				-- 황색 복선 중앙선
				for _, lat in { -0.45, 0.45 } do
					local m1, m2 = extended(q1, q2, extension(ea, 0.65), extension(eb, 0.65), 0)
					B.along(folder, "중앙선", m1, m2, lat, mdy, 0.35, 0.06, C.yellow, M.smooth, B.NOCOLLIDE, 0)
				end
				count(2)
				-- 간선도로는 흰색 바깥 차선
				if primary then
					local lat = w / 2 - 1.4
					for _, side in { -1, 1 } do
						local m1, m2 = extended(q1, q2, extension(ea, lat + 0.3), extension(eb, lat + 0.3), 0)
						B.along(folder, "바깥차선", m1, m2, side * lat, mdy, 0.45, 0.06, C.white, M.smooth, B.NOCOLLIDE, 0)
					end
					count(2)
				end
			end
			s = s1
		end
	end
end

-- 고가 고속도로 (1층)
local function buildExpresswayEdge(folder, e, grid, rampStarts)
	local w = e.w
	local k = 0
	for _, pc in e.pieces do
		local pts = {}
		for i, p in pc.pts do
			pts[i] = v3(p)
		end
		local angles = anglesOf(pts)
		local kind = pc.kind
		local acc = PILLAR_STEP / 2
		for i = 1, #pts - 1 do
			local p1, p2 = pts[i], pts[i + 1]
			local L = (p2 - p1).Magnitude
			if L < 0.05 then
				continue
			end
			k += 1
			local dy = if k % 2 == 0 then STAGGER else 0
			local ta, tb = angles[i], angles[i + 1]
			local d1, d2 = extended(p1, p2, extension(ta, w / 2), extension(tb, w / 2), dy)
			local deck = B.slab(folder, "고가상판", d1, d2, w, DECK_T, C.deck, M.asphalt, 0)
			count()
			local mid = (p1 + p2) * 0.5
			local ground = grid:height(mid.X, mid.Z)
			if kind == "tunnel" then
				deck:SetAttribute("Carve", "tunnel")
				deck:SetAttribute("CarveWidth", w + 6)
			elseif kind == "road" then
				deck:SetAttribute("Carve", "viaduct")
				deck:SetAttribute("CarveWidth", w + 6)
				if mid.Y - DECK_T - ground < 4 then
					deck:SetAttribute("CarveFill", true)
				end
			end
			-- 램프가 붙는 곳은 방호벽을 비운다
			local nearRamp = false
			for _, r in rampStarts do
				if (flat(mid) - flat(r)).Magnitude < 110 then
					nearRamp = true
					break
				end
			end
			if not nearRamp then
				for _, side in { -1, 1 } do
					local lat = side * (w / 2 - 0.6)
					local q1, q2 = extended(p1, p2, extension(ta, w / 2), extension(tb, w / 2), dy)
					B.along(folder, "방호벽", q1, q2, lat, 1.4, 1.2, 2.8, C.concrete, M.concrete, nil, 0)
				end
				count(2)
			end
			local q1, q2 = extended(p1, p2, extension(ta, 0.6), extension(tb, 0.6), dy)
			B.along(folder, "중앙분리대", q1, q2, 0, 1.1, 1.0, 2.2, C.concrete, M.concrete, nil, 0)
			count()
			if kind ~= "tunnel" then
				while acc < L do
					local q = lerp(p1, p2, acc / L)
					local g = grid:height(q.X, q.Z)
					local bottom = q.Y - DECK_T
					if bottom - g > 3 then
						local _, _, f = B.basis(p1, p2)
						local yaw = B.yawOf(f)
						B.column(folder, "교각", q.X, q.Z, g - 2, bottom - 2.4, 7, 9, yaw, C.pillar, M.concrete)
						B.column(folder, "교각머리", q.X, q.Z, bottom - 2.4, bottom, w * 0.72, 5, yaw + math.pi / 2, C.pillar, M.concrete)
						count(2)
					end
					acc += PILLAR_STEP
				end
				acc -= L
			end
		end
	end
end

local function buildRamp(folder, r, grid)
	local w = r.w
	local pts = {}
	for i, p in r.pts do
		-- 고가·지상 도로와 겹치는 부분에서 윗면이 겹쳐 깜빡이지 않도록 아주 조금 낮춘다
		pts[i] = v3(p) - Vector3.new(0, 0.04, 0)
	end
	local angles = anglesOf(pts)
	local total = 0
	for i = 1, #pts - 1 do
		total += (pts[i + 1] - pts[i]).Magnitude
	end
	local s = 0
	local acc = RAMP_PILLAR_STEP / 2
	for i = 1, #pts - 1 do
		local p1, p2 = pts[i], pts[i + 1]
		local L = (p2 - p1).Magnitude
		if L < 0.05 then
			continue
		end
		local dy = if i % 2 == 0 then -STAGGER else 0
		local ta, tb = angles[i], angles[i + 1]
		local d1, d2 = extended(p1, p2, extension(ta, w / 2), extension(tb, w / 2), dy)
		local deck = B.slab(folder, "램프", d1, d2, w, RAMP_T, C.deck, M.asphalt, 0)
		count()
		local mid = (p1 + p2) * 0.5
		local ground = grid:height(mid.X, mid.Z)
		deck:SetAttribute("Carve", "ramp")
		deck:SetAttribute("CarveWidth", w + 4)
		if mid.Y - RAMP_T - ground < 4 then
			deck:SetAttribute("CarveFill", true)
		end
		if s > 45 and s + L < total - 30 then
			for _, side in { -1, 1 } do
				local q1, q2 = extended(p1, p2, extension(ta, w / 2), extension(tb, w / 2), dy)
				B.along(folder, "방호벽", q1, q2, side * (w / 2 - 0.45), 1.2, 0.9, 2.4, C.concrete, M.concrete, nil, 0)
			end
			count(2)
		end
		while acc < L do
			local q = lerp(p1, p2, acc / L)
			local g = grid:height(q.X, q.Z)
			local bottom = q.Y - RAMP_T
			if bottom - g > 4 then
				local _, _, f = B.basis(p1, p2)
				B.column(folder, "교각", q.X, q.Z, g - 2, bottom, 5, 6, B.yawOf(f), C.pillar, M.concrete)
				count()
			end
			acc += RAMP_PILLAR_STEP
		end
		acc -= L
		s += L
	end
end

function Roads.build(parent, data, grid)
	local root = B.folder(parent, "Roads")
	local surface = B.folder(root, "Surface")
	local express = B.folder(root, "Expressway")
	local rampsFolder = B.folder(root, "Ramps")
	local junctions = B.folder(root, "Junctions")

	local nodes = data.nodes
	-- 노드별 가장 넓은 도로 폭 (같은 층끼리)
	local maxW = {}
	for _, e in data.edges do
		for _, idx in { e.a, e.b } do
			if idx >= 0 and e.tier == nodes[idx + 1].tier then
				maxW[idx] = math.max(maxW[idx] or 0, e.w)
			end
		end
	end

	-- 고가별 램프 시작점
	local rampStarts = {}
	for _, r in data.ramps do
		local list = rampStarts[r.e1] or {}
		table.insert(list, v3(r.pts[1]))
		table.insert(list, v3(r.pts[math.min(3, #r.pts)]))
		rampStarts[r.e1] = list
	end

	for ei, e in data.edges do
		local idx = ei - 1
		local name = if e.name ~= "" then e.name else "도로"
		if e.tier == 0 then
			local f = B.folder(surface, string.format("%s #%d", name, idx))
			f:SetAttribute("Class", e.cls)
			local function trim(n)
				if n < 0 then
					return 0
				end
				local node = nodes[n + 1]
				return if node.deg >= 3 then (maxW[n] or e.w) / 2 + 6 else 0
			end
			buildSurfaceEdge(f, e, grid, trim(e.a), trim(e.b))
		else
			local f = B.folder(express, string.format("%s #%d", name, idx))
			buildExpresswayEdge(f, e, grid, rampStarts[idx] or {})
		end
	end

	for i, r in data.ramps do
		local f = B.folder(rampsFolder, string.format("램프 #%d", i))
		buildRamp(f, r, grid)
	end

	-- 교차로
	for i, n in nodes do
		local idx = i - 1
		if n.deg >= 3 then
			local center = Vector3.new(n.x, n.y + 0.04, n.z)
			if n.tier == 0 then
				local d = (maxW[idx] or 48) + 4
				local p = B.disk(junctions, "교차로", center, d, ROAD_T, C.asphalt, M.asphalt)
				p:SetAttribute("Carve", "junction")
				p:SetAttribute("CarveRadius", d / 2 + 9)
				count()
			else
				local d = (maxW[idx] or 56) + 6
				B.disk(junctions, "고가교차", center, d, DECK_T, C.deck, M.asphalt)
				local g = grid:height(n.x, n.z)
				B.column(junctions, "교각", n.x, n.z, g - 2, n.y - DECK_T, 10, 10, 0, C.pillar, M.concrete)
				count(2)
			end
		end
	end
	return root, stats
end

return Roads
