package net.createmod.ponder.foundation.ui;

/** Logical-pixel layout independent of fonts and native windows. */
public final class AuthoringEditorLayout {
    public static final class Rect {
        public final int x, y, width, height;
        Rect(int x, int y, int w, int h) { this.x=x; this.y=y; width=Math.max(0,w); height=Math.max(0,h); }
        public boolean contains(int px,int py) { return px>=x && py>=y && px<x+width && py<y+height; }
    }
    public final boolean compact;
    public final Rect list, preview, properties;
    public AuthoringEditorLayout(int width, int height, int pane) {
        int w = Math.max(1, width), h = Math.max(1, height), top = Math.min(h, 64);
        int body = Math.max(0, h-top-24);
        compact = w < 600;
        if (compact) {
            Rect all = new Rect(8, top, Math.max(0,w-16), body);
            Rect none = new Rect(0,0,0,0);
            list = pane==0 ? all : none; preview = pane==1 ? all : none; properties = pane==2 ? all : none;
        } else {
            int side = Math.min(210, Math.max(140, w/4));
            list = new Rect(8,top,side,body);
            preview = new Rect(side+16,top,w-side*2-32,body);
            properties = new Rect(w-side-8,top,side,body);
        }
    }
}
