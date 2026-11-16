package net.createmod.ponder.authoring.capture;

/** Pure finite ownership checks shared by native capture, review and headless regression tests. */
public final class CaptureLease {
    public static final int CLOSE_ANIMATION_GRACE_TICKS = 40;
    public enum State { ARMED, BOUND, CAPTURED, REVIEW, FINISHED }
    public enum Observation { WAIT, REVIEW_READY, CANCEL }
    private Object world, player, container, screen, review;
    private final long startTick;
    private final int timeout;
    private long lastObservedTick, reviewStartedTick;
    private long closingStartedTick = -1;
    private State state = State.ARMED;
    private boolean reviewReady;
    public CaptureLease(Object world, Object player, long tick, int timeout) {
        if (world == null || player == null || tick < 0 || timeout < 1 || timeout > 2400) throw new IllegalArgumentException("Invalid capture lease");
        this.world = world; this.player = player; startTick = lastObservedTick = tick; this.timeout = timeout;
    }
    public State getState() { return state; }
    public boolean ownsWorld(Object world) { return state != State.FINISHED && this.world == world; }
    public boolean owns(Object world, Object player) { return state != State.FINISHED && this.world == world && this.player == player; }
    public boolean bind(Object world, Object player, Object container, Object screen) {
        if (!owns(world, player) || state != State.ARMED || container == null || screen == null) return false;
        this.container = container; this.screen = screen; state = State.BOUND; return true;
    }
    public boolean capture(Object container, Object screen) {
        if (state != State.BOUND || this.container != container || this.screen != screen) return false;
        state = State.CAPTURED; return true;
    }
    public Observation observe(Object world, Object player, Object currentContainer, Object currentScreen, Object playerContainer, long tick) {
        reviewReady = false;
        long started = state == State.REVIEW ? reviewStartedTick : startTick;
        long budget = state == State.REVIEW ? timeout * 10L : timeout;
        if (!owns(world, player) || tick < started || tick - started > budget) return Observation.CANCEL;
        if (closingStartedTick >= 0 && (tick < lastObservedTick || tick - closingStartedTick > CLOSE_ANIMATION_GRACE_TICKS))
            return Observation.CANCEL;
        lastObservedTick = tick;
        if (state == State.REVIEW) return currentScreen == review && currentContainer == playerContainer ? Observation.WAIT : Observation.CANCEL;
        if (state == State.ARMED) return Observation.WAIT;
        if (currentContainer == container && currentScreen == screen)
            return closingStartedTick < 0 ? Observation.WAIT : Observation.CANCEL;
        if (currentContainer == playerContainer && currentScreen == null) {
            reviewReady = state == State.CAPTURED;
            return reviewReady ? Observation.REVIEW_READY : Observation.CANCEL;
        }
        // Closing animations can retain the exact old GUI after vanilla has already closed its server window.
        if (state == State.CAPTURED && currentContainer == playerContainer && currentScreen == screen) {
            if (closingStartedTick < 0) closingStartedTick = tick;
            return Observation.WAIT;
        }
        return Observation.CANCEL;
    }
    public void reviewing(Object screen) {
        if (state != State.CAPTURED || !reviewReady || screen == null) throw new IllegalStateException("Capture must close normally before review");
        container = this.screen = null; closingStartedTick = -1; review = screen; reviewStartedTick = lastObservedTick; state = State.REVIEW;
    }
    public void finish() { world = player = container = screen = review = null; closingStartedTick = -1; reviewReady = false; state = State.FINISHED; }
}
