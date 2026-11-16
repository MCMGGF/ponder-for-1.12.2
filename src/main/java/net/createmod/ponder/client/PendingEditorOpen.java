package net.createmod.ponder.client;

/** A one-shot editor request must not outlive its world, player, or source screen. */
final class PendingEditorOpen {
    private Request pending;

    void queue(Object world, Object player, Object sourceScreen, boolean returnToSource) {
        if (pending == null && world != null && player != null) {
            pending = new Request(world, player, sourceScreen, returnToSource);
        }
    }

    Request consume(boolean singleplayer, Object world, Object player, Object currentScreen) {
        Request request = pending;
        pending = null;
        if (request == null || !singleplayer || world == null || player == null
            || request.world != world || request.player != player
            || (currentScreen != null && currentScreen != request.sourceScreen)) {
            return null;
        }
        return request;
    }

    void clear() {
        pending = null;
    }

    static final class Request {
        private final Object world;
        private final Object player;
        private final Object sourceScreen;
        private final boolean returnToSource;

        private Request(Object world, Object player, Object sourceScreen, boolean returnToSource) {
            this.world = world;
            this.player = player;
            this.sourceScreen = sourceScreen;
            this.returnToSource = returnToSource;
        }

        Object parentScreen(Object currentScreen) {
            return returnToSource && currentScreen == sourceScreen ? sourceScreen : null;
        }
    }
}
