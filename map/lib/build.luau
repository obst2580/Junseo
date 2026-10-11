-- 파트 만들기 도우미.
-- 주의: Lune 0.8.9의 CFrame.lookAt은 방향이 틀리게 나온다. 회전은 항상 basis()/frame()으로 직접 만든다.

local roblox = require("@lune/roblox")
local Instance = roblox.Instance
local Vector3 = roblox.Vector3
local CFrame = roblox.CFrame
local Color3 = roblox.Color3
local Enum = roblox.Enum

local B = {}

B.COLOR = {
	asphalt = Color3.fromRGB(52, 53, 57),
	deck = Color3.fromRGB(60, 61, 66),
	concrete = Color3.fromRGB(178, 176, 170),
	pillar = Color3.fromRGB(150, 149, 144),
	yellow = Color3.fromRGB(236, 182, 28),
	white = Color3.fromRGB(232, 232, 232),
	rail = Color3.fromRGB(118, 124, 130),
	sign = Color3.fromRGB(28, 70, 140),
}

B.MAT = {
	asphalt = Enum.Material.Asphalt,
	concrete = Enum.Material.Concrete,
	smooth = Enum.Material.SmoothPlastic,
	metal = Enum.Material.Metal,
}

local UP = Vector3.new(0, 1, 0)

function B.folder(parent, name: string)
	local f = Instance.new("Folder")
	f.Name = name
	f.Parent = parent
	return f
end

function B.part(parent, name: string, size, cframe, color, material, props: { [string]: any }?)
	local p = Instance.new("Part")
	p.Name = name
	p.Anchored = true
	p.Size = size
	p.CFrame = cframe
	p.Color = color
	p.Material = material
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	if props then
		for k, v in props do
			(p :: any)[k] = v
		end
	end
	p.Parent = parent
	return p
end

-- 선분 방향 기준 좌표축 (오른쪽, 위, 앞, 길이). 위는 수평에 가깝게 유지된다.
function B.basis(p1, p2)
	local d = p2 - p1
	local L = d.Magnitude
	local f = if L > 1e-6 then d / L else Vector3.new(0, 0, -1)
	local right = f:Cross(UP)
	if right.Magnitude < 1e-4 then
		right = Vector3.new(1, 0, 0)
	end
	right = right.Unit
	local up = right:Cross(f).Unit
	return right, up, f, L
end

function B.frame(pos, right, up, f)
	local back = -f
	return CFrame.new(pos.X, pos.Y, pos.Z, right.X, up.X, back.X, right.Y, up.Y, back.Y, right.Z, up.Z, back.Z)
end

-- 윗면 중심선이 p1→p2를 지나는 판
function B.slab(parent, name, p1, p2, width, thick, color, material, extend: number?, props: { [string]: any }?)
	local right, up, f, L = B.basis(p1, p2)
	local mid = (p1 + p2) * 0.5 - up * (thick / 2)
	return B.part(parent, name, Vector3.new(width, thick, L + (extend or 0.2)), B.frame(mid, right, up, f), color, material, props)
end

-- 선분을 따라 옆(lateral)·위(vertical)로 비켜 놓는 상자 (난간·중앙선 등)
function B.along(parent, name, p1, p2, lateral, vertical, w, h, color, material, props: { [string]: any }?, extend: number?)
	local right, up, f, L = B.basis(p1, p2)
	local mid = (p1 + p2) * 0.5 + right * lateral + up * vertical
	return B.part(parent, name, Vector3.new(w, h, L + (extend or 0.2)), B.frame(mid, right, up, f), color, material, props)
end

-- 윗면 중심이 center인 납작한 원판 (교차로·이음매)
function B.disk(parent, name, center, diameter, thick, color, material, props: { [string]: any }?)
	local cf = CFrame.new(center.X, center.Y - thick / 2, center.Z) * CFrame.Angles(0, 0, math.pi / 2)
	local p = B.part(parent, name, Vector3.new(thick, diameter, diameter), cf, color, material, props)
	p.Shape = Enum.PartType.Cylinder
	return p
end

-- 세로 기둥 (yaw: 진행 방향에 맞춘 회전)
function B.column(parent, name, x, z, yBottom, yTop, w, d, yaw, color, material)
	local h = yTop - yBottom
	if h <= 0.2 then
		return nil
	end
	local cf = CFrame.new(x, (yTop + yBottom) / 2, z) * CFrame.Angles(0, yaw, 0)
	return B.part(parent, name, Vector3.new(w, h, d), cf, color, material, { CastShadow = true })
end

function B.yawOf(f)
	-- 앞 방향 f(수평 성분)에 맞춘 Y축 회전각. CFrame.Angles(0, yaw, 0)의 앞이 f가 되도록
	return math.atan2(-f.X, -f.Z)
end

B.NOCOLLIDE = { CanCollide = false, CastShadow = false, CanQuery = false, CanTouch = false }

return B
