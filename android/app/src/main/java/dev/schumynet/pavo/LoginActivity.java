package dev.schumynet.pavo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
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
 * L'utente entra in Pavo; quando il token compare nel {@code localStorage}, lo
 * leggiamo e lo salviamo. La password resta nel browser: non la vediamo e non la
 * conserviamo.
 *
 * La pagina si presenta come su un computer. Serve: da telefono Pavo mostra la
 * versione mobile, che rimanda alla pagina di scaricamento della sua app invece
 * di far entrare. Lo user-agent da solo non basta, perche' il layout lo decide
 * la larghezza del viewport: si impone anche quella.
 *
 * L'accesso si fa con Google. Pavo usa la libreria di Google, che apre il
 * consenso in una FINESTRA NUOVA ({@code window.open}). Senza gestire quella
 * finestra, il tocco sul pulsante non apre niente. Per questo c'e'
 * {@code onCreateWindow}: apre il popup in una finestra dentro l'app.
 *
 * Le due finestre condividono la memoria del browser, quindi quando il popup
 * finisce il login il token si trova gia' li' e la finestra principale lo legge.
 */
public class LoginActivity extends Activity {

    private WebView vista;
    private TextView stato;
    private ProgressBar attesa;
    private Sessione sessione;
    private boolean salvato;
    private String uaMobile;

    /** La finestra del popup di Google, quando e' aperta. */
    private WebView popup;
    private Dialog finestraPopup;

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

        // Lo user-agent si calcola e si applica subito, prima di caricare
        // qualunque cosa. Google decide all'istante: una richiesta partita con
        // lo user-agent del telefono viene rifiutata senza appello, e non
        // basta correggerla dopo.
        uaMobile = VistaBrowser.pulisci(vista.getSettings().getUserAgentString());
        configura(vista);

