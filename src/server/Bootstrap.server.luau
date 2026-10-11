-- 서버 시작 시 맵 준비.
-- 지형이 구워져 있지 않으면 지금 만든다. Studio에서 미리 구워 두면 이 과정이 생략된다.

local TerrainBuilder = require(script.Parent.TerrainBuilder)

local city = workspace:WaitForChild("City")

-- 개발용 이름표는 게임 중에는 숨긴다
local labels = city:FindFirstChild("DevLabels")
if labels then
	labels:Destroy()
end

if not TerrainBuilder.isBuilt() then
	warn("[맵] 지형이 구워져 있지 않아 지금 생성합니다. Studio에서 미리 구워 두면 서버 시작이 빨라집니다.")
	TerrainBuilder.build({ yield = true })
end
