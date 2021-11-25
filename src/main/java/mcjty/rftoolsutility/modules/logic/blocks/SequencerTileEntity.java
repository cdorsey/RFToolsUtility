package mcjty.rftoolsutility.modules.logic.blocks;

import mcjty.lib.api.container.DefaultContainerProvider;
import mcjty.lib.bindings.GuiValue;
import mcjty.lib.bindings.Value;
import mcjty.lib.blockcommands.Command;
import mcjty.lib.blockcommands.ServerCommand;
import mcjty.lib.blocks.LogicSlabBlock;
import mcjty.lib.builder.BlockBuilder;
import mcjty.lib.container.ContainerFactory;
import mcjty.lib.container.GenericContainer;
import mcjty.lib.tileentity.Cap;
import mcjty.lib.tileentity.CapType;
import mcjty.lib.tileentity.LogicTileEntity;
import mcjty.lib.typed.Key;
import mcjty.lib.typed.Type;
import mcjty.lib.varia.Sync;
import mcjty.rftoolsbase.tools.ManualHelper;
import mcjty.rftoolsbase.tools.TickOrderHandler;
import mcjty.rftoolsutility.compat.RFToolsUtilityTOPDriver;
import mcjty.rftoolsutility.modules.logic.LogicBlockModule;
import mcjty.rftoolsutility.modules.logic.tools.SequencerMode;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;

import static mcjty.lib.builder.TooltipBuilder.header;
import static mcjty.lib.builder.TooltipBuilder.key;

public class SequencerTileEntity extends LogicTileEntity implements ITickableTileEntity, TickOrderHandler.IOrderTicker {

    private long cycleBits = 0;
    private int currentStep = -1;

    public static final Key<Integer> PARAM_BIT = new Key<>("bit", Type.INTEGER);
    public static final Key<Boolean> PARAM_CHOICE = new Key<>("choice", Type.BOOLEAN);

    private SequencerMode mode = SequencerMode.MODE_ONCE1;
    @GuiValue
    public static final Value<SequencerTileEntity, String> VALUE_MODE = Value.createEnum("mode", SequencerMode.values(), SequencerTileEntity::getMode, SequencerTileEntity::setMode);

    @GuiValue
    private boolean endstate = false;
    @GuiValue
    private int stepcount = 64;
    @GuiValue
    private int delay = 1;

    // For pulse detection.
    private boolean prevIn = false;
    private int timer = 0;

    @Cap(type = CapType.CONTAINER)
    private LazyOptional<INamedContainerProvider> screenHandler = LazyOptional.of(() -> new DefaultContainerProvider<GenericContainer>("Sequencer")
            .containerSupplier((windowId, player) -> new GenericContainer(LogicBlockModule.CONTAINER_SEQUENCER, windowId, ContainerFactory.EMPTY, this))
            .integerListener(Sync.integer(() -> (int) (cycleBits), v -> cycleBits |= v & 0xffffffffL))
            .integerListener(Sync.integer(() -> (int) (cycleBits >> 32), v -> cycleBits |= (((long) v) << 32) & 0xffffffff00000000L))
            .setupSync(this));

    public static LogicSlabBlock createBlock() {
        return new LogicSlabBlock(new BlockBuilder()
                .topDriver(RFToolsUtilityTOPDriver.DRIVER)
                .manualEntry(ManualHelper.create("rftoolsutility:logic/sequencer"))
                .info(key("message.rftoolsutility.shiftmessage"))
                .infoShift(header())
                .tileEntitySupplier(SequencerTileEntity::new));
    }

    public SequencerTileEntity() {
        super(LogicBlockModule.TYPE_SEQUENCER.get());
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
        timer = delay;
        setChanged();
    }

    public int getStepcount() {
        return stepcount;
    }

    public void setStepcount(int stepcount) {
        if (stepcount > 64) {
            stepcount = 64;
        } else if (stepcount < 0) {
            stepcount = 0;
        }
        this.stepcount = stepcount;
        if (this.currentStep >= stepcount) {
            this.currentStep = stepcount - 1;
        }
        setChanged();
    }

    public boolean getEndstate() {
        return endstate;
    }

    public void setEndstate(boolean endstate) {
        this.endstate = endstate;
        setChanged();
    }

    public SequencerMode getMode() {
        return mode;
    }