        vista.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap icona) {
                attesa.setVisibility(View.VISIBLE);
                // Il viewport va forzato a ogni pagina: il login passa da piu'
                // domini (Pavo, Google), e ognuno porta il suo.
                applicaVista(v);
                cercaToken();
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                attesa.setVisibility(View.GONE);
                stato.setText(getString(R.string.login_in_corso, url));
                applicaVista(v);
                cercaToken();
            }
        });

        vista.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView padre, boolean dialogo,
                                          boolean gestoUtente, Message messaggio) {
                apriPopup(messaggio);
                return true;
            }

            @Override
            public void onCloseWindow(WebView finestra) {
                chiudiPopup();
            }
        });

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
        applicaVista(vista);
        vista.loadUrl(AVVIO);
    }

    /**
     * Le impostazioni che servono a ogni finestra, principale o popup.
     *
     * {@code setSupportMultipleWindows} e
     * {@code setJavaScriptCanOpenWindowsAutomatically} sono quelli che rendono
     * possibile il popup di Google. Senza, il pulsante non fa nulla.
     */
    private void configura(WebView v) {
        WebSettings imp = v.getSettings();
        imp.setJavaScriptEnabled(true);
        imp.setDomStorageEnabled(true);
        imp.setDatabaseEnabled(true);
        // Il pizzico per ingrandire: in vista PC il testo e' piccolo.
        imp.setSupportZoom(true);
        imp.setBuiltInZoomControls(true);
        imp.setDisplayZoomControls(false);
        imp.setLoadWithOverviewMode(true);
        imp.setSupportMultipleWindows(true);
        imp.setJavaScriptCanOpenWindowsAutomatically(true);
        // Lo user-agent si imposta QUI, non solo a pagina caricata: la prima
        // richiesta parte prima di qualunque callback, e Google la giudica
        // subito. Con lo user-agent del telefono la rifiuta.
        imp.setUseWideViewPort(sessione.desktop());
        imp.setUserAgentString(VistaBrowser.userAgent(sessione.desktop(), uaMobile));
        // Senza, il flusso OAuth si interrompe a meta'.
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(v, true);
    }

    /**
     * Presenta la pagina come su un computer.
     *
     * Lo user-agent da solo non basta: la pagina decide il layout dalla
     * larghezza del viewport, e su un telefono e' stretta. Si impone una
     * larghezza da portatile e si lascia che il WebView rimpicciolisca il
     * risultato per farlo stare nello schermo.
     */
    private void applicaVista(WebView v) {
        if (v == null) {
            return;
        }
        WebSettings imp = v.getSettings();

        if (!sessione.desktop()) {
            imp.setUserAgentString(uaMobile);
            imp.setUseWideViewPort(false);
            v.evaluateJavascript(VistaBrowser.scriptViewportMobile(), null);
            return;
        }

        imp.setUserAgentString(VistaBrowser.UA_DESKTOP);
        imp.setUseWideViewPort(true);
        v.evaluateJavascript(VistaBrowser.scriptViewport(), null);
    }

    /**
     * Apre il popup di Google in una finestra dentro l'app.
     *
     * La finestra nuova nasce vuota: e' il browser a riempirla con l'indirizzo
     * che il sito ha chiesto di aprire. Va quindi mostrata prima di consegnarla,
     * altrimenti non ha dove disegnare.
     */
    private void apriPopup(Message messaggio) {
        chiudiPopup();

        WebView nuova = new WebView(this);
        configura(nuova);
        nuova.addJavascriptInterface(new Ponte(), "pavoPonte");

        nuova.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                applicaVista(v);
            }
        });

        nuova.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView padre, boolean dialogo,
                                          boolean gestoUtente, Message m) {
                apriPopup(m);
                return true;
            }

            @Override
            public void onCloseWindow(WebView finestra) {
                chiudiPopup();
            }
        });

        finestraPopup = new Dialog(this,
                android.R.style.Theme_Material_Light_NoActionBar);
        finestraPopup.setContentView(nuova, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        finestraPopup.setOnDismissListener(d -> {
            if (popup != null) {
                popup.destroy();
                popup = null;
            }
        });
        stato.setText(R.string.login_google);
        finestraPopup.show();
        sorveglia();

        popup = nuova;
        WebView.WebViewTransport trasporto = (WebView.WebViewTransport) messaggio.obj;
        trasporto.setWebView(nuova);
        messaggio.sendToTarget();
    }

    private void chiudiPopup() {
        if (finestraPopup != null) {
            finestraPopup.dismiss();
            finestraPopup = null;
        }
    }

    /**
     * Sorveglia il popup di Google finche' non ha finito.
     *
     * Con la libreria di Google il popup NON torna sulla pagina di Pavo: si
     * chiude da solo e consegna il risultato alla pagina principale, che poi
     * chiama il server e scrive il token. Quindi non c'e' una pagina da
     * aspettare, e non si ricarica: ricaricare proprio mentre arriva il
     * consenso interromperebbe il login. Si guarda solo il token, a intervalli,
     * per un tempo finito.
     */
    private void sorveglia() {
        final Handler orologio = new Handler(Looper.getMainLooper());
        final int[] giri = {0};

        Runnable controllo = new Runnable() {
            @Override
            public void run() {
                if (salvato || isFinishing()) {
                    return;
                }
                cercaToken();
                if (++giri[0] < 480) {
                    orologio.postDelayed(this, 500);
                }
            }
        };
        orologio.postDelayed(controllo, 1000);
    }

    /**
     * Cerca il token nel localStorage.
     *
     * Il token lo scrive Pavo dopo il login, quindi si prova a ogni cambio di
     * pagina. Le due finestre condividono la memoria, quindi va bene provarci
     * anche dalla principale mentre il popup e' aperto.
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
        chiudiPopup();
        sessione.salvaToken(token);
        // La sessione resta nella memoria del browser: cosi' al prossimo avvio
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
     * Serve quando il provider di login rifiuta il browser incorporato: si fa
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
        if (finestraPopup != null) {
            chiudiPopup();
            return;
        }
        if (vista != null && vista.canGoBack()) {
            vista.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        chiudiPopup();
        super.onDestroy();
    }
}
