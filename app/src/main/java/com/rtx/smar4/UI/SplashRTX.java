package com.rtx.smar4.UI;

/**
 * JNI wrapper for the Infiniti Stream native library (librtx_rebrand.so)
 * This class provides the same JNI method signatures as the original app
 * so the native library can bind correctly.
 *
 * Methods:
 * - performHttpsGet(url): Makes HTTPS GET bypassing Cloudflare, returns response body
 * - nativeDecrypt(hash): Decrypts an encrypted hash string
 * - nativeDecryptName(hash): Decrypts a name hash string
 */
public class SplashRTX {

    public static String _qgdrndckndjdkde;
    public static String _sdgbfsljsbdf;

    public static native String nativeDecrypt(String str);
    public static native String nativeDecryptName(String str);
    public native String performHttpsGet(String str);

    static {
        try {
            System.loadLibrary("rtx_rebrand");
            _qgdrndckndjdkde = "";
        } catch (UnsatisfiedLinkError e) {
            // Native library not available on this architecture
            _qgdrndckndjdkde = "ERROR";
        }
    }

    /**
     * Check if native library is available
     */
    public static boolean isNativeAvailable() {
        return !"ERROR".equals(_qgdrndckndjdkde);
    }
}
