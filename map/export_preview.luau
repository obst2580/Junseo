-- 미리보기용 파트 내보내기: build/City.rbxm → build/preview/parts.bin
-- 저장소 최상위에서 실행:  lune run map/export_preview
--
-- 파트 하나당 22바이트:
--   u8 묶음(색), u8 모양(0 상자, 1 원기둥),
--   i32 X, i16 Y, i32 Z 위치 (X·Z 0.01스터드, Y 0.02스터드 단위),
--   u16 x3 크기(0.05스터드 단위), i8 x4 회전 사원수(/127)

local fs = require("@lune/fs")
local roblox = require("@lune/roblox")

local GROUP = {
	["도로"] = 1,
	["다리노면"] = 1,
	["교차로"] = 1,
	["회차로"] = 1,
	["램프"] = 2,
	["고가상판"] = 2,
	["고가교차"] = 2,
	["방호벽"] = 3,
	["중앙분리대"] = 3,
	["다리상판"] = 3,
	["교각"] = 4,
	["교각머리"] = 4,
	["중앙선"] = 5,
	["바깥차선"] = 6,
	["난간"] = 7,
	["차단막기둥"] = 7,
	["표지판기둥"] = 7,
	["차단막"] = 8,
	["통행금지표지"] = 8,
}

local function quat(cf)
	local _, _, _, m00, m01, m02, m10, m11, m12, m20, m21, m22 = cf:GetComponents()
	local tr = m00 + m11 + m22
	local w, x, y, z
	if tr > 0 then
		local s = math.sqrt(tr + 1) * 2
		w = 0.25 * s
		x = (m21 - m12) / s
		y = (m02 - m20) / s
		z = (m10 - m01) / s
	elseif m00 > m11 and m00 > m22 then
		local s = math.sqrt(1 + m00 - m11 - m22) * 2
		w = (m21 - m12) / s
		x = 0.25 * s
		y = (m01 + m10) / s
		z = (m02 + m20) / s
	elseif m11 > m22 then
		local s = math.sqrt(1 + m11 - m00 - m22) * 2
		w = (m02 - m20) / s
		x = (m01 + m10) / s
		y = 0.25 * s
		z = (m12 + m21) / s
	else
		local s = math.sqrt(1 + m22 - m00 - m11) * 2
		w = (m10 - m01) / s
		x = (m02 + m20) / s
		y = (m12 + m21) / s
		z = 0.25 * s
	end
	return x, y, z, w
end

local city = roblox.deserializeModel(fs.readFile("build/City.rbxm"))[1]
local list = {}
for _, d in city:GetDescendants() do
	if d:IsA("BasePart") and GROUP[d.Name] then
		table.insert(list, d)
	end
end

local REC = 22
local out = buffer.create(#list * REC)
local function clampi16(v)
	return math.clamp(math.floor(v + 0.5), -32768, 32767)
end
for i, p in list do
	local o = (i - 1) * REC
	local cf = p.CFrame
	local pos = cf.Position
	local size = p.Size
	local qx, qy, qz, qw = quat(cf)
	buffer.writeu8(out, o, GROUP[p.Name])
	buffer.writeu8(out, o + 1, if p.Shape == roblox.Enum.PartType.Cylinder then 1 else 0)
	buffer.writei32(out, o + 2, math.floor(pos.X * 100 + 0.5))
	buffer.writei16(out, o + 6, clampi16(pos.Y * 50))
	buffer.writei32(out, o + 8, math.floor(pos.Z * 100 + 0.5))
	buffer.writeu16(out, o + 12, math.clamp(math.floor(size.X * 20 + 0.5), 0, 65535))
	buffer.writeu16(out, o + 14, math.clamp(math.floor(size.Y * 20 + 0.5), 0, 65535))
	buffer.writeu16(out, o + 16, math.clamp(math.floor(size.Z * 20 + 0.5), 0, 65535))
	buffer.writei8(out, o + 18, math.clamp(math.floor(qx * 127 + 0.5), -127, 127))
	buffer.writei8(out, o + 19, math.clamp(math.floor(qy * 127 + 0.5), -127, 127))
	buffer.writei8(out, o + 20, math.clamp(math.floor(qz * 127 + 0.5), -127, 127))
	buffer.writei8(out, o + 21, math.clamp(math.floor(qw * 127 + 0.5), -127, 127))
end
fs.writeDir("build/preview")
fs.writeFile("build/preview/parts.bin", buffer.tostring(out))
print(string.format("미리보기 파트 %d개 → build/preview/parts.bin (%.1fMB)", #list, buffer.len(out) / 1e6))
