package net.createmod.ponder.foundation.content.oil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounded, isolated teaching ledger, not an MMCE machine or an Ender IO network.
 * Warm-up uses a simplified net 2 C/s with no cooling; each resource uses the
 * representative tank shown here. Transfer speed and steam branch priority are
 * simplified, while audited recipe debits, outputs, durations and conservation
 * remain explicit. No state is shared with another session or the game.
 */
public final class OilLineOperatingSession {
    public enum Station { BOILER, PUMP, TOWER, STORAGE, GENERATOR }
    public enum Action {
        INSTALL_COMPONENTS, ADD_WATER, ADD_COAL, TOGGLE_STEAM_LINE, SURVEY,
        TOGGLE_OIL_OUTPUT, ADD_OIL, TOGGLE_TOWER_STEAM, COLLECT_DIESEL,
        COLLECT_LUBRICANT, COLLECT_BITUMEN, TOGGLE_DIESEL_FEED,
        TOGGLE_ENERGY_OUTPUT, TOGGLE_LOAD, TOGGLE_MIXED_PIPE
    }
    public enum Vessel { BUCKET, CELL }

    public static final int MAX_ADVANCE_TICKS = 24000;
    public static final int TANK_CAPACITY = 16000;
    public static final int OUTPUT_CAPACITY = 5000;
    public static final int OIL_PIPE_CAPACITY = 4000;
    public static final int PUMP_STEAM_CAPACITY = 1280;
    public static final int TOWER_STEAM_CAPACITY = 2560;
    public static final int GENERATOR_CAPACITY = 100000;
    public static final int CAPACITOR_CAPACITY = 1000000;
    public static final int INITIAL_RESERVOIR = 12000;

    private final boolean[] components = new boolean[Station.values().length];
    private final boolean[] goals = new boolean[6];
    private boolean closed;
    private Vessel vessel;
    private long tick;
    private String feedback;
    private boolean steamLine, surveyed, oilOutput, towerSteam, dieselFeed, energyOutput, load, mixedPipe;
    private boolean loadedWater, loadedCoal, safeSteamClosure;
    private int waterSupply, coalSupply, water, coal, fuelTicks, temperatureHundredths, boilerProgress, boilerSteam;
    private int pumpSteam, towerInternalSteam, towerInputSteam, reservoir, pumpProgress, pumpInFlightOil;
    private int pumpOil, pipeOil, oilTank, towerOil, towerProgress, towerDiesel, towerLubricant, towerBitumen;
    private int dieselTank, lubricantTank, bitumenBox, generatorTicks, generatorEnergy, capacitorEnergy;
    private long steamProduced, pumpSteamUsed, towerSteamUsed, oilProduced, oilDeposited, towerBatches;
    private long dieselCollected, lubricantCollected, bitumenCollected, dieselConsumed;
    private long generatedEnergy, exportedEnergy, consumedEnergy;

    public OilLineOperatingSession() { initialize(); }

    private void initialize() {
        Arrays.fill(components, false);
        Arrays.fill(goals, false);
        vessel = Vessel.BUCKET;
        tick = 0;
        feedback = "本轮为隔离操作推演；先准备组件、水与燃料。";
        steamLine = surveyed = oilOutput = towerSteam = dieselFeed = energyOutput = load = mixedPipe = false;
        loadedWater = loadedCoal = safeSteamClosure = false;
        waterSupply = 8000; coalSupply = 8; reservoir = INITIAL_RESERVOIR;
        temperatureHundredths = 2000;
        water = coal = fuelTicks = boilerProgress = boilerSteam = 0;
        pumpSteam = towerInternalSteam = towerInputSteam = pumpProgress = pumpInFlightOil = 0;
        pumpOil = pipeOil = oilTank = towerOil = towerProgress = towerDiesel = towerLubricant = towerBitumen = 0;
        dieselTank = lubricantTank = bitumenBox = generatorTicks = generatorEnergy = capacitorEnergy = 0;
        steamProduced = pumpSteamUsed = towerSteamUsed = oilProduced = oilDeposited = towerBatches = 0;
        dieselCollected = lubricantCollected = bitumenCollected = dieselConsumed = 0;
        generatedEnergy = exportedEnergy = consumedEnergy = 0;
    }

