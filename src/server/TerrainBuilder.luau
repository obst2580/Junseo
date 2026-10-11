--!strict
-- 서울+인천 지형 생성기.
--
-- MapData 폴더의 지형 격자(높이·수면·재질)를 읽어 로블록스 지형(Terrain)을 만들고,
-- 도로 파트를 따라 지형을 깎고 채워 도로·인도 높이를 맞춘다.
--
-- Studio에서 한 번 구워 두려면 명령줄(View > Command Bar)에 아래 한 줄을 넣고 실행한 뒤 저장한다.
--   require(game.ServerScriptService.MapRuntime.TerrainBuilder).build()
-- 구워 두지 않으면 서버가 시작할 때 Bootstrap 스크립트가 자동으로 만든다(시간이 걸림).

local ServerScriptService = game:GetService("ServerScriptService")

local GridCodec = require(script.Parent.GridCodec)

local TerrainBuilder = {}

TerrainBuilder.ATTRIBUTE = "SeoulMapVersion"

local VOX = 4
local CHUNK_VOX = 16 -- 한 번에 쓰는 영역: 16x16 칸 = 64x64 스터드
local CHUNK = CHUNK_VOX * VOX
local SKIN = 8 -- 지표 아래로 채우는 두께
local NO_WATER = -32768

export type Options = {
	yield: boolean?, -- 서버에서 돌릴 때 중간중간 양보해서 멈춤을 막음
	dataFolder: Instance?,
	roads: Instance?,
	skipRoads: boolean?,
}

type Info = {
	version: string,
	grid: number,
	x0: number,
	z0: number,
	nx: number,
	nz: number,
	unit: number,
	waterLevel: number,
	materials: { string },
	height: { string },
	waterTop: { string },
	material: { string },
}

