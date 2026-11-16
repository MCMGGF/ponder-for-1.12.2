package net.createmod.ponder.foundation;

/** Immutable identities for deterministic preview-section ownership, never live world objects. */
public final class SectionOwnershipEvent {
    public enum Action { PREPARE, STAGE, PLACE, SHOW, HIDE, MOVE, ROTATE }
    private final Action action;
    private final String sectionId, targetId, revealId;
    private final boolean smartDisplay, visible;

    public SectionOwnershipEvent(Action action, String sectionId, String targetId, String revealId,
        boolean smartDisplay, boolean visible) {
        if (action == null) throw new IllegalArgumentException("Missing section ownership action");
        this.action = action;
        this.sectionId = id(sectionId);
        this.targetId = id(targetId);
        this.revealId = id(revealId);
        this.smartDisplay = smartDisplay;
        this.visible = visible;
        if ((action == Action.SHOW || action == Action.HIDE || action == Action.MOVE || action == Action.ROTATE)
            && this.sectionId.isEmpty()) throw new IllegalArgumentException("Section action requires an identity");
        if ((action == Action.PREPARE || action == Action.STAGE) && this.revealId.isEmpty())
            throw new IllegalArgumentException("Section preparation requires a reveal identity");
        if (!this.targetId.isEmpty() && (action != Action.SHOW || this.targetId.equals(this.sectionId)))
            throw new IllegalArgumentException("Only a new show section may merge onto another identity");
        if ((action == Action.PREPARE || action == Action.STAGE || action == Action.PLACE) && !this.sectionId.isEmpty()
            || action != Action.PREPARE && smartDisplay
            || (action == Action.PLACE || action == Action.MOVE || action == Action.ROTATE || action == Action.HIDE) && !this.revealId.isEmpty()
            || (action == Action.SHOW || action == Action.MOVE || action == Action.ROTATE) && !visible
            || (action == Action.PREPARE || action == Action.STAGE || action == Action.HIDE) && visible)
            throw new IllegalArgumentException("Section metadata fields do not apply to this action");
    }

    private static String id(String value) {
        if (value == null) return "";
        if (!value.isEmpty() && !value.matches("[a-zA-Z0-9_./:-]{1,192}"))
            throw new IllegalArgumentException("Invalid preview section identity");
        return value;
    }
    public Action getAction() { return action; }
    public String getSectionId() { return sectionId; }
    public String getTargetId() { return targetId; }
    public String getRevealId() { return revealId; }
    public boolean isSmartDisplay() { return smartDisplay; }
    public boolean isVisible() { return visible; }
}