    public void act(Station station, Action action) {
        if (closed) return;
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(action, "action");
        if (!belongs(station, action)) { feedback = "此操作不属于当前站点。"; return; }
        switch (action) {
            case INSTALL_COMPONENTS:
                components[station.ordinal()] = true;
                feedback = station == Station.GENERATOR ? "已安装流体与能源组件。"
                    : station == Station.PUMP ? "已安装流体组件。" : "已安装物品与流体组件。";
                break;
            case ADD_WATER:
                if (!ready(Station.BOILER)) break;
                if (waterSupply < 1000 || water > OUTPUT_CAPACITY - 1000) feedback = "备水不足一桶，或锅炉示例水罐空间不足。";
                else { waterSupply -= 1000; water += 1000; loadedWater = true; feedback = "已向锅炉输入罐加入 1000 mB 水。"; }
                break;
            case ADD_COAL:
                if (!ready(Station.BOILER)) break;
                if (coalSupply == 0) feedback = "本轮示例燃料已用完；重置可重新准备。";
                else { coalSupply--; coal++; loadedCoal = true; feedback = "已向锅炉物品输入加入一份煤。"; }
                break;
            case TOGGLE_STEAM_LINE:
                steamLine = !steamLine;
                feedback = steamLine ? "蒸汽干线已接通：锅炉北侧输出，抽油机北侧输入。" : "蒸汽干线已断开，已有内部汽量保留。";
                break;
            case SURVEY:
                surveyed = true;
                feedback = "本轮井口示例已满足勘探条件；剩余油藏不会因重复确认而补充。";
                break;
            case TOGGLE_OIL_OUTPUT:
                oilOutput = !oilOutput;
                feedback = oilOutput ? "抽油仓下方液体输出已接通，先灌原油管，再到缓冲罐。" : "下方原油输出已关闭，原油暂存仓内。";
                break;
            case ADD_OIL:
                if (!ready(Station.TOWER)) break;
                if (towerOil == 0 && towerInputSteam > 15000) feedback = "四个输入罐已被蒸汽占用；不可凭空删除蒸汽腾出原油槽。请重置并先装油、后短充汽。";
                else if (oilTank < 1000) feedback = "原油缓冲罐不足 1000 mB；累计抽出不等于已到罐，先检查灌管与出口。";
                else if (towerOil > OUTPUT_CAPACITY - 1000) feedback = "原油示例输入罐已满，等待加工留出空间。";
                else { oilTank -= 1000; towerOil += 1000; feedback = "已从缓冲罐取一桶原油，灌入分馏输入罐；仓面关闭不影响此手动操作。"; }
                break;
            case TOGGLE_TOWER_STEAM:
                towerSteam = !towerSteam;
                // A running batch has already reserved its oil and steam, even when the only input bucket is empty.
                if (!towerSteam && (towerProgress > 0
                    || towerOil > 0 && towerInternalSteam >= OilLineRecipeFacts.DISTILL_STEAM)) safeSteamClosure = true;
                feedback = towerSteam ? "分馏仓北侧液体输入已打开；先保留原油槽，充足即关。"
                    : "分馏仓北侧供汽已关闭；内部汽量与其他输入罐的残汽仍保留。";
                break;
            case COLLECT_DIESEL: collect(true); break;
            case COLLECT_LUBRICANT: collect(false); break;
            case COLLECT_BITUMEN:
                if (towerBitumen == 0) feedback = "沥青输出为空，先完成分馏。";
                else { bitumenCollected += towerBitumen; bitumenBox += towerBitumen; towerBitumen = 0; feedback = "沥青已单独移入副产物箱。"; }
                break;
            case TOGGLE_DIESEL_FEED:
                dieselFeed = !dieselFeed;
                feedback = dieselFeed ? "柴油专线已打开，专用柴油罐供给发电仓北侧。" : "柴油供料已关闭；已投入的本轮燃料仍可继续使用。";
                break;
            case TOGGLE_ENERGY_OUTPUT:
                energyOutput = !energyOutput;
                feedback = energyOutput ? "发电仓下方能源输出已接通基础电容库。" : "外部能源输出已关闭；机内缓冲还有空间时仍可短时发电。";
                break;
            case TOGGLE_LOAD:
                load = !load;
                feedback = load ? "示例用电负载已接通，每刻从电容库取 128 FE。" : "示例负载已关闭，电能继续留在电容库。";
                break;
            case TOGGLE_MIXED_PIPE:
                mixedPipe = !mixedPipe;
                feedback = mixedPipe ? "故障已注入：普通液管串液，原油输送被阻止；已有原油不会消失。" : "串液故障已排除，原油可继续输送。";
                break;
            default: throw new AssertionError(action);
        }
        updateGoals();
    }

