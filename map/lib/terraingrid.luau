-- 생성기에서 지형 높이를 읽는다 (교각 높이 계산 등). 로블록스 쪽과 같은 디코더를 쓴다.

local GridCodec = require("../../src/server/GridCodec")

local TerrainGrid = {}
TerrainGrid.__index = TerrainGrid

function TerrainGrid.new(info)
	local self = setmetatable({}, TerrainGrid)
	self.info = info
	self.H = GridCodec.decodeGrid(info.height, info.nx, info.nz, "i16")
	return self
end

-- 게임 좌표의 지형 높이 (물속이면 바닥 높이)
function TerrainGrid:height(x: number, z: number): number
	local info = self.info
	local gx = (x - info.x0) / info.grid
	local gz = (z - info.z0) / info.grid
	local ix = math.floor(gx)
	local iz = math.floor(gz)
	local fx = gx - ix
	local fz = gz - iz
	local get = GridCodec.get
	local H = self.H
	local a = get(H, ix, iz) * (1 - fx) + get(H, ix + 1, iz) * fx
	local b = get(H, ix, iz + 1) * (1 - fx) + get(H, ix + 1, iz + 1) * fx
	return (a * (1 - fz) + b * fz) * info.unit
end

return TerrainGrid