local function join(folder: Instance, names: { string }): string
	local parts = table.create(#names)
	for i, name in names do
		parts[i] = require(folder:WaitForChild(name)) :: any
	end
	return table.concat(parts)
end

local function setAppearance(terrain: Terrain)
	terrain.WaterColor = Color3.fromRGB(38, 92, 118)
	terrain.WaterTransparency = 0.55
	terrain.WaterReflectance = 0.6
	terrain.WaterWaveSize = 0.12
	terrain.WaterWaveSpeed = 8
	terrain:SetMaterialColor(Enum.Material.Grass, Color3.fromRGB(106, 140, 72))
	terrain:SetMaterialColor(Enum.Material.LeafyGrass, Color3.fromRGB(78, 112, 58))
	-- 인도(보도블록)는 따뜻한 회갈색, 시가지 바닥은 회색 콘크리트
	terrain:SetMaterialColor(Enum.Material.Pavement, Color3.fromRGB(172, 158, 142))
	terrain:SetMaterialColor(Enum.Material.Concrete, Color3.fromRGB(140, 140, 136))
	terrain:SetMaterialColor(Enum.Material.Ground, Color3.fromRGB(120, 96, 66))
	terrain:SetMaterialColor(Enum.Material.Sand, Color3.fromRGB(196, 180, 140))
	terrain:SetMaterialColor(Enum.Material.Rock, Color3.fromRGB(110, 106, 100))
end

-- 지형 격자 → 지형
local function buildGround(terrain: Terrain, info: Info, folder: Instance, yieldFn: () -> ())
	local nx, nz = info.nx, info.nz
	local H = GridCodec.decodeGrid(join(folder, info.height), nx, nz, "i16")
	local W = GridCodec.decodeGrid(join(folder, info.waterTop), nx, nz, "i16")
	local M = GridCodec.decodeGrid(join(folder, info.material), nx, nz, "u8")
	local materials: { [number]: Enum.Material } = {}
	for i, name in info.materials do
		materials[i - 1] = (Enum.Material :: any)[name]
	end
	local unit = info.unit
	local G = info.grid
	local perCell = G // VOX -- 격자 한 칸에 들어가는 지형 칸 수
	local x0, z0 = info.x0, info.z0
	local get = GridCodec.get
	local AIR, WATER = Enum.Material.Air, Enum.Material.Water

	local chunksX = math.ceil((nx - 1) * perCell / CHUNK_VOX)
	local chunksZ = math.ceil((nz - 1) * perCell / CHUNK_VOX)
	local total = chunksX * chunksZ
	local done = 0
	local nextReport = 0.1

	local tops = table.create(CHUNK_VOX * CHUNK_VOX, 0)
	local levels = table.create(CHUNK_VOX * CHUNK_VOX, 0)
	local mats = table.create(CHUNK_VOX * CHUNK_VOX, AIR)

	for cz = 0, chunksZ - 1 do
		-- 같은 줄에서 평평하고 같은 재질인 덩어리는 이어서 한 번에 채운다
		local runStart: number? = nil
		local runKey = ""
		local runTop, runLevel, runMat = 0, NO_WATER, AIR
		local function flush(endCx: number)
			if runStart == nil then
				return
			end
			local xa = x0 + runStart * CHUNK
			local xb = x0 + endCx * CHUNK
			local za = z0 + cz * CHUNK
			local cx = (xa + xb) / 2
			local czw = za + CHUNK / 2
			terrain:FillBlock(CFrame.new(cx, runTop - SKIN / 2, czw), Vector3.new(xb - xa, SKIN, CHUNK), runMat)
			if runLevel ~= NO_WATER and runLevel > runTop then
				terrain:FillBlock(
					CFrame.new(cx, (runTop + runLevel) / 2, czw),
					Vector3.new(xb - xa, runLevel - runTop, CHUNK),
					WATER
				)
			end
			runStart = nil
		end

		for cx = 0, chunksX - 1 do
			local minTop, maxTop = math.huge, -math.huge
			local maxLevel = NO_WATER
			local anyWater = false
			local uniform = true
			local firstMat: Enum.Material? = nil
			local firstLevel: number? = nil
			for vz = 0, CHUNK_VOX - 1 do
				local gzf = (cz * CHUNK_VOX + vz + 0.5) / perCell
				local iz = math.floor(gzf)
				local fz = gzf - iz
				for vx = 0, CHUNK_VOX - 1 do
					local gxf = (cx * CHUNK_VOX + vx + 0.5) / perCell
					local ix = math.floor(gxf)
					local fx = gxf - ix
					local h00 = get(H, ix, iz)
					local h10 = get(H, ix + 1, iz)
					local h01 = get(H, ix, iz + 1)
					local h11 = get(H, ix + 1, iz + 1)
					local top = ((h00 * (1 - fx) + h10 * fx) * (1 - fz) + (h01 * (1 - fx) + h11 * fx) * fz) * unit
					-- 수면: 둘레 꼭짓점 중 물이 있는 곳의 가장 높은 수면 (물가가 부드럽게 이어짐)
					local level = NO_WATER
					local w00, w10, w01, w11 = get(W, ix, iz), get(W, ix + 1, iz), get(W, ix, iz + 1), get(W, ix + 1, iz + 1)
					if w00 ~= NO_WATER and w00 * unit > level then
						level = w00 * unit
					end
					if w10 ~= NO_WATER and w10 * unit > level then
						level = w10 * unit
					end
					if w01 ~= NO_WATER and w01 * unit > level then
						level = w01 * unit
					end
					if w11 ~= NO_WATER and w11 * unit > level then
						level = w11 * unit
					end
					local mat = materials[get(M, math.floor(gxf + 0.5), math.floor(gzf + 0.5))] or Enum.Material.Grass
					local k = vz * CHUNK_VOX + vx + 1
					tops[k] = top
					levels[k] = if level ~= NO_WATER and level > top then level else NO_WATER
					mats[k] = mat
					if top < minTop then
						minTop = top
					end
					if top > maxTop then
						maxTop = top
					end
					if levels[k] ~= NO_WATER then
						anyWater = true
						if levels[k] > maxLevel then
							maxLevel = levels[k]
						end
					end
					if firstMat == nil then
						firstMat = mat
						firstLevel = levels[k]
					elseif mat ~= firstMat or levels[k] ~= firstLevel then
						uniform = false
					end
				end
			end
			if uniform and maxTop - minTop < 0.01 then
				local key = string.format("%.2f|%.2f|%s", minTop, firstLevel :: number, tostring(firstMat))
				if runStart == nil or key ~= runKey then
					flush(cx)
					runStart = cx
					runKey = key
					runTop, runLevel, runMat = minTop, firstLevel :: number, firstMat :: Enum.Material
				end
			else
				flush(cx)
				local yLo = math.floor((minTop - SKIN) / VOX) * VOX
				local yHi = math.ceil(math.max(maxTop, if anyWater then maxLevel else maxTop) / VOX) * VOX
				if yHi <= yLo then
					yHi = yLo + VOX
				end
				local ny = (yHi - yLo) // VOX
				local mArr = table.create(CHUNK_VOX)
				local oArr = table.create(CHUNK_VOX)
				for vx = 1, CHUNK_VOX do
					local mx = table.create(ny)
					local ox = table.create(ny)
					for vy = 1, ny do
						local my = table.create(CHUNK_VOX)
						local oy = table.create(CHUNK_VOX)
						local bottom = yLo + (vy - 1) * VOX
						for vz = 1, CHUNK_VOX do
							local k = (vz - 1) * CHUNK_VOX + vx
							local top = tops[k]
							local occ = (top - bottom) / VOX
							if occ > 0 then
								my[vz] = mats[k]
								oy[vz] = if occ > 1 then 1 else occ
							elseif levels[k] ~= NO_WATER and levels[k] > bottom + 0.5 then
								my[vz] = WATER
								oy[vz] = 1
							else
								my[vz] = AIR
								oy[vz] = 0
							end
						end
						mx[vy] = my
						ox[vy] = oy
					end
					mArr[vx] = mx
					oArr[vx] = ox
				end
				local xa = x0 + cx * CHUNK
				local za = z0 + cz * CHUNK
				local region = Region3.new(Vector3.new(xa, yLo, za), Vector3.new(xa + CHUNK, yHi, za + CHUNK))
				terrain:WriteVoxels(region, VOX, mArr, oArr)
			end
			done += 1
			if done % 150 == 0 then
				yieldFn()
			end
			if done / total >= nextReport then
				print(string.format("[맵] 지형 %d%%", math.floor(done / total * 100 + 0.5)))
				nextReport += 0.1
			end
		end
		flush(chunksX)
	end
end

-- 도로를 따라 지형 깎기/채우기
local function carveRoads(terrain: Terrain, roads: Instance, yieldFn: () -> ())
	local parts: { BasePart } = {}
	for _, d in roads:GetDescendants() do
		if d:IsA("BasePart") and d:GetAttribute("Carve") ~= nil then
			table.insert(parts, d)
		end
	end
	local AIR = Enum.Material.Air
	local PAVE = Enum.Material.Pavement
	-- 1단계: 도로 위 공간 비우기
	for i, p in parts do
		local kind = p:GetAttribute("Carve")
		local width = (p:GetAttribute("CarveWidth") :: number?) or p.Size.X
		if kind == "junction" then
			local r = (p:GetAttribute("CarveRadius") :: number?) or p.Size.Y / 2
			local top = p.CFrame.Position.Y + p.Size.X / 2
			terrain:FillCylinder(CFrame.new(p.CFrame.Position.X, top - 0.05 + 30, p.CFrame.Position.Z), 60, r, AIR)
		else
			local topCf = p.CFrame * CFrame.new(0, p.Size.Y / 2, 0)
			local height = if kind == "tunnel" then 20 elseif kind == "viaduct" then 34 else 60
			local base = if kind == "viaduct" then -(p.Size.Y + 2) else -0.05
			terrain:FillBlock(topCf * CFrame.new(0, base + height / 2, 0), Vector3.new(width, height, p.Size.Z + 4), AIR)
		end
		if i % 400 == 0 then
			yieldFn()
		end
	end
	-- 2단계: 도로 아래·인도 채우기
	for i, p in parts do
		local kind = p:GetAttribute("Carve")
		local width = (p:GetAttribute("CarveWidth") :: number?) or p.Size.X
		local topCf = p.CFrame * CFrame.new(0, p.Size.Y / 2, 0)
		if kind == "surface" then
			terrain:FillBlock(topCf * CFrame.new(0, -0.05 - 6, 0), Vector3.new(width, 12, p.Size.Z + 4), PAVE)
		elseif kind == "junction" then
			local r = (p:GetAttribute("CarveRadius") :: number?) or p.Size.Y / 2
			local top = p.CFrame.Position.Y + p.Size.X / 2
			terrain:FillCylinder(CFrame.new(p.CFrame.Position.X, top - 0.05 - 6, p.CFrame.Position.Z), 12, r, PAVE)
		elseif kind == "tunnel" then
			terrain:FillBlock(topCf * CFrame.new(0, -0.05 - 3, 0), Vector3.new(width, 6, p.Size.Z + 2), Enum.Material.Concrete)
		elseif p:GetAttribute("CarveFill") then
			terrain:FillBlock(topCf * CFrame.new(0, -p.Size.Y - 6, 0), Vector3.new(width, 12, p.Size.Z + 2), Enum.Material.Ground)
		end
		if i % 400 == 0 then
			yieldFn()
		end
	end
	return #parts
end

function TerrainBuilder.build(options: Options?)
	local opts: Options = options or {}
	local folder = opts.dataFolder or ServerScriptService:WaitForChild("MapData")
	local info = require(folder:WaitForChild("TerrainInfo")) :: any
	local terrain = workspace.Terrain
	local yieldFn = if opts.yield then function()
		task.wait()
	end else function() end
	local t0 = os.clock()
	print("[맵] 지형 생성 시작")
	terrain:Clear()
	setAppearance(terrain)
	buildGround(terrain, info, folder, yieldFn)
	print(string.format("[맵] 지면 완료 (%.0f초)", os.clock() - t0))
	if not opts.skipRoads then
		local city = workspace:FindFirstChild("City")
		local roads = opts.roads or (city and city:FindFirstChild("Roads"))
		if roads then
			local n = carveRoads(terrain, roads, yieldFn)
			print(string.format("[맵] 도로 %d개 구간 정리 완료 (%.0f초)", n, os.clock() - t0))
		end
	end
	terrain:SetAttribute(TerrainBuilder.ATTRIBUTE, info.version)
	print(string.format("[맵] 지형 생성 끝 (%.0f초). 지금 저장하면 다음부터는 바로 열립니다.", os.clock() - t0))
end

function TerrainBuilder.isBuilt(): boolean
	local folder = ServerScriptService:FindFirstChild("MapData")
	if not folder then
		return false
	end
	local info = require(folder:WaitForChild("TerrainInfo")) :: any
	return workspace.Terrain:GetAttribute(TerrainBuilder.ATTRIBUTE) == info.version
end

return TerrainBuilder