    public void setMode(SequencerMode mode) {
        this.mode = mode;
        switch (mode) {
            case MODE_ONCE1:
            case MODE_ONCE2:
            case MODE_LOOP3:
            case MODE_LOOP4:
                currentStep = -1;
                break;
            case MODE_LOOP1:
            case MODE_LOOP2:
            case MODE_STEP:
                currentStep = 0;
                break;
        }
        setChanged();
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public boolean getCycleBit(int bit) {
        return ((cycleBits >> bit) & 1) == 1;
    }

    public long getCycleBits() {
        return cycleBits;
    }

    public void setCycleBit(int bit, boolean flag) {
        if (flag) {
            cycleBits |= 1L << bit;
        } else {
            cycleBits &= ~(1L << bit);
        }
        setChanged();
    }

    public void flipCycleBits() {
        cycleBits ^= ~0L;
        setChanged();
    }

    public void clearCycleBits() {
        cycleBits = 0L;
        setChanged();
    }

    @Override
    public void tick() {
        if (!level.isClientSide) {
            TickOrderHandler.queue(this);
        }
    }

    @Override
    public TickOrderHandler.Rank getRank() {
        return TickOrderHandler.Rank.RANK_4;
    }

    @Override
    public void tickServer() {
        boolean pulse = (powerLevel > 0) && !prevIn;
        prevIn = powerLevel > 0;

        if (pulse) {
            handlePulse();
        }

        setChanged();
        timer--;
        if (timer <= 0) {
            timer = delay;
            setRedstoneState(checkOutput() ? 15 : 0);
            handleCycle(powerLevel > 0);
        } else if (timer > delay) {
            timer = delay;
        }
    }

    public boolean checkOutput() {
        return currentStep == -1 ? endstate : getCycleBit(currentStep);
    }

    /**
     * Handle a cycle step.
     *
     * @param redstone true if there is a redstone signal
     */
    private void handleCycle(boolean redstone) {
        switch (mode) {
            case MODE_ONCE1:
            case MODE_ONCE2:
                if (currentStep != -1) {
                    nextStepAndStop();
                }
                break;
            case MODE_LOOP1:
                nextStep();
                break;
            case MODE_LOOP2:
                nextStep();
                break;
            case MODE_LOOP3:
                if (redstone) {
                    nextStep();
                }
                break;
            case MODE_LOOP4:
                if (redstone) {
                    nextStep();
                } else {
                    currentStep = -1;
                }
                break;
            case MODE_STEP:
                break;
        }
    }

    /**
     * Handle the arrival of a new redstone pulse.
     */
    private void handlePulse() {
        switch (mode) {
            case MODE_ONCE1:
                // If we're not doing a cycle then we start one now. Otherwise we do nothing.
                if (currentStep == -1) {
                    currentStep = 0;
                }
                break;
            case MODE_ONCE2:
                // If we're not doing a cycle then we start one now. Otherwise we restart the cycle..
                currentStep = 0;
                break;
            case MODE_LOOP1:
                // Ignore signals
                break;
            case MODE_LOOP2:
                // Set cycle to the start.
                currentStep = 0;
                break;
            case MODE_LOOP3:
            case MODE_LOOP4:
                // Ignore pulses. We just work on redstone signal.
                break;
            case MODE_STEP:
                // Go to next step.
                nextStep();
                break;
        }
    }

    private void nextStep() {
        currentStep++;
        if (currentStep >= stepcount) {
            currentStep = 0;
        }
    }

    private void nextStepAndStop() {
        currentStep++;
        if (currentStep >= stepcount) {
            currentStep = -1;
        }
    }

    @Override
    public void read(CompoundNBT tagCompound) {
        super.read(tagCompound);
        powerOutput = tagCompound.getBoolean("rs") ? 15 : 0;
        currentStep = tagCompound.getInt("step");
        prevIn = tagCompound.getBoolean("prevIn");
        timer = tagCompound.getInt("timer");
    }

    @Override
    public void readInfo(CompoundNBT tagCompound) {
        super.readInfo(tagCompound);
        CompoundNBT info = tagCompound.getCompound("Info");
        if (info.contains("bits")) {
            cycleBits = info.getLong("bits");
        }
        int m = info.getInt("mode");
        mode = SequencerMode.values()[m];
        delay = (short) info.getInt("delay");
        if (delay == 0) {
            delay = 1;
        }
        stepcount = (short) info.getInt("stepCount");
        if (stepcount == 0) {
            stepcount = 64;
        }
        endstate = info.getBoolean("endState");
    }

    @Nonnull
    @Override
    public CompoundNBT save(@Nonnull CompoundNBT tagCompound) {
        super.save(tagCompound);
        tagCompound.putBoolean("rs", powerOutput > 0);
        tagCompound.putInt("step", currentStep);
        tagCompound.putBoolean("prevIn", prevIn);
        tagCompound.putInt("timer", timer);
        return tagCompound;
    }

    @Override
    public void writeInfo(CompoundNBT tagCompound) {
        super.writeInfo(tagCompound);
        CompoundNBT info = getOrCreateInfo(tagCompound);
        info.putLong("bits", cycleBits);
        info.putInt("mode", mode.ordinal());
        info.putInt("delay", delay);
        info.putInt("stepCount", stepcount);
        info.putBoolean("endState", endstate);
    }

    @ServerCommand
    public static final Command<?> CMD_FLIPBITS = Command.<SequencerTileEntity>create("sequencer.flipBits",
            (te, player, params) -> te.flipCycleBits());
    @ServerCommand
    public static final Command<?> CMD_CLEARBITS = Command.<SequencerTileEntity>create("sequencer.clearBits",
            (te, player, params) -> te.clearCycleBits());
    @ServerCommand
    public static final Command<?> CMD_SETBIT = Command.<SequencerTileEntity>create("sequencer.setBit",
            (te, player, params) -> te.setCycleBit(params.get(PARAM_BIT), params.get(PARAM_CHOICE)));
}
