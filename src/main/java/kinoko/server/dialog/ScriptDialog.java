package kinoko.server.dialog;

import kinoko.server.script.ScriptManager;

public final class ScriptDialog implements Dialog {
    private final ScriptManager scriptManager;

    public ScriptDialog(ScriptManager scriptManager) {
        this.scriptManager = scriptManager;
    }

    public ScriptManager getScriptManager() {
        return scriptManager;
    }

    public static ScriptDialog from(ScriptManager scriptManager) {
        return new ScriptDialog(scriptManager);
    }
}
