local function assertEquals(val, expected) assert(val == expected, "Got " .. tostring(val) .. ", expected " .. tostring(expected)) end

local function assertLine(stack, line)
	local success, msg = pcall(error, "", stack + 2)
	assertEquals("debug.lua:" .. line .. ": ", msg)
end

local function assertStack()
	assertLine(1, 9)
	assertLine(2, 18)
	assertLine(3, 20)
	assertLine(4, 24)
end

local func
func = function(verify)
	if verify then
		assertStack()
	else
		func(true)
	end
end

func(false)

local function testing()
	local info = debug.getinfo(1)
	assertEquals(27, info.currentline, "currentline")
end

local info = debug.getinfo(testing)

assertEquals(26, info.linedefined, "linedefined")
assertEquals(29, info.lastlinedefined, "lastlinedefined")
assertEquals("debug.lua", info.short_src, "short_src")
assertEquals("debug.lua", info.short_src, "short_src")
assertEquals("Lua", info.what, "what")
assertEquals(-1, info.currentline, "currentline")

testing()

local function overflow() overflow() end
local co = coroutine.create(overflow)
local result, message = coroutine.resume(co)
assert(not result)
assert(message == "debug.lua:42: stack overflow", message)
