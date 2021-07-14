local function assertEquals(expected, val, msg) assert(val == expected, (msg and (msg .. ": ") or "") .. "Got " .. tostring(val) .. ", expected " .. tostring(expected)) end

local function assertLine(stack, line)
	local _, msg = pcall(error, "", stack + 2)
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
	assertEquals(27, info.currentline, "currentline") assertEquals("testing", info.name, "name")
end

local info = debug.getinfo(testing)

assertEquals(26, info.linedefined, "linedefined")
assertEquals(29, info.lastlinedefined, "lastlinedefined")
assertEquals("debug.lua", info.short_src, "short_src")
assertEquals("debug.lua", info.short_src, "short_src")
assertEquals("Lua", info.what, "what")
assertEquals(-1, info.currentline, "currentline")

testing()

do -- Test Lua call stack
	local n = 0
	local function overflow() n = n + 1 overflow() end
	local co = coroutine.create(overflow)
	local result, message = coroutine.resume(co)
	assert(not result)
	assert(message == "debug.lua:44: stack overflow", message)
	assert(n == 32767, ("Called %d times"):format(n))
end

do -- Test Java call stack
	local n = 0
	local function overflow() n = n + 1 local _, err = pcall(overflow) error(err, 0) end
	local co = coroutine.create(overflow)
	local result, message = coroutine.resume(co)
	assert(not result)
	assert(message == "debug.lua:54: stack overflow", message)
	assert(n == 100, ("Called %d times"):format(n))
end

do -- Test Java call stack, but doing something else first.
	local n = 0
	local function overflow() n = n + 1 local _, err = pcall(overflow) error(err, 0) end
	local function fib(x) if x <= 2 then return 1 else return fib(x - 1) * fib(x - 2) end end
	local co = coroutine.create(function()
		fib(10)
		overflow()
	end)

	local result, message = coroutine.resume(co)
	assert(not result)
	assert(message == "debug.lua:64: stack overflow", message)
	assert(n == 100, ("Called %d times"):format(n))
end

-- Check correct getfenv levels
local function foo()
	assertEquals(getfenv(1), getfenv(foo))
	assertEquals(getfenv(2), _G)
end
setfenv(foo, { getfenv = getfenv, print = print, _G = _G, tostring = tostring, pcall = pcall, error = error, assert = assert })
foo()

local function tracebackVerbose(x)
	if x then
		assertLine(1, 87)
		assertLine(2, 92)
		assertLine(3, 97)
		assertLine(4, 100)
	else
		tracebackVerbose(true)
	end
end

debug.sethook(function()
	tracebackVerbose(false)
end, "c");

(function() end)()
