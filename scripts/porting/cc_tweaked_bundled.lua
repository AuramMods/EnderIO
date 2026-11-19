local function record()
    local otherSides = {}
    for _, side in ipairs(redstone.getSides()) do
        if side ~= "back" then
            otherSides[side] = redstone.getBundledInput(side)
        end
    end
    local sample = {
        bundled = redstone.getBundledInput("back"),
        analog = redstone.getAnalogInput("back"),
        white = redstone.testBundledInput("back", colors.white),
        black = redstone.testBundledInput("back", colors.black),
        red = redstone.testBundledInput("back", colors.red),
        both = redstone.testBundledInput("back", colors.combine(colors.white, colors.black)),
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
