package in.dukkan.common;

public final class ClientSourceHolder {

    private static final ThreadLocal<String> CURRENT_SOURCE = ThreadLocal.withInitial(() -> "O");

    private ClientSourceHolder() {}

    public static String getSource() {
        String val = CURRENT_SOURCE.get();
        return (val != null && val.equalsIgnoreCase("M")) ? "M" : "O";
    }

    public static void setSource(String source) {
        if (source != null && source.trim().equalsIgnoreCase("M")) {
            CURRENT_SOURCE.set("M");
        } else {
            CURRENT_SOURCE.set("O");
        }
    }

    public static void clear() {
        CURRENT_SOURCE.remove();
    }
}
