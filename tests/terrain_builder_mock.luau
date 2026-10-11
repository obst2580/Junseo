-- 지형 생성기(TerrainBuilder) 검사: 로블록스 없이 Lune에서 가짜 Terrain으로 끝까지 돌려 본다.
-- 생성된 build/City.rbxm, build/MapData.rbxm을 그대로 읽어 쓰므로 맵 생성 후에 실행한다.
--   lune run tests/terrain_builder_mock
--
-- 확인하는 것: 실행 중 오류가 없는지, WriteVoxels 영역이 4스터드 격자에 맞는지,
-- 배열 크기·점유율 범위가 맞는지, 물·지면이 얼마나 채워지는지, 루아 쪽 실행 시간.

local fs = require("@lune/fs")
local luau = require("@lune/luau")
local roblox = require("@lune/roblox")

local process = require("@lune/process")

local GridCodec = require("../src/server/GridCodec")

-- "fast"를 주면 칸 하나하나 검사는 50번에 한 번만 (생성기 자체 시간 재기용)
local FAST = table.find(process.args, "fast") ~= nil

local Enum = roblox.Enum
local Vector3 = roblox.Vector3
local CFrame = roblox.CFrame
local Color3 = roblox.Color3
local Region3 = roblox.Region3

local stats = {
	fillBlock = 0,
	fillCylinder = 0,
	writeVoxels = 0,
	voxels = 0,
	solid = 0,
	water = 0,
	errors = {},
	materials = {},
}

local function fail(msg)
	if #stats.errors < 20 then
		table.insert(stats.errors, msg)
	end
end

local attrs = {}
local terrain = {}
function terrain:Clear() end
function terrain:SetMaterialColor() end
function terrain:SetAttribute(k, v)
	attrs[k] = v
end
function terrain:GetAttribute(k)
	return attrs[k]
end
function terrain:FillBlock(cf, size, mat)
	stats.fillBlock += 1
	if size.X <= 0 or size.Y <= 0 or size.Z <= 0 then
		fail("FillBlock 크기가 0 이하: " .. tostring(size))
	end
	stats.materials[tostring(mat)] = (stats.materials[tostring(mat)] or 0) + 1
end
function terrain:FillCylinder(cf, h, r, mat)
	stats.fillCylinder += 1
	if h <= 0 or r <= 0 then
		fail("FillCylinder 크기가 0 이하")
	end
end
function terrain:WriteVoxels(region, res, mats, occs)
	stats.writeVoxels += 1
	if res ~= 4 then
		fail("해상도가 4가 아님")
	end
	local size = region.Size
	local minC = region.CFrame.Position - size / 2
	for _, v in { minC.X, minC.Y, minC.Z } do
		if math.abs(v / 4 - math.floor(v / 4 + 0.5)) > 1e-6 then
			fail("영역이 4스터드 격자에 안 맞음: " .. tostring(minC))
			break
		end
	end
	local nx, ny, nz = size.X / 4, size.Y / 4, size.Z / 4
	if #mats ~= nx or #mats[1] ~= ny or #mats[1][1] ~= nz or #occs ~= nx then
		fail(string.format("배열 크기 불일치: 영역 %d,%d,%d / 배열 %d,%d,%d", nx, ny, nz, #mats, #mats[1], #mats[1][1]))
	end
	stats.voxels += nx * ny * nz
	if FAST and stats.writeVoxels % 50 ~= 0 then
		return
	end
	for x = 1, #mats do
		for y = 1, #mats[x] do
			local mrow, orow = mats[x][y], occs[x][y]
			for z = 1, #mrow do
				local o = orow[z]
				if o < 0 or o > 1 then
					fail("점유율 범위 밖: " .. tostring(o))
				end
				if mrow[z] == Enum.Material.Water then
					stats.water += 1
				elseif o > 0 then
					stats.solid += 1
				end
			end
		end
	end
end

-- MapData 모듈: 생성된 ModuleScript 소스를 실제로 실행해서 값을 얻는다
local mapDataModel = roblox.deserializeModel(fs.readFile("build/MapData.rbxm"))[1]
local moduleValues = {}
local mapData = {}
function mapData:WaitForChild(name)
	local inst = mapDataModel:FindFirstChild(name)
	assert(inst, "MapData에 없음: " .. name)
	if moduleValues[name] == nil then
		local fn = luau.load(inst.Source, { debugName = "MapData." .. name })
		moduleValues[name] = fn()
	end
	return { __value = moduleValues[name] }
end
mapData.FindFirstChild = mapData.WaitForChild

local city = roblox.deserializeModel(fs.readFile("build/City.rbxm"))[1]

local env = {
	game = {
		GetService = function(_, name)
			assert(name == "ServerScriptService", "모르는 서비스: " .. name)
			return {
				WaitForChild = function(_, n)
					assert(n == "MapData")
					return mapData
				end,
				FindFirstChild = function(_, n)
					return if n == "MapData" then mapData else nil
				end,
			}
		end,
	},
	workspace = {
		Terrain = terrain,
		FindFirstChild = function(_, name)
			return if name == "City" then city else nil
		end,
	},
	script = { Parent = { GridCodec = { __value = GridCodec } } },
	require = function(m)
		return m.__value
	end,
	Enum = Enum,
	Vector3 = Vector3,
	CFrame = CFrame,
	Color3 = Color3,
	Region3 = Region3,
	task = { wait = function() end },
}

local source = fs.readFile("src/server/TerrainBuilder.luau")
local TerrainBuilder = luau.load(source, { debugName = "TerrainBuilder", environment = env })()

local t0 = os.clock()
TerrainBuilder.build({ yield = true })
local dt = os.clock() - t0

print(string.format("실행 시간(루아 쪽) %.1f초", dt))
print(string.format("FillBlock %d회, FillCylinder %d회, WriteVoxels %d회", stats.fillBlock, stats.fillCylinder, stats.writeVoxels))
print(string.format("쓴 칸 %d개%s (지면 %d, 물 %d)", stats.voxels, if FAST then ", 1/50 표본 검사" else "", stats.solid, stats.water))
print("버전 속성", attrs[TerrainBuilder.ATTRIBUTE])
if #stats.errors > 0 then
	for _, e in stats.errors do
		print("오류:", e)
	end
	error("지형 생성 검사 실패")
end
print("지형 생성 검사 통과")
