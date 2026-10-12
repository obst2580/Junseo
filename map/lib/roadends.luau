-- 길 끝 처리
--
-- 맵 경계: 도로는 벽 바깥으로 계속 이어지고(tools/mapdata/roadprofile.py), 벽 바로 안쪽에는
--   빨강·흰 줄무늬 차단막과 '통행금지' 표지판을 세운다. 보이지 않는 벽에 그냥 부딪히지 않게 하려는 것.
-- 막다른 길: 바닷가 길이나 땅으로 내려온 고가 끝처럼 이을 곳이 없는 끝에는 둥근 회차로를 둔다.

local roblox = require("@lune/roblox")
local Instance = roblox.Instance
local Vector3 = roblox.Vector3
local Vector2 = roblox.Vector2
local Color3 = roblox.Color3
local UDim = roblox.UDim
local UDim2 = roblox.UDim2
local ColorSequence = roblox.ColorSequence
local ColorSequenceKeypoint = roblox.ColorSequenceKeypoint
local Enum = roblox.Enum
local B = require("./build")

local RoadEnds = {}

local RED = Color3.fromRGB(214, 32, 38)
local WHITE = Color3.fromRGB(245, 245, 245)
local BLACK = Color3.fromRGB(22, 22, 22)
local POST = Color3.fromRGB(70, 72, 76)

local BOARD_H = 1.4
local BOARD_Y = 2.3 -- 노면에서 판 가운데까지
local POST_STEP = 10
local STRIPE_W = 5 -- 줄무늬 한 묶음의 폭 (스터드)
local SIGN = 4.6
local SIGN_Y = 7.6

-- 빨강·흰 띠를 번갈아 놓은 색 (UIGradient로 비스듬한 줄무늬를 만든다)
local STRIPES = (function()
	local n = 9
	local kps = {}
	for i = 0, n - 1 do
		local c = if i % 2 == 0 then RED else WHITE
		local a = if i == 0 then 0 else i / n + 0.0005
		local b = if i == n - 1 then 1 else (i + 1) / n
		table.insert(kps, ColorSequenceKeypoint.new(a, c))
		table.insert(kps, ColorSequenceKeypoint.new(b, c))
	end
	return ColorSequence.new(kps)
end)()

local function surfaceGui(part, face, pps: number)
	local gui = Instance.new("SurfaceGui")
	gui.Face = face
	gui.SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud
	gui.PixelsPerStud = pps
	gui.LightInfluence = 0.6
	gui.MaxDistance = 600
	gui.Parent = part
	return gui
end

local function frame(parent, pos, size, color)
	local f = Instance.new("Frame")
	f.BorderSizePixel = 0
	f.BackgroundColor3 = color
	f.AnchorPoint = Vector2.new(0.5, 0.5)
	f.Position = pos
	f.Size = size
	f.Parent = parent
	return f
end

local function circle(parent, scale: number, color)
	local f = frame(parent, UDim2.fromScale(0.5, 0.5), UDim2.fromScale(scale, scale), color)
	local c = Instance.new("UICorner")
	c.CornerRadius = UDim.new(0.5, 0)
	c.Parent = f
	return f
end

-- 안쪽(플레이어 쪽)을 보는 면 = Back. 판의 앞(LookVector)은 바깥 방향이다.
local function stripedBoard(parent, center, right, up, f, span)
	local board = B.part(parent, "차단막", Vector3.new(span, BOARD_H, 0.3), B.frame(center, right, up, f), WHITE, B.MAT.smooth)
	local gui = surfaceGui(board, Enum.NormalId.Back, 20)
	local n = math.max(1, math.floor(span / STRIPE_W + 0.5))
	for k = 0, n - 1 do
		local seg = frame(gui, UDim2.fromScale((k + 0.5) / n, 0.5), UDim2.fromScale(1 / n + 0.002, 1), WHITE)
		local g = Instance.new("UIGradient")
		g.Color = STRIPES
		g.Rotation = 35
		g.Parent = seg
	end
	return board
end

local function noEntrySign(parent, center, right, up, f)
	local plate = B.part(parent, "통행금지표지", Vector3.new(SIGN, SIGN, 0.2), B.frame(center, right, up, f), WHITE, B.MAT.smooth, {
		Transparency = 1,
		CastShadow = false,
	})
	local gui = surfaceGui(plate, Enum.NormalId.Back, 40)
	circle(gui, 1, RED)
	circle(gui, 0.8, WHITE)
	local l = Instance.new("TextLabel")
	l.BackgroundTransparency = 1
	l.AnchorPoint = Vector2.new(0.5, 0.5)
	l.Position = UDim2.fromScale(0.5, 0.5)
	l.Size = UDim2.fromScale(0.66, 0.3)
	l.Text = "통행금지"
	l.TextColor3 = BLACK
	l.TextScaled = true
	l.Font = Enum.Font.GothamBlack
	l.Parent = gui
	return plate
end

local function barricade(parent, b)
	local base = Vector3.new(b.x, b.y, b.z)
	local right, _, f = B.basis(base, base + Vector3.new(b.dx, 0, b.dz))
	local up = Vector3.new(0, 1, 0)
	-- 지상 도로는 인도까지, 고가는 방호벽 안쪽까지 막는다
	local span = if b.tier == 0 then b.w + 2 * (if b.w >= 48 then 10 else 8) else b.w - 2.4
	local n = 0
	stripedBoard(parent, base + up * BOARD_Y, right, up, f, span)
	n += 1
	local posts = math.max(2, math.floor(span / POST_STEP) + 1)
	for k = 0, posts - 1 do
		local lat = -span / 2 + 0.4 + (span - 0.8) * k / (posts - 1)
		local q = base + right * lat
		B.column(parent, "차단막기둥", q.X, q.Z, b.y - 0.5, b.y + BOARD_Y + BOARD_H / 2, 0.35, 0.35, B.yawOf(f), POST, B.MAT.metal)
		n += 1
	end
	-- 표지판은 길 가운데, 차단막 바로 뒤
	local sp = base + f * 0.6
	B.column(parent, "표지판기둥", sp.X, sp.Z, b.y - 0.5, b.y + SIGN_Y - SIGN / 2 + 0.2, 0.3, 0.3, B.yawOf(f), POST, B.MAT.metal)
	noEntrySign(parent, sp + up * SIGN_Y - f * 0.2, right, up, f)
	return n + 2
end

function RoadEnds.build(roadsRoot, data)
	local count = 0
	local bf = B.folder(roadsRoot, "Barricades")
	for i, b in data.barriers or {} do
		local f = B.folder(bf, string.format("통행금지 #%d", i))
		count += barricade(f, b)
	end
	local tf = B.folder(roadsRoot, "Turnarounds")
	for _, t in data.turnarounds or {} do
		local p = B.disk(tf, "회차로", Vector3.new(t.x, t.y + 0.03, t.z), t.d, 1.2, B.COLOR.asphalt, B.MAT.asphalt)
		p:SetAttribute("Carve", "junction")
		p:SetAttribute("CarveRadius", t.d / 2 + 9)
		count += 1
	end
	return count, #(data.barriers or {}), #(data.turnarounds or {})
end

return RoadEnds