    private boolean ready(Station station) {
        if (components[station.ordinal()]) return true;
        feedback = "先在这台机器的共享功能仓安装对应组件。";
        return false;
    }

    private boolean belongs(Station station, Action action) {
        switch (action) {
            case INSTALL_COMPONENTS: return station != Station.STORAGE;
            case ADD_WATER: case ADD_COAL: case TOGGLE_STEAM_LINE: return station == Station.BOILER;
            case SURVEY: case TOGGLE_OIL_OUTPUT: return station == Station.PUMP;
            case ADD_OIL: case TOGGLE_TOWER_STEAM: return station == Station.TOWER;
            case COLLECT_DIESEL: case COLLECT_LUBRICANT: case COLLECT_BITUMEN:
                return station == Station.STORAGE || station == Station.TOWER;
            case TOGGLE_DIESEL_FEED: case TOGGLE_ENERGY_OUTPUT: case TOGGLE_LOAD: return station == Station.GENERATOR;
            case TOGGLE_MIXED_PIPE: return station == Station.STORAGE || station == Station.PUMP;
            default: return false;
        }
    }

    private void collect(boolean diesel) {
        int available = diesel ? towerDiesel : towerLubricant;
        int stored = diesel ? dieselTank : lubricantTank;
        int amount = vessel == Vessel.BUCKET ? 1000 : Math.min(1000, available);
        String name = diesel ? "柴油" : "润滑油";
        if (available == 0) { feedback = name + "输出为空，先完成分馏。"; return; }
        if (available < amount) { feedback = name + "不足一整桶；改用通用流体单元，或继续积攒到 1000 mB。"; return; }
        if (TANK_CAPACITY - stored < amount) { feedback = name + "专用罐空间不足，未移动任何液体。"; return; }
        if (diesel) { towerDiesel -= amount; dieselTank += amount; dieselCollected += amount; }
        else { towerLubricant -= amount; lubricantTank += amount; lubricantCollected += amount; }
        feedback = "已将 " + amount + " mB " + name + "转存到专用罐，没有与另一种油混装。";
    }

    public void setVessel(Vessel value) {
        if (closed) return;
        vessel = Objects.requireNonNull(value, "vessel");
        feedback = vessel == Vessel.BUCKET ? "选择普通桶：每次必须整 1000 mB。" : "选择通用流体单元：允许不足 1000 mB 的部分液量。";
    }

    public void advance(int ticks) {
        if (closed) return;
        if (ticks < 0 || ticks > MAX_ADVANCE_TICKS) throw new IllegalArgumentException("Tick budget exceeded");
        if (ticks == 0) return;
        for (int i = 0; i < ticks; i++) {
            tick++;
            boilerTick();
            steamTick();
            moveOil();
            pumpTick();
            moveOil();
            towerTick();
            generatorTick();
            updateGoals();
        }
        feedback = "推演时间 " + tick / 20 + " 秒。" + (mixedPipe ? "普通液管串液，原油输送仍被阻止。" : "各站状态与库存已更新。") ;
    }

    private void boilerTick() {
        if (!components[Station.BOILER.ordinal()]) return;
        if (fuelTicks == 0 && coal > 0) { coal--; fuelTicks = 800; }
        if (fuelTicks == 0) return;
        fuelTicks--;
        temperatureHundredths = Math.min(OilLineRecipeFacts.BOILER_MAX_TEMP * 100, temperatureHundredths + 10);
        int temp = getTemperature();
        int steam = boilerSteamPerBatch();
        if (temp < OilLineRecipeFacts.BOILER_MIN_TEMP || water < steam / 8 || boilerSteam > OUTPUT_CAPACITY - steam) return;
        if (++boilerProgress < 40) return;
        boilerProgress = 0;
        water -= steam / 8;
        boilerSteam += steam;
        steamProduced += steam;
    }

