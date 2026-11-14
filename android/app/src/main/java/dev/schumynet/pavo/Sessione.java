package dev.schumynet.pavo;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;

import dev.schumynet.pavo.core.PavoClient;

/**
 * Il token di sessione, nelle preferenze dell'app.
 *
 * Non finisce mai in chiaro su disco piu' di quanto non faccia il browser, e
 * non esce dal telefono. Il login lo fa l'utente nel WebView: la password non
 * passa da qui.
 */
public final class Sessione implements PavoClient.TokenProvider {

    private static final String ARCHIVIO = "pavo";
    private static final String CHIAVE_TOKEN = "token";
    private static final String CHIAVE_ACCOPPIAMENTO = "token_accoppiamento";
    private static final String CHIAVE_PORTA = "porta";
    private static final String CHIAVE_RETE_LOCALE = "rete_locale";
    private static final String CHIAVE_DESKTOP = "modalita_desktop";

    public static final int PORTA_PREDEFINITA = 8787;

    private final SharedPreferences prefs;

    public Sessione(Context contesto) {
        this.prefs = contesto.getApplicationContext()
                .getSharedPreferences(ARCHIVIO, Context.MODE_PRIVATE);
    }

    // --- token di sessione ---------------------------------------------------

    @Override
    public String token() throws IOException {
        return prefs.getString(CHIAVE_TOKEN, "");
    }

    public boolean haToken() {
        String t = prefs.getString(CHIAVE_TOKEN, "");
        return t != null && !t.trim().isEmpty();
    }

    public void salvaToken(String token) {
        prefs.edit().putString(CHIAVE_TOKEN, token == null ? "" : token).apply();
    }

    public void dimenticaToken() {
        prefs.edit().remove(CHIAVE_TOKEN).apply();
    }

    // --- token di accoppiamento MCP -----------------------------------------

    public String tokenAccoppiamento() {
        return prefs.getString(CHIAVE_ACCOPPIAMENTO, "");
    }

    public void salvaTokenAccoppiamento(String t) {
        prefs.edit().putString(CHIAVE_ACCOPPIAMENTO, t).apply();
    }

    // --- impostazioni del server ---------------------------------------------

    public int porta() {
        return prefs.getInt(CHIAVE_PORTA, PORTA_PREDEFINITA);
    }

    public void salvaPorta(int p) {
        prefs.edit().putInt(CHIAVE_PORTA, p).apply();
    }

    /** true = visibile dalla rete locale, false = solo dal telefono stesso. */
    public boolean reteLocale() {
        return prefs.getBoolean(CHIAVE_RETE_LOCALE, true);
    }

    public void salvaReteLocale(boolean v) {
        prefs.edit().putBoolean(CHIAVE_RETE_LOCALE, v).apply();
    }

    /**
     * true = il WebView di login si presenta come un browser da PC.
     *
     * Di serie e' acceso, e deve restarlo: da telefono Pavo mostra la versione
     * mobile, che rimanda alla pagina di scaricamento della sua app invece di
     * far entrare.
     */
    public boolean desktop() {
        return prefs.getBoolean(CHIAVE_DESKTOP, true);
    }

    public void salvaDesktop(boolean v) {
        prefs.edit().putBoolean(CHIAVE_DESKTOP, v).apply();
    }

}
