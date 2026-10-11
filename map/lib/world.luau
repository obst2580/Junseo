-- 경계 벽, 스폰, 안내판, 랜드마크 자리, 개발용 이름표

local roblox = require("@lune/roblox")
local Instance = roblox.Instance
local Vector3 = roblox.Vector3
local CFrame = roblox.CFrame
local Color3 = roblox.Color3
local UDim2 = roblox.UDim2
local Enum = roblox.Enum
local B = require("./build")

local World = {}

local WALL_BOTTOM = -120
local WALL_TOP = 1100

local function walls(parent, meta)
	local f = B.folder(parent, "Boundary")
	local n = 0
	for _, ring in meta.walls do
		for i = 1, #ring - 1 do
			local a = ring[i]
			local b = ring[i + 1]
			local dx, dz = b[1] - a[1], b[2] - a[2]
			local L = math.sqrt(dx * dx + dz * dz)
			if L > 0.5 then
				local yaw = math.atan2(-dx, -dz)
				local cf = CFrame.new((a[1] + b[1]) / 2, (WALL_BOTTOM + WALL_TOP) / 2, (a[2] + b[2]) / 2) * CFrame.Angles(0, yaw, 0)
				B.part(f, "경계벽", Vector3.new(4, WALL_TOP - WALL_BOTTOM, L + 4), cf, Color3.fromRGB(255, 255, 255), B.MAT.smooth, {
					Transparency = 1,
					CastShadow = false,
					CanQuery = false,
					CanTouch = false,
				})
				n += 1
			end
		end
	end
	return n
end

local function textLabel(parent, text, pos, size, color, font)
	local l = Instance.new("TextLabel")
	l.BackgroundTransparency = 1
	l.Text = text
	l.Position = pos
	l.Size = size
	l.TextColor3 = color
	l.TextScaled = true
	l.Font = font or Enum.Font.GothamBold
	l.Parent = parent
	return l
end

local function spawnArea(parent, meta, version)
	local s = meta.spawn
	local f = B.folder(parent, "Spawn")
	local pad = Instance.new("SpawnLocation")
	pad.Name = "시청앞스폰"
	pad.Anchored = true
	pad.Size = Vector3.new(16, 1, 16)
	pad.CFrame = CFrame.new(s.x, s.y + 0.5, s.z)
	pad.Neutral = true
	pad.Duration = 0
	pad.Material = Enum.Material.SmoothPlastic
	pad.Color = Color3.fromRGB(210, 210, 205)
	pad.TopSurface = Enum.SurfaceType.Smooth
	pad.Parent = f

	-- 안내판 (지도 출처 표시 포함)
	local board = B.part(f, "안내판", Vector3.new(14, 7, 0.6), CFrame.new(s.x, s.y + 5.5, s.z - 12) * CFrame.Angles(0, math.pi, 0),
		B.COLOR.sign, B.MAT.smooth)
	B.part(f, "안내판기둥", Vector3.new(0.8, 2.2, 0.8), CFrame.new(s.x, s.y + 1.1, s.z - 12), B.COLOR.rail, B.MAT.metal)
	local gui = Instance.new("SurfaceGui")
	gui.Face = Enum.NormalId.Front
	gui.SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud
	gui.PixelsPerStud = 40
	gui.LightInfluence = 0
	gui.Parent = board
	local white = Color3.fromRGB(255, 255, 255)
	textLabel(gui, "서울특별시", UDim2.fromScale(0.05, 0.08), UDim2.fromScale(0.9, 0.42), white)
	textLabel(gui, "지도 데이터 © OpenStreetMap contributors (ODbL)", UDim2.fromScale(0.05, 0.56), UDim2.fromScale(0.9, 0.16), white, Enum.Font.Gotham)
	textLabel(gui, "축척 1/10 · 맵 버전 " .. version, UDim2.fromScale(0.05, 0.76), UDim2.fromScale(0.9, 0.14), white, Enum.Font.Gotham)
end

local function sites(parent, meta)
	local f = B.folder(parent, "Sites")
	for _, lm in meta.landmarks do
		local p = B.part(f, lm.name, Vector3.new(4, 4, 4), CFrame.new(lm.x, lm.y + 2, lm.z), Color3.fromRGB(255, 200, 0), B.MAT.smooth, {
			Transparency = 1,
			CanCollide = false,
			CanQuery = false,
			CanTouch = false,
			CastShadow = false,
		})
		p:SetAttribute("Group", lm.group)
		p:SetAttribute("Radius", lm.r)
	end
	local fz = meta.facilityZone
	local z = B.part(f, "도심 시설 구역", Vector3.new(4, 4, 4), CFrame.new(fz.x, 2, fz.z), Color3.fromRGB(255, 80, 80), B.MAT.smooth, {
		Transparency = 1,
		CanCollide = false,
		CanQuery = false,
		CanTouch = false,
		CastShadow = false,
	})
	z:SetAttribute("Radius", fz.r)
	for i, farm in meta.farms do
		local p = B.part(f, "농장 " .. i, Vector3.new(4, 4, 4), CFrame.new(farm.x, 2, farm.z), Color3.fromRGB(120, 200, 80), B.MAT.smooth, {
			Transparency = 1,
			CanCollide = false,
			CanQuery = false,
			CanTouch = false,
			CastShadow = false,
		})
		p:SetAttribute("Group", "농장")
		p:SetAttribute("Area", farm.area)
	end
end

local function billboard(parent, name, pos, text, size, color, height, maxDistance)
	local anchor = B.part(parent, name, Vector3.new(1, 1, 1), CFrame.new(pos.X, pos.Y + height, pos.Z), Color3.fromRGB(255, 255, 255), B.MAT.smooth, {
		Transparency = 1,
		CanCollide = false,
		CanQuery = false,
		CanTouch = false,
		CastShadow = false,
	})
	local g = Instance.new("BillboardGui")
	g.Size = UDim2.fromOffset(size * 8, size)
	g.AlwaysOnTop = true
	g.LightInfluence = 0
	g.MaxDistance = maxDistance
	g.Parent = anchor
	local l = textLabel(g, text, UDim2.fromScale(0, 0), UDim2.fromScale(1, 1), color)
	l.TextStrokeTransparency = 0.2
end

local function labels(parent, meta)
	local f = B.folder(parent, "DevLabels")
	for _, d in meta.districts do
		local ring = d.rings[1]
		if ring then
			local sx, sz = 0, 0
			for _, p in ring do
				sx += p[1]
				sz += p[2]
			end
			local short = string.gsub(d.name, "^%S+%s", "")
			billboard(f, d.name, Vector3.new(sx / #ring, 0, sz / #ring), short, 30, Color3.fromRGB(255, 255, 255), 220, 6000)
		end
	end
	for _, lm in meta.landmarks do
		billboard(f, lm.name, Vector3.new(lm.x, lm.y, lm.z), lm.name, 24, Color3.fromRGB(255, 214, 90), 90, 2500)
	end
end

function World.build(parent, meta, version)
	local nWalls = walls(parent, meta)
	spawnArea(parent, meta, version)
	sites(parent, meta)
	labels(parent, meta)
	return nWalls
end

return World