    private int boilerSteamPerBatch() {
        return getTemperature() < OilLineRecipeFacts.BOILER_MIDDLE_BAND_MIN_TEMP ? 80
            : getTemperature() < OilLineRecipeFacts.BOILER_HIGH_BAND_MIN_TEMP ? 120 : 160;
    }

    private void steamTick() {
        if (components[Station.TOWER.ordinal()]) {
            int transfer = Math.min(towerInputSteam, TOWER_STEAM_CAPACITY - towerInternalSteam);
            towerInputSteam -= transfer; towerInternalSteam += transfer;
        }
        if (!steamLine) return;
        if (components[Station.PUMP.ordinal()]) {
            int transfer = Math.min(boilerSteam, PUMP_STEAM_CAPACITY - pumpSteam);
            boilerSteam -= transfer; pumpSteam += transfer;
        }
        if (towerSteam && components[Station.TOWER.ordinal()]) {
            int inputCapacity = towerOil > 0 ? 15000 : 20000;
            int transfer = Math.min(boilerSteam, inputCapacity - towerInputSteam);
            boilerSteam -= transfer; towerInputSteam += transfer;
            transfer = Math.min(towerInputSteam, TOWER_STEAM_CAPACITY - towerInternalSteam);
            towerInputSteam -= transfer; towerInternalSteam += transfer;
        }
    }

    private void moveOil() {
        if (!oilOutput || mixedPipe) return;
        int transfer = Math.min(pumpOil, OIL_PIPE_CAPACITY - pipeOil);
        pumpOil -= transfer; pipeOil += transfer;
        if (pipeOil == OIL_PIPE_CAPACITY) {
            transfer = Math.min(pumpOil, TANK_CAPACITY - oilTank);
            pumpOil -= transfer; oilTank += transfer; oilDeposited += transfer;
        }
    }

    private void pumpTick() {
        if (!components[Station.PUMP.ordinal()] || !surveyed) return;
        if (pumpProgress == 0) {
            int amount = Math.min(OilLineRecipeFacts.PUMP_OIL_MB, reservoir);
            if (amount == 0 || pumpSteam < OilLineRecipeFacts.PUMP_STEAM || pumpOil > OUTPUT_CAPACITY - amount) return;
            pumpSteam -= OilLineRecipeFacts.PUMP_STEAM;
            pumpSteamUsed += OilLineRecipeFacts.PUMP_STEAM;
            reservoir -= amount;
            pumpInFlightOil = amount;
        }
        if (pumpProgress < OilLineRecipeFacts.PUMP_TICKS) pumpProgress++;
        if (pumpProgress == OilLineRecipeFacts.PUMP_TICKS && pumpOil <= OUTPUT_CAPACITY - pumpInFlightOil) {
            pumpOil += pumpInFlightOil; oilProduced += pumpInFlightOil;
            pumpInFlightOil = 0; pumpProgress = 0;
        }
    }

    private void towerTick() {
        if (!components[Station.TOWER.ordinal()]) return;
        if (towerProgress == 0) {
            if (towerOil < OilLineRecipeFacts.DISTILL_OIL_MB || towerInternalSteam < OilLineRecipeFacts.DISTILL_STEAM || !towerHasOutputSpace()) return;
            towerOil -= OilLineRecipeFacts.DISTILL_OIL_MB;
            towerInternalSteam -= OilLineRecipeFacts.DISTILL_STEAM;
            towerSteamUsed += OilLineRecipeFacts.DISTILL_STEAM;
        }
        if (towerProgress < OilLineRecipeFacts.DISTILL_TICKS) towerProgress++;
        if (towerProgress == OilLineRecipeFacts.DISTILL_TICKS && towerHasOutputSpace()) {
            towerDiesel += OilLineRecipeFacts.DISTILL_DIESEL_MB;
            towerLubricant += OilLineRecipeFacts.DISTILL_LUBRICANT_MB;
            towerBitumen += OilLineRecipeFacts.DISTILL_BITUMEN;
            towerBatches++; towerProgress = 0;
            if (safeSteamClosure && !towerSteam) goals[3] = true;
        }
    }

