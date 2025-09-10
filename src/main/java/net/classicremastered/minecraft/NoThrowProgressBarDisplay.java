package net.classicremastered.minecraft;

public class NoThrowProgressBarDisplay extends ProgressBarDisplay {

    public NoThrowProgressBarDisplay(Minecraft mc) {
        super(mc); // base class *does* have (Minecraft) ctor
    }

    @Override
    public void setTitle(String title) {
        // Don't render / don't throw. Just remember & log.
        this.title = (title != null) ? title : "";
        System.out.println("[Progress] " + this.title);
    }

    @Override
    public void setText(String text) {
        this.text = (text != null) ? text : "";
        if (!this.text.isEmpty()) {
            System.out.println("[Progress] " + this.text);
        }
        // Don't call setProgress(-1) like the base; avoid GL before Display.create()
    }

    @Override
    public void setProgress(int percent) {
        // Intentionally do nothing (and don't touch any non-existent 'percent' field).
        // Optional: System.out.println("[Progress] " + Math.max(0, Math.min(100, percent)) + "%");
    }
}
