package dev.schumynet.pavo.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client degli endpoint di Pavo.
 *
 * Java puro (solo {@link HttpURLConnection}): gira sia su Android sia su JVM,
 * quindi si puo' collaudare contro l'API vera senza un telefono.
 *
 * Pavo non ha API key: si autentica con il token di sessione del browser, che
 * scade. Oltre ad {@code Authorization} il server pretende cinque header: senza
 * quelli rifiuta la richiesta.
 */
public class PavoClient {

    public static final String BASE_API = "https://api.pixa-ai.work/api/v1";
    public static final String APP_ORIGIN = "https://app.pavo-ai.work/";
    public static final String ESITO_OK = "000000";

    /** Fornitore del token: su Android lo legge dalle preferenze, nei test da una stringa. */
    public interface TokenProvider {
        String token() throws IOException;
    }

    private final TokenProvider fornitore;
    private int timeoutMs = 60000;
    private int ultimoHttp;

    public PavoClient(TokenProvider fornitore) {
        this.fornitore = fornitore;
    }

    public PavoClient(final String tokenFisso) {
        this.fornitore = new TokenProvider() {
            @Override
            public String token() {
                return tokenFisso;
            }
        };
    }

    public int ultimoHttp() {
        return ultimoHttp;
    }

    public void setTimeoutMs(int ms) {
        this.timeoutMs = ms;
    }

    /** Errore dell'API, con il codice di business se c'e'. */
    public static class PavoException extends IOException {
        public final int http;
        public final String codice;

        public PavoException(String messaggio, int http, String codice) {
            super(messaggio);
            this.http = http;
            this.codice = codice;
        }
    }

    // --- trasporto -----------------------------------------------------------

    public Map<String, Object> intestazioni(boolean corpoJson) throws IOException {
        String t = fornitore.token();
        if (t == null || t.trim().isEmpty()) {
            throw new PavoException("nessun token: fai il login", 0, "");
        }
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("Authorization", "Bearer " + t);
        h.put("Accept", "application/json");
        h.put("x-platform", "1");
        h.put("x-app-timezone", "UTC");
        h.put("x-user-language", "en");
        h.put("x-client-time-ms", String.valueOf(System.currentTimeMillis()));
        h.put("Referer", APP_ORIGIN);
        if (corpoJson) {
            h.put("Content-Type", "application/json");
        }
        return h;
    }

    public Object get(String percorso) throws IOException {
        return chiama("GET", percorso, null);
    }

    public Object post(String percorso, Object corpo) throws IOException {
        return chiama("POST", percorso, corpo);
    }

    public Object patch(String percorso, Object corpo) throws IOException {
        return chiama("PATCH", percorso, corpo);
    }

    public Object delete(String percorso) throws IOException {
        return chiama("DELETE", percorso, null);
    }

    public Object chiama(String metodo, String percorso, Object corpo) throws IOException {
        return chiama(metodo, percorso, corpo, null);
    }