    private boolean towerHasOutputSpace() {
        return towerDiesel <= OUTPUT_CAPACITY - OilLineRecipeFacts.DISTILL_DIESEL_MB
            && towerLubricant <= OUTPUT_CAPACITY - OilLineRecipeFacts.DISTILL_LUBRICANT_MB
            && towerBitumen <= 64 - OilLineRecipeFacts.DISTILL_BITUMEN;
    }

    private void generatorTick() {
        if (!components[Station.GENERATOR.ordinal()]) return;
        if (energyOutput) {
            int amount = Math.min(generatorEnergy, Math.min(OilLineRecipeFacts.GENERATOR_FE_PER_TICK, CAPACITOR_CAPACITY - capacitorEnergy));
            generatorEnergy -= amount; capacitorEnergy += amount; exportedEnergy += amount;
        }
        if (load) {
            int amount = Math.min(capacitorEnergy, 128);
            capacitorEnergy -= amount; consumedEnergy += amount;
        }
        if (generatorTicks == 0 && dieselFeed && dieselTank >= OilLineRecipeFacts.GENERATOR_DIESEL_MB
            && generatorEnergy <= GENERATOR_CAPACITY - OilLineRecipeFacts.GENERATOR_FE_PER_TICK) {
            dieselTank -= OilLineRecipeFacts.GENERATOR_DIESEL_MB;
            dieselConsumed += OilLineRecipeFacts.GENERATOR_DIESEL_MB;
            generatorTicks = OilLineRecipeFacts.GENERATOR_TICKS;
        }
        if (generatorTicks > 0 && generatorEnergy <= GENERATOR_CAPACITY - OilLineRecipeFacts.GENERATOR_FE_PER_TICK) {
            generatorEnergy += OilLineRecipeFacts.GENERATOR_FE_PER_TICK;
            generatedEnergy += OilLineRecipeFacts.GENERATOR_FE_PER_TICK;
            generatorTicks--;
        }
    }

    private void updateGoals() {
        if (loadedWater && loadedCoal && components[Station.BOILER.ordinal()] && getTemperature() >= OilLineRecipeFacts.BOILER_MIN_TEMP) goals[0] = true;
        if (surveyed && oilProduced >= 1000) goals[1] = true;
        if (oilDeposited >= 1000) goals[2] = true;
        if (dieselCollected >= 700 && lubricantCollected >= 200 && bitumenCollected >= 1) goals[4] = true;
        if (generatedEnergy >= OilLineRecipeFacts.energyPerDistillation() && exportedEnergy > 0) goals[5] = true;
    }

    public List<String> getStationStats(Station station) {
        Objects.requireNonNull(station, "station");
        List<String> rows = new ArrayList<>();
        switch (station) {
            case BOILER:
                rows.add("温度（简化）：" + getTemperature() + " / 100℃");
                rows.add("水：" + water + " mB；备水 " + waterSupply);
                rows.add("燃料余时：" + fuelTicks + " 刻；待烧 " + coal);
                rows.add("备用煤：" + coalSupply + " 份");
                rows.add("蒸汽输出罐：" + boilerSteam + " / " + OUTPUT_CAPACITY);
                rows.add("北侧蒸汽干线：" + (steamLine ? "接通" : "断开"));
                break;
            case PUMP:
                rows.add("勘探条件：" + (surveyed ? "已满足" : "未满足"));
                rows.add("油藏余量：" + reservoir + " mB");
                rows.add("内部汽量：" + pumpSteam + " / " + PUMP_STEAM_CAPACITY);
                rows.add("抽取进度：" + pumpProgress + " / " + OilLineRecipeFacts.PUMP_TICKS);
                rows.add("累计抽出：" + oilProduced + " mB");
                rows.add("仓内原油：" + pumpOil + " / " + OUTPUT_CAPACITY);
                rows.add("下方原油出口：" + (oilOutput ? "打开" : "关闭"));
                break;
            case TOWER:
                rows.add("原油输入：" + towerOil + " / " + OUTPUT_CAPACITY);
                rows.add("内部汽量：" + towerInternalSteam + " / " + TOWER_STEAM_CAPACITY);
                rows.add("输入罐残汽：" + towerInputSteam + " mB");
                rows.add("柴油输出：" + towerDiesel + " mB");
                rows.add("润滑油输出：" + towerLubricant + " mB；沥青 " + towerBitumen);
                rows.add("分馏进度：" + towerProgress + " / " + OilLineRecipeFacts.DISTILL_TICKS);
                rows.add("北侧供汽：" + (towerSteam ? "打开" : "关闭"));
                break;
            case STORAGE:
                rows.add("原油管内：" + pipeOil + " / " + OIL_PIPE_CAPACITY);
                rows.add("原油缓冲罐：" + oilTank + " / " + TANK_CAPACITY);
                rows.add("柴油专用罐：" + dieselTank + " / " + TANK_CAPACITY);
                rows.add("润滑油专用罐：" + lubricantTank + " / " + TANK_CAPACITY);
                rows.add("副产物箱：沥青 " + bitumenBox + " 个");
                rows.add("取液容器：" + (vessel == Vessel.BUCKET ? "普通桶（整桶）" : "流体单元（可部分取液）"));
                rows.add("普通管网：" + (mixedPipe ? "串液故障" : "各液体独立"));
                break;
            case GENERATOR:
                rows.add("柴油供料：" + (dieselFeed ? "打开" : "关闭"));
                rows.add("机内储能：" + generatorEnergy + " / " + GENERATOR_CAPACITY);
                rows.add("电容库：" + capacitorEnergy + " / " + CAPACITOR_CAPACITY);
                rows.add("累计发电：" + generatedEnergy + " FE");
                rows.add("负载耗电：" + consumedEnergy + " FE");
                rows.add("本轮燃烧剩余：" + generatorTicks + " 刻");
                rows.add("下方能源输出：" + (energyOutput ? "接通" : "断开"));
                break;
            default: throw new AssertionError(station);
        }
        rows.add("状态：" + getStationStatus(station));
        return Collections.unmodifiableList(rows);
    }

