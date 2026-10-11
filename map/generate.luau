-- 맵 생성기: map/data/*.json → build/City.rbxm, build/MapData.rbxm
-- 저장소 최상위에서 실행:  lune run map/generate

local fs = require("@lune/fs")
local serde = require("@lune/serde")
local roblox = require("@lune/roblox")
local Instance = roblox.Instance

local B = require("./lib/build")
local Roads = require("./lib/roads")
local TerrainGrid = require("./lib/terraingrid")
local World = require("./lib/world")

local CHUNK_CHARS = 100000

local function readJson(path: string)
	return serde.decode("json", fs.readFile(path))
end

-- 루아 표 → Luau 소스 (MapMeta 모듈용)
local function toLuau(v, indent: string?): string
	local ind = indent or ""
	local t = typeof(v)
	if t == "string" then
		return string.format("%q", v)
	elseif t == "number" then
		if v == math.floor(v) then
			return tostring(v)
		end
		return string.format("%.2f", v)
	elseif t == "boolean" then
		return tostring(v)
	elseif t == "table" then
		local inner = ind .. "\t"
		local out = {}
		if #v > 0 then
			for _, item in v do
				table.insert(out, inner .. toLuau(item, inner))
			end
		else
			local keys = {}
			for k in v do
				table.insert(keys, k)
			end
			table.sort(keys)
			for _, k in keys do
				table.insert(out, string.format("%s[%q] = %s", inner, k, toLuau(v[k], inner)))
			end
		end
		if #out == 0 then
			return "{}"
		end
		return "{\n" .. table.concat(out, ",\n") .. ",\n" .. ind .. "}"
	end
	return "nil"
end

local function module(parent, name: string, source: string)
	local m = Instance.new("ModuleScript")
	m.Name = name
	m.Source = source
	m.Parent = parent
	return m
end

local function chunked(parent, prefix: string, s: string): { string }
	local names = {}
	local i = 1
	local n = 1
	while i <= #s do
		local name = string.format("%s%02d", prefix, n)
		module(parent, name, "-- 자동 생성: 지형 데이터 조각. 직접 고치지 마세요.\nreturn [[" .. string.sub(s, i, i + CHUNK_CHARS - 1) .. "]]\n")
		table.insert(names, name)
		i += CHUNK_CHARS
		n += 1
	end
	return names
end

local t0 = os.clock()
local roadsData = readJson("map/data/roads.json")
local terrainData = readJson("map/data/terrain.json")
local meta = readJson("map/data/meta.json")
local version = terrainData.version
print("맵 버전", version)

local grid = TerrainGrid.new(terrainData)

-- 월드
local city = Instance.new("Model")
city.Name = "City"
city:SetAttribute("Version", version)
city:SetAttribute("Scale", "1/10")
city:SetAttribute("Attribution", meta.attribution)

local _, stats = Roads.build(city, roadsData, grid)
local nWalls = World.build(city, meta, version)
print(string.format("도로 파트 %d개, 경계벽 %d개", stats.parts, nWalls))

fs.writeDir("build")
fs.writeFile("build/City.rbxm", roblox.serializeModel({ city }))

-- 서버 데이터 (지형 격자·맵 정보)
local data = Instance.new("Folder")
data.Name = "MapData"
local info = {
	version = version,
	grid = terrainData.grid,
	x0 = terrainData.x0,
	z0 = terrainData.z0,
	nx = terrainData.nx,
	nz = terrainData.nz,
	unit = terrainData.unit,
	waterLevel = terrainData.waterLevel,
	materials = terrainData.materials,
	height = chunked(data, "H", terrainData.height),
	waterTop = chunked(data, "W", terrainData.waterTop),
	material = chunked(data, "M", terrainData.material),
}
module(data, "TerrainInfo", "-- 자동 생성: 지형 격자 정보\nreturn " .. toLuau(info) .. "\n")
local mapMeta = {
	version = version,
	offset = meta.offset,
	spawn = meta.spawn,
	landmarks = meta.landmarks,
	facilityZone = meta.facilityZone,
	farms = meta.farms,
	districts = meta.districts,
	attribution = meta.attribution,
}
module(data, "MapMeta", "-- 자동 생성: 구역·랜드마크·스폰 정보 (게임 좌표)\nreturn " .. toLuau(mapMeta) .. "\n")
fs.writeFile("build/MapData.rbxm", roblox.serializeModel({ data }))

print(string.format("완료 %.1fs  build/City.rbxm %.1fMB, build/MapData.rbxm %.1fMB", os.clock() - t0,
	#fs.readFile("build/City.rbxm") / 1e6, #fs.readFile("build/MapData.rbxm") / 1e6))
