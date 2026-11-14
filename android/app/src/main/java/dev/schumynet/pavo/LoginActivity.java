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
import android.widget.Switch;
import android.widget.TextView;

import dev.schumynet.pavo.core.MiniJson;
import dev.schumynet.pavo.core.VistaBrowser;

import java.util.Map;

/**
 * Il login, dentro l'app.
 *
 * L'utente entra in Pavo nel WebView; quando il token compare nel
 * {@code localStorage}, lo leggiamo e lo salviamo. La password resta nel
 * WebView: non la vediamo e non la conserviamo.
 *
 * La pagina si presenta come su un computer. Serve: da telefono Pavo mostra la
 * versione mobile, che rimanda alla pagina di scaricamento della sua app invece
 * di far entrare. Con la vista da PC si entra direttamente.
 *
 * Lo user-agent da solo non basta. La pagina sceglie il layout dalla larghezza
 * del viewport, e su un telefono quella larghezza e' stretta: il sito capisce
 * comunque di essere su un telefono. Quindi si impone anche una larghezza da
 * portatile, e il WebView rimpicciolisce il risultato per farlo stare nello
 * schermo.
 *
 * Due cose che rompono l'OAuth dentro una WebView, e come le affrontiamo:
 *
 * 1. Google rifiuta il login se lo user-agent contiene "wv" (WebView).
 * 2. I cookie di terze parti servono al flusso OAuth. Senza, il login si
 *    interrompe a meta'.
 *
 * L'interruttore in basso spegne la vista da PC, se un passaggio del login si
 * comportasse male in quella modalita'.
 */
public class LoginActivity extends Activity {

    private WebView vista;
    private TextView stato;
    private ProgressBar attesa;
    private Sessione sessione;
    private boolean salvato;
    private String uaMobile;

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
        // Il pizzico per ingrandire: in vista PC il testo e' piccolo, e senza
        // questo non c'e' modo di leggerlo.
        impostazioni.setSupportZoom(true);
        impostazioni.setBuiltInZoomControls(true);
        impostazioni.setDisplayZoomControls(false);
        // Senza, il viewport largo non entra nello schermo e la pagina risulta
        // tagliata a destra.
        impostazioni.setLoadWithOverviewMode(true);
        // Senza, il flusso OAuth si interrompe a meta'.
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(vista, true);

        // Google blocca il login se lo user-agent contiene "wv": lo si toglie.
        String ua = impostazioni.getUserAgentString();
        uaMobile = VistaBrowser.pulisci(ua);

        vista.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap icona) {
                attesa.setVisibility(View.VISIBLE);
                // Il viewport va forzato a ogni pagina: il login passa da piu'
                // domini (Pavo, Google), e ognuno porta il suo.
                applicaVista();
                cercaToken();
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                attesa.setVisibility(View.GONE);
                stato.setText(getString(R.string.login_in_corso, url));
                // Anche qui: a inizio pagina il documento puo' non essere
                // ancora pronto a ricevere il viewport.
                applicaVista();
                cercaToken();
            }
        });

        vista.setWebChromeClient(new WebChromeClient());
        vista.addJavascriptInterface(new Ponte(), "pavoPonte");

        Switch desktop = findViewById(R.id.desktop);
        desktop.setChecked(sessione.desktop());
        desktop.setOnCheckedChangeListener((v, attivo) -> {
            sessione.salvaDesktop(attivo);
            // Si ricarica da capo: la scelta non si applica a pagina in corso.
            vista.reload();
        });

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
        // Prima di caricare: lo user-agent deve essere gia' quello giusto,
        // altrimenti il sito rimanda alla pagina di scaricamento dell'app.
        applicaVista();
        vista.loadUrl(AVVIO);
    }

    /**
     * Presenta la pagina come su un computer.
     *
     * Lo user-agent da solo non basta: la pagina decide il layout dalla
     * larghezza del viewport, e su un telefono e' stretta, quindi il sito mostra
     * comunque la versione mobile. Si impone una larghezza da portatile e si
     * lascia che il WebView rimpicciolisca il risultato.
     *
     * Il viewport viene riscritto a ogni pagina, perche' il login attraversa
     * piu' domini e ognuno porta il suo.
     */
    private void applicaVista() {
        if (vista == null) {
            return;
        }
        WebSettings impostazioni = vista.getSettings();

        if (!sessione.desktop()) {
            impostazioni.setUserAgentString(uaMobile);
            impostazioni.setUseWideViewPort(false);
            vista.evaluateJavascript(VistaBrowser.scriptViewportMobile(), null);
            return;
        }

        impostazioni.setUserAgentString(VistaBrowser.UA_DESKTOP);
        impostazioni.setUseWideViewPort(true);

        vista.evaluateJavascript(VistaBrowser.scriptViewport(), null);
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
     * il login in un browser vero, si copia il token, e si passa da qui.
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
