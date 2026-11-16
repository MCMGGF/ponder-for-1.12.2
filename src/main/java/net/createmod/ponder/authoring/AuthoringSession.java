package net.createmod.ponder.authoring;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/** One editor's bounded document history, independent of runtime playback and built-in content. */
public final class AuthoringSession {
    public static final int UNDO_LIMIT = 100;
    private AuthoringDocument document;
    private final Deque<AuthoringDocument> undo = new ArrayDeque<AuthoringDocument>();
    private final Deque<AuthoringDocument> redo = new ArrayDeque<AuthoringDocument>();
    private String draftHash, publishedHash, savedDocumentHash;

    public AuthoringSession(AuthoringDocument document) {
        if (document == null) throw new IllegalArgumentException("Document is required");
        this.document = document;
    }

    public static AuthoringSession load(AuthoringStore store, String id) throws IOException {
        AuthoringStore.SavedDocument draft = store.loadDraft(id);
        AuthoringStore.SavedDocument published = store.loadPublished(id);
        if (draft == null && published == null) throw new IOException("Authoring document not found: " + id);
        AuthoringSession session = new AuthoringSession((draft == null ? published : draft).getDocument());
        session.draftHash = draft == null ? null : draft.getHash();
        session.publishedHash = published == null ? null : published.getHash();
        session.savedDocumentHash = session.document.sha256();
        return session;
    }

    public AuthoringDocument getDocument() { return document; }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public boolean isDirty() { return !document.sha256().equals(savedDocumentHash); }
    public String getDraftHash() { return draftHash; }
    public String getPublishedHash() { return publishedHash; }

    public void replace(AuthoringDocument replacement) {
        if (replacement == null) throw new IllegalArgumentException("Document is required");
        if (!document.getId().equals(replacement.getId())) throw new IllegalArgumentException("Use a new session to rename a document");
        if (replacement.sha256().equals(document.sha256())) return;
        undo.addLast(document);
        while (undo.size() > UNDO_LIMIT) undo.removeFirst();
        redo.clear();
        document = replacement;
    }

    public void replaceDocument(AuthoringDocument replacement) { replace(replacement); }
    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.addLast(document); document = undo.removeLast(); return true;
    }
    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.addLast(document); document = redo.removeLast(); return true;
    }
    public AuthoringStore.SavedDocument save(AuthoringStore store) throws IOException {
        AuthoringStore.SavedDocument saved = store.saveDraft(document, draftHash);
        draftHash = saved.getHash(); savedDocumentHash = document.sha256(); return saved;
    }
    public AuthoringStore.SavedDocument publish(AuthoringStore store, AuthoringCompiler.AssetResolver resolver) throws IOException {
        AuthoringStore.SavedDocument published = store.publish(document, publishedHash, resolver);
        publishedHash = published.getHash(); return published;
    }
}