    public String getStationStatus(Station station) {
        if (closed) return "本轮已关闭";
        if (station != Station.STORAGE && !components[station.ordinal()]) return "未安装对应组件";
        switch (station) {
            case BOILER:
                if (fuelTicks == 0 && coal == 0) return "无燃料；水和输出堵塞不会自动停止烧煤";
                if (getTemperature() < OilLineRecipeFacts.BOILER_MIN_TEMP) return "预热中，尚未达到 100℃";
                if (water < boilerSteamPerBatch() / 8) return "当前温度档的产汽用水不足";
                if (boilerSteam > OUTPUT_CAPACITY - boilerSteamPerBatch()) return "检查蒸汽输出空间";
                return "具备产汽条件";
            case PUMP:
                if (!surveyed) return "当前示例井口尚未勘探";
                if (reservoir == 0 && pumpProgress == 0) return "油藏已耗尽，重复勘探不能补油";
                if (pumpProgress > 0) return "正在抽油";
                if (pumpOil > OUTPUT_CAPACITY - Math.min(250, reservoir)) return "仓内原油堵塞，检查下方出口与缓冲罐";
                return pumpSteam < OilLineRecipeFacts.PUMP_STEAM ? "内部汽量不足，检查北侧供汽" : "等待下一轮抽取";
            case TOWER:
                if (towerProgress > 0) return towerHasOutputSpace() ? "正在分馏；开汽不是机器失败条件" : "产物堵塞，先分别移出两种油和沥青";
                if (!towerHasOutputSpace()) return "产物堵塞，先分别移出两种油和沥青";
                if (towerOil < 1000) return towerInputSteam > 15000 ? "蒸汽占用全部输入槽，先装油可避免抢槽" : "原油不足一批，手动灌入输入罐";
                return towerInternalSteam < OilLineRecipeFacts.DISTILL_STEAM ? "内部汽量不足，短时打开北侧供汽" : "等待下一批分馏";
            case STORAGE:
                if (mixedPipe) return "普通液管串液；排除故障后再送原油";
                if (pipeOil < OIL_PIPE_CAPACITY) return "首次仍在灌管，累计抽出不等于到罐";
                return "三种液体分罐，沥青单独入箱";
            case GENERATOR:
                if (generatorEnergy > GENERATOR_CAPACITY - OilLineRecipeFacts.GENERATOR_FE_PER_TICK) return "机内空间不足一刻产电，检查下方输出和电容库";
                if (generatorTicks > 0) return "正在发电；储罐只能平滑供料，不能提高上游产量";
                if (!dieselFeed) return "柴油供料关闭，检查北侧专线";
                return dieselTank < 20 ? "柴油不足 20 mB，原油与润滑油不能代替" : "等待发电";
            default: throw new AssertionError(station);
        }
    }

