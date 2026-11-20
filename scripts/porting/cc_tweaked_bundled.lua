local input = redstone
if fs.exists("port-relay") then
    while peripheral.getType("bottom") ~= "redstone_relay" do
        os.pullEvent("peripheral")
    end
    input = assert(peripheral.wrap("bottom"))
end

local function record()
    local otherSides = {}
    for _, side in ipairs(redstone.getSides()) do
        if side ~= "back" then
            otherSides[side] = input.getBundledInput(side)
        end
    end
    local sample = {
        bundled = input.getBundledInput("back"),
        analog = input.getAnalogInput("back"),
        white = input.testBundledInput("back", colors.white),
        black = input.testBundledInput("back", colors.black),
        red = input.testBundledInput("back", colors.red),
        both = input.testBundledInput("back", colors.combine(colors.white, colors.black)),
        otherSides = otherSides,
    }
    local log = assert(fs.open("port-input.jsonl", "a"))
    log.writeLine(textutils.serializeJSON(sample))
    log.close()
end

record()
while true do
    os.pullEvent("redstone")
    record()
end