    /**
     * Chiamata grezza. Restituisce il campo {@code data} della risposta.
     *
     * Pavo incapsula tutto in {@code {"code": "...", "data": ...}}. Restituire
     * l'involucro intero farebbe cercare i campi al livello sbagliato, e ogni
     * strumento risponderebbe vuoto.
     *
     * Gli errori di business (codice diverso da {@code 000000}) diventano
     * eccezioni, cosi' il chiamante non deve controllarli ogni volta. I 5xx si
     * ritentano: sono quasi sempre temporanei.
     */
    public Object chiama(String metodo, String percorso, Object corpo, Map<String, Object> query)
            throws IOException {
        IOException ultimo = null;
        for (int tentativo = 0; tentativo < 3; tentativo++) {
            try {
                return unaVolta(metodo, percorso, corpo, query);
            } catch (PavoException e) {
                ultimo = e;
                // Solo i 5xx valgono un altro tentativo: un 4xx e' colpa nostra.
                if (e.http < 500 || tentativo == 2) {
                    throw e;
                }
                try {
                    Thread.sleep(300L * (tentativo + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw ultimo == null ? new PavoException("chiamata fallita", 0, "") : ultimo;
    }

    private Object unaVolta(String metodo, String percorso, Object corpo,
                            Map<String, Object> query) throws IOException {
        String url = percorso.startsWith("http") ? percorso : BASE_API + percorso;
        if (query != null && !query.isEmpty()) {
            StringBuilder q = new StringBuilder(url.contains("?") ? "&" : "?");
            boolean primo = true;
            for (Map.Entry<String, Object> e : query.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                if (!primo) {
                    q.append('&');
                }
                primo = false;
                q.append(urlEncode(e.getKey())).append('=')
                 .append(urlEncode(String.valueOf(e.getValue())));
            }
            url = url + q;
        }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod(metodo);
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            for (Map.Entry<String, Object> e : intestazioni(corpo != null).entrySet()) {
                c.setRequestProperty(e.getKey(), String.valueOf(e.getValue()));
            }

            byte[] daInviare = null;
            if (corpo != null) {
                c.setDoOutput(true);
                daInviare = MiniJson.scrivi(corpo).getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(daInviare.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(daInviare);
                }
            }

            ultimoHttp = c.getResponseCode();
            InputStream flusso = ultimoHttp >= 400 ? c.getErrorStream() : c.getInputStream();
            String risposta = flusso == null ? "" : leggi(flusso);

            if (ultimoHttp >= 400) {
                throw new PavoException(
                        "HTTP " + ultimoHttp + ": " + accorcia(risposta),
                        ultimoHttp, codiceDa(risposta));
            }
            if (risposta.trim().isEmpty()) {
                return new LinkedHashMap<String, Object>();
            }
            Object letto = MiniJson.leggi(risposta);

            // Pavo incapsula: {"code":"000000","data":{...}}. Si restituisce il
            // contenuto di `data`, non l'involucro, altrimenti ogni strumento
            // cercherebbe i campi al livello sbagliato.
            if (letto instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mappa = (Map<String, Object>) letto;
                if (mappa.containsKey("code")) {
                    String codice = MiniJson.testo(mappa, "code", "");
                    if (!codice.isEmpty() && !ESITO_OK.equals(codice)) {
                        throw new PavoException(
                                "Pavo ha risposto " + codice + ": "
                                        + MiniJson.testo(mappa, "message", accorcia(risposta)),
                                ultimoHttp, codice);
                    }
                    Object dati = mappa.get("data");
                    return dati == null ? new LinkedHashMap<String, Object>() : dati;
                }
            }
            return letto;
        } finally {
            c.disconnect();
        }
    }

    private static String leggi(InputStream in) throws IOException {
        try (InputStream s = in; ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0) {
                b.write(buf, 0, n);
            }
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String codiceDa(String risposta) {
        try {
            return MiniJson.testo(MiniJson.leggi(risposta), "code", "");
        } catch (Exception e) {
            return "";
        }
    }

    private static String accorcia(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    // --- lettura -------------------------------------------------------------

    public Map<String, Object> saldo() throws IOException {
        return MiniJson.mappa(get("/subscription/credits-balance"), "");
    }

    public List<Object> modelli() throws IOException {
        Object d = get("/pixa/models");
        List<Object> l = MiniJson.lista(d, "models");
        return l.isEmpty() ? MiniJson.lista(d, "items") : l;
    }

    public Map<String, Object> costoStimato(String modelCode, String modelType, Object extra)
            throws IOException {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("model_code", modelCode);
        corpo.put("model_type", modelType);
        if (extra instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) extra;
            corpo.putAll(m);
        }
        return MiniJson.mappa(post("/aigc/credits/estimate", corpo), "");
    }

    public List<Object> elencaProgetti(int pagina, int quanti) throws IOException {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("page", pagina);
        q.put("page_size", quanti);
        Object d = chiama("GET", "/pixa/short-drama/projects", null, q);
        List<Object> l = MiniJson.lista(d, "items");
        return l.isEmpty() ? MiniJson.lista(d, "list") : l;
    }

    public Map<String, Object> strumentiCanvas() throws IOException {
        return MiniJson.mappa(get("/pixa/canvas/tool-specs"), "");
    }

    public Map<String, Object> tokenStato() throws IOException {
        Map<String, Object> r = new LinkedHashMap<>();
        String t;
        try {
            t = fornitore.token();
        } catch (IOException e) {
            r.put("valido", false);
            r.put("motivo", "nessun token: fai il login");
            return r;
        }
        if (t == null || t.trim().isEmpty()) {
            r.put("valido", false);
            r.put("motivo", "nessun token: fai il login");
            return r;
        }
        r.putAll(scadenzaToken(t));
        return r;
    }

    // --- token ---------------------------------------------------------------

    /** Scadenza del token, senza verificarne la firma: basta il payload base64. */
    public static Map<String, Object> scadenzaToken(String token) {
        Map<String, Object> r = new LinkedHashMap<>();
        try {
            String[] parti = token.split("\\.");
            if (parti.length < 2) {
                r.put("valido", true);
                r.put("scadenza", "sconosciuta");
                return r;
            }
            String p = parti[1];
            while (p.length() % 4 != 0) {
                p = p + "=";
            }
            byte[] grezzo = java.util.Base64.getUrlDecoder().decode(p);
            Map<String, Object> payload = MiniJson.oggetto(new String(grezzo, StandardCharsets.UTF_8));
            double exp = MiniJson.numero(payload, "exp", 0);
            if (exp > 0) {
                long secondi = (long) exp;
                long adesso = System.currentTimeMillis() / 1000L;
                r.put("valido", secondi > adesso);
                r.put("scadenza", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'")
                        .format(new java.util.Date(secondi * 1000L)));
                r.put("giorni_rimasti", Math.max(0, (secondi - adesso) / 86400.0));
                Object uid = MiniJson.cerca(payload, "user_id");
                if (uid == null) {
                    uid = MiniJson.cerca(payload, "sub");
                }
                if (uid != null) {
                    r.put("utente", String.valueOf(uid));
                }
            } else {
                r.put("valido", true);
                r.put("scadenza", "sconosciuta");
            }
        } catch (Exception e) {
            r.put("valido", true);
            r.put("scadenza", "sconosciuta");
        }
        return r;
    }
}
