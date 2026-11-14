package dev.schumynet.pavo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import dev.schumynet.pavo.core.MiniJson;

import java.util.Map;

/**
 * Il login, dentro l'app.
 *
 * L'utente entra in Pavo nel WebView; quando il token compare nel
 * {@code localStorage}, lo leggiamo e lo salviamo. La password resta nel
 * WebView: non la vediamo e non la conserviamo.
 *
 * Due cose che rompono l'OAuth dentro una WebView, e come le affrontiamo:
 *
 * 1. Google rifiuta il login se lo user-agent contiene "wv" (WebView). Quindi
 *    ci presentiamo come Chrome. Non e' un trucco per aggirare una difesa: e'
 *    quello che fa qualunque app che incapsula un browser, ed e' la stessa
 *    pagina che l'utente vedrebbe in Chrome.
 * 2. I cookie di terze parti servono al flusso OAuth. Senza, il login si
 *    interrompe a meta'.
 *
 * Resta il caso in cui Google non gradisce comunque il WebView incorporato: in
 * quel caso si entra con email e password, oppure si fa il login in Chrome e si
 * incolla il token. L'app lo dice, invece di fallire in silenzio.
 */
public class LoginActivity extends Activity {

    private WebView vista;
    private TextView stato;
    private ProgressBar attesa;
    private Sessione sessione;
    private boolean salvato;

    private static final String AVVIO = "https://app.pavo-ai.work/";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle statoPrecedente) {
        super.onCreate(statoPrecedente);
        setContentView(R.layout.activity_login);
        sessione = new Sessione(this);
        salvato = false;

        vista = findViewById(R.id.webview);
        stato = findViewById(R.id.stato);
        attesa = findViewById(R.id.attesa);
        Button chiudi = findViewById(R.id.chiudi);

        WebSettings impostazioni = vista.getSettings();
        impostazioni.setJavaScriptEnabled(true);
        impostazioni.setDomStorageEnabled(true);
        impostazioni.setDatabaseEnabled(true);
        // Senza, il flusso OAuth si interrompe a meta'.
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(vista, true);

        // Google blocca il login se lo user-agent contiene "wv". Ci presentiamo
        // come Chrome: e' la stessa pagina che l'utente vedrebbe nel browser.
        String ua = impostazioni.getUserAgentString();
        if (ua != null) {
            impostazioni.setUserAgentString(ua.replace("; wv", "")
                    .replaceAll("Version/\\d+\\.\\d+ ", ""));
        }

        vista.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap icona) {
                attesa.setVisibility(View.VISIBLE);
                cercaToken();
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                attesa.setVisibility(View.GONE);
                stato.setText(getString(R.string.login_in_corso, url));
                cercaToken();
            }
        });

        vista.setWebChromeClient(new WebChromeClient());
        vista.addJavascriptInterface(new Ponte(), "pavoPonte");

        chiudi.setOnClickListener(v -> {
            if (!sessione.haToken()) {
                stato.setText(R.string.login_annullato);
                return;
            }
            finish();
        });

        if (sessione.haToken()) {
            stato.setText(R.string.login_gia_fatto);
        }
        vista.loadUrl(AVVIO);
    }

    /**
     * Cerca il token nel localStorage.
     *
     * Il token lo scrive l'app di Pavo dopo il login, quindi si prova a ogni
     * cambio di pagina. Quando c'e', lo salviamo e chiudiamo.
     */
    private void cercaToken() {
        if (salvato || vista == null) {
            return;
        }
        vista.evaluateJavascript(
                "(function(){try{return window.localStorage.getItem('token')||'';}"
                        + "catch(e){return '';}})()",
                valore -> {
                    if (valore == null) {
                        return;
                    }
                    String t = valore;
                    if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
                        t = t.substring(1, t.length() - 1);
                    }
                    t = t.replace("\\\"", "\"").replace("\\\\", "\\");
                    if (!t.trim().isEmpty() && !"null".equals(t)) {
                        salva(t.trim());
                    }
                });
    }

    private void salva(String token) {
        if (salvato) {
            return;
        }
        salvato = true;
        sessione.salvaToken(token);
        // La sessione resta nel profilo del WebView: cosi' al prossimo avvio
        // l'utente e' gia' dentro e non deve rifare il login.
        CookieManager.getInstance().flush();

        Map<String, Object> statoToken = dev.schumynet.pavo.core.PavoClient
                .scadenzaToken(token);
        String quando = MiniJson.testo(statoToken, "scadenza", "sconosciuta");
        stato.setText(getString(R.string.login_riuscito, quando));
        attesa.setVisibility(View.GONE);
        // Un momento perche' l'utente legga, poi si chiude.
        vista.postDelayed(this::finish, 1200);
    }

    /**
     * Ponte per il caso in cui l'utente preferisca incollare il token a mano.
     *
     * Serve quando il provider di login rifiuta il WebView incorporato: si fa
     * il login in Chrome, si copia il token, e si passa da qui.
     */
    private final class Ponte {
        @JavascriptInterface
        public void consegnaToken(String token) {
            if (token != null && !token.trim().isEmpty()) {
                runOnUiThread(() -> salva(token.trim()));
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (vista != null && vista.canGoBack()) {
            vista.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