    public List<String> getGoals() {
        return Collections.unmodifiableList(Arrays.asList("安装锅炉组件、加水加煤并预热至 100℃",
            "满足勘探条件并累计抽出 1000 mB 原油", "灌满原油管后，缓冲罐实际收到 1000 mB",
            "先留原油槽、充足后关汽，再完成一批分馏", "分别收集至少 700 mB 柴油、200 mB 润滑油和 1 沥青",
            "累计发电 179200 FE，并把电输出到电容库"));
    }

    public boolean isGoalComplete(int index) {
        if (index < 0 || index >= goals.length) throw new IndexOutOfBoundsException("goal");
        return goals[index];
    }
    public boolean isComplete() { for (boolean goal : goals) if (!goal) return false; return !closed; }
    public void reset() { if (!closed) initialize(); }
    public void close() {
        if (closed) return;
        initialize();
        waterSupply = coalSupply = reservoir = temperatureHundredths = 0;
        closed = true;
        feedback = "本轮推演已关闭，虚拟物料已清空。";
    }

    public Vessel getVessel() { return vessel; }
    public boolean isComponentsInstalled(Station station) { return components[Objects.requireNonNull(station, "station").ordinal()]; }
    public boolean isSteamLineOpen() { return steamLine; }
    public boolean isSurveyed() { return surveyed; }
    public boolean isOilOutputOpen() { return oilOutput; }
    public boolean isTowerSteamOpen() { return towerSteam; }
    public boolean isDieselFeedOpen() { return dieselFeed; }
    public boolean isEnergyOutputOpen() { return energyOutput; }
    public boolean isLoadEnabled() { return load; }
    public boolean isMixedPipe() { return mixedPipe; }
    public boolean isClosed() { return closed; }
    public long getTick() { return tick; }
    public String getFeedback() { return feedback; }
    public int getTemperature() { return temperatureHundredths / 100; }
    public int getWaterSupply() { return waterSupply; }
    public int getCoalSupply() { return coalSupply; }
    public int getWater() { return water; }
    public int getCoal() { return coal; }
    public int getFuelTicks() { return fuelTicks; }
    public int getBoilerSteam() { return boilerSteam; }
    public int getPumpSteam() { return pumpSteam; }
    public int getTowerInternalSteam() { return towerInternalSteam; }
    public int getTowerInputSteam() { return towerInputSteam; }
    public int getReservoir() { return reservoir; }
    public int getPumpProgress() { return pumpProgress; }
    public int getPumpInFlightOil() { return pumpInFlightOil; }
    public int getPumpOil() { return pumpOil; }
    public int getPipeOil() { return pipeOil; }
    public int getOilTank() { return oilTank; }
    public int getTowerOil() { return towerOil; }
    public int getTowerProgress() { return towerProgress; }
    public int getTowerDiesel() { return towerDiesel; }
    public int getTowerLubricant() { return towerLubricant; }
    public int getTowerBitumen() { return towerBitumen; }
    public int getDieselTank() { return dieselTank; }
    public int getLubricantTank() { return lubricantTank; }
    public int getBitumenBox() { return bitumenBox; }
    public int getGeneratorTicks() { return generatorTicks; }
    public int getGeneratorEnergy() { return generatorEnergy; }
    public int getCapacitorEnergy() { return capacitorEnergy; }
    public long getSteamProduced() { return steamProduced; }
    public long getPumpSteamUsed() { return pumpSteamUsed; }
    public long getTowerSteamUsed() { return towerSteamUsed; }
    public long getOilProduced() { return oilProduced; }
    public long getOilDeposited() { return oilDeposited; }
    public long getTowerBatches() { return towerBatches; }
    public long getDieselCollected() { return dieselCollected; }
    public long getLubricantCollected() { return lubricantCollected; }
    public long getBitumenCollected() { return bitumenCollected; }
    public long getDieselConsumed() { return dieselConsumed; }
    public long getGeneratedEnergy() { return generatedEnergy; }
    public long getExportedEnergy() { return exportedEnergy; }
    public long getConsumedEnergy() { return consumedEnergy; }
}
