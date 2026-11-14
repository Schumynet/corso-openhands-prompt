package dev.schumynet.pavo.core;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server MCP su HTTP, dentro l'app.
 *
 * Perche' HTTP e non stdio: uno strumento a riga di comando non puo' parlare
 * con un telefono. Il telefono espone un indirizzo in rete locale, e il client
 * MCP si collega li'.
 *
 * Sicurezza: questo endpoint puo' **spendere crediti**. Quindi ogni richiesta
 * deve portare un token di accoppiamento, generato a caso al primo avvio e
 * mostrato nell'app. Senza, risponde 401. Il token non e' una password utente:
 * serve solo a dire "questa richiesta viene da chi ha letto il codice sullo
 * schermo del telefono".
 */
public final class McpServer {

    public static final String PROTOCOLLO = "2024-11-05";
    private static final String NOME = "pavo-mobile";

    private final ToolRegistry registro;
    private ServerSocket server;
    private Thread filo;
    private volatile boolean inCorso;
    private String tokenAccoppiamento;
    private String ultimoErrore = "";
    private boolean soloLocale;

    public McpServer(ToolRegistry registro) {
        this.registro = registro;
    }

    public boolean attivo() {
        return inCorso;
    }

    public String errore() {
        return ultimoErrore;
    }

    public String token() {
        return tokenAccoppiamento;
    }

    /** true = ascolta solo su 127.0.0.1, false = anche dalla rete locale. */
    public boolean soloLocale() {
        return soloLocale;
    }

    /** Genera un token di accoppiamento nuovo, se non ce n'e' gia' uno. */
    public String generaToken() {
        if (tokenAccoppiamento == null || tokenAccoppiamento.isEmpty()) {
            byte[] b = new byte[24];
            new SecureRandom().nextBytes(b);
            StringBuilder s = new StringBuilder();
            for (byte x : b) {
                s.append(String.format("%02x", x));
            }
            tokenAccoppiamento = s.toString();
        }
        return tokenAccoppiamento;
    }

    public void impostaToken(String t) {
        tokenAccoppiamento = t;
    }

    /**
     * Avvia il server.
     *
     * @param porta      porta di ascolto
     * @param soloLocale true = ascolta solo su 127.0.0.1 (il telefono stesso);
     *                   false = anche dalla rete locale, sempre col token
     */
    public synchronized void avvia(final int porta, boolean soloLocale) throws IOException {
        if (inCorso) {
            return;
        }
        generaToken();
        this.soloLocale = soloLocale;
        // Non uso com.sun.net.httpserver: non esiste su Android. Un socket
        // mio, con un ciclo di accettazione su un filo, e' piu' portabile.
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new java.net.InetSocketAddress(
                soloLocale ? InetAddress.getByName("127.0.0.1") : null, porta), 8);
        inCorso = true;
        filo = new Thread(new Runnable() {
            public void run() {
                ciclo(porta);
            }
        }, "pavo-mcp");
        filo.setDaemon(true);
        filo.start();
    }

    public synchronized void ferma() {
        inCorso = false;
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
                // Chiusura: non c'e' niente da recuperare.
            }
            server = null;
        }
    }

    private void ciclo(int porta) {
        while (inCorso) {
            try {
                final Socket s = server.accept();
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        try {
                            servi(s);
                        } catch (Exception e) {
                            ultimoErrore = String.valueOf(e.getMessage());
                        } finally {
                            try {
                                s.close();
                            } catch (IOException ignored) {
                                // Connessione gia' chiusa.
                            }
                        }
                    }
                }, "pavo-mcp-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (inCorso) {
                    ultimoErrore = "accettazione fallita: " + e.getMessage();
                }
                return;
            }
        }
    }

    // --- HTTP ----------------------------------------------------------------

    private void servi(Socket s) throws IOException {
        s.setSoTimeout(30000);
        BufferedReader in = new BufferedReader(new InputStreamReader(
                s.getInputStream(), StandardCharsets.UTF_8));

        String riga = in.readLine();
        if (riga == null) {
            return;
        }
        String[] parti = riga.split(" ");
        if (parti.length < 2) {
            rispondi(s, 400, "text/plain; charset=utf-8", "richiesta malformata");
            return;
        }
        String metodo = parti[0];
        String percorso = parti[1];

        Map<String, String> intestazioni = new LinkedHashMap<>();
        String h;
        while ((h = in.readLine()) != null && !h.isEmpty()) {
            int due = h.indexOf(':');
            if (due > 0) {
                intestazioni.put(h.substring(0, due).trim().toLowerCase(),
                        h.substring(due + 1).trim());
            }
        }

        int lunghezza = 0;
        String cl = intestazioni.get("content-length");
        if (cl != null) {
            try {
                lunghezza = Integer.parseInt(cl.trim());
            } catch (NumberFormatException ignored) {
                lunghezza = 0;
            }
        }
        StringBuilder corpo = new StringBuilder();
        for (int i = 0; i < lunghezza; i++) {
            int c = in.read();
            if (c < 0) {
                break;
            }
            corpo.append((char) c);
        }

        if (!percorso.startsWith("/mcp")) {
            rispondi(s, 200, "text/plain; charset=utf-8",
                    "Pavo MCP attivo.\nEndpoint MCP: POST /mcp\n"
                            + "Serve l'header: Authorization: Bearer <token>\n");
            return;
        }
        if (!"POST".equalsIgnoreCase(metodo)) {
            rispondi(s, 405, "text/plain; charset=utf-8", "usa POST");
            return;
        }

        String presentato = tokenPresentato(intestazioni);
        if (tokenAccoppiamento != null && !tokenAccoppiamento.isEmpty()
                && !confrontoSicuro(tokenAccoppiamento, presentato)) {
            rispondi(s, 401, "application/json; charset=utf-8",
                    erroreJsonTesto(null, -32001,
                            "token di accoppiamento mancante o errato"));
            return;
        }

        Object richiesta;
        try {
            richiesta = MiniJson.leggi(corpo.toString());
        } catch (Exception ex) {
            rispondi(s, 400, "application/json; charset=utf-8",
                    erroreJsonTesto(null, -32700, "JSON non valido: " + ex.getMessage()));
            return;
        }

        Object risposta = elabora(richiesta);
        if (risposta == null) {
            rispondi(s, 202, "text/plain; charset=utf-8", "");
            return;
        }
        rispondi(s, 200, "application/json; charset=utf-8", MiniJson.scrivi(risposta));
    }

    private static String tokenPresentato(Map<String, String> intestazioni) {
        String h = intestazioni.get("authorization");
        if (h == null) {
            h = intestazioni.get("x-pavo-token");
            return h == null ? "" : h.trim();
        }
        String t = h.trim();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return t.substring(7).trim();
        }
        return t;
    }

    /** Confronto a tempo costante: il tempo di risposta non deve rivelare il token. */
    private static boolean confrontoSicuro(String atteso, String presentato) {
        if (presentato == null) {
            return false;
        }
        byte[] a = atteso.getBytes(StandardCharsets.UTF_8);
        byte[] b = presentato.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    // --- JSON-RPC ------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public Object elabora(Object richiesta) {
        if (!(richiesta instanceof Map)) {
            return erroreJson(null, -32600, "richiesta non valida");
        }
        Map<String, Object> req = (Map<String, Object>) richiesta;
        Object id = req.get("id");
        String metodo = MiniJson.testo(req, "method", "");

        if (metodo.isEmpty()) {
            return erroreJson(id, -32600, "manca il metodo");
        }

        // Le notifiche non hanno id e non vogliono risposta.
        boolean notifica = !req.containsKey("id");

        switch (metodo) {
            case "initialize":
                return rispostaJson(id, initialize());
            case "notifications/initialized":
            case "notifications/cancelled":
                return null;
            case "ping":
                return rispostaJson(id, new LinkedHashMap<String, Object>());
            case "tools/list":
                return rispostaJson(id, toolsList());
            case "tools/call":
                return rispostaJson(id, toolsCall(MiniJson.mappa(req, "params")));
            default:
                if (notifica) {
                    return null;
                }
                return erroreJson(id, -32601, "metodo sconosciuto: " + metodo);
        }
    }

    private Map<String, Object> initialize() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", NOME);
        info.put("version", "1.0.0");

        Map<String, Object> capacita = new LinkedHashMap<>();
        capacita.put("tools", new LinkedHashMap<String, Object>());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("protocolVersion", PROTOCOLLO);
        r.put("capabilities", capacita);
        r.put("serverInfo", info);
        return r;
    }

    private Map<String, Object> toolsList() {
        List<Object> fuori = new ArrayList<>();
        for (ToolRegistry.Strumento s : registro.elenco()) {
            Map<String, Object> voce = new LinkedHashMap<>();
            voce.put("name", s.nome);
            voce.put("description", s.descrizione);
            voce.put("inputSchema", s.schema);
            fuori.add(voce);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tools", fuori);
        return r;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolsCall(Map<String, Object> params) {
        String nome = MiniJson.testo(params, "name", "");
        Map<String, Object> args = MiniJson.mappa(params, "arguments");

        ToolRegistry.Strumento s = registro.strumento(nome);
        if (s == null) {
            return contenuto(true, "strumento sconosciuto: " + nome);
        }
        try {
            Object dati = s.funzione.esegui(args);
            String testo = MiniJson.scrivi(dati);
            if (s.consumaCrediti) {
                testo = "ATTENZIONE: questo strumento consuma crediti.\n" + testo;
            }
            return contenuto(false, testo);
        } catch (Exception e) {
            return contenuto(true, "errore: " + e.getMessage());
        }
    }

    /** Il formato di risposta di MCP: una lista di contenuti testuali. */
    private static Map<String, Object> contenuto(boolean errore, String testo) {
        List<Object> contenuti = new ArrayList<>();
        Map<String, Object> voce = new LinkedHashMap<>();
        voce.put("type", "text");
        voce.put("text", testo);
        contenuti.add(voce);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("content", contenuti);
        if (errore) {
            r.put("isError", true);
        }
        return r;
    }

    private static Map<String, Object> rispostaJson(Object id, Object risultato) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        r.put("result", risultato);
        return r;
    }

    /**
     * L'oggetto d'errore JSON-RPC.
     *
     * Restituisce una mappa, non una stringa: il chiamante la serializza una
     * volta sola. Restituendo una stringa si finirebbe con il JSON codificato
     * due volte, e un client vero non riuscirebbe a leggere l'errore.
     */
    private static Map<String, Object> erroreJson(Object id, int codice, String messaggio) {
        Map<String, Object> errore = new LinkedHashMap<>();
        errore.put("code", codice);
        errore.put("message", messaggio);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        r.put("error", errore);
        return r;
    }

    /** Come {@link #erroreJson}, ma gia' serializzato per il corpo HTTP. */
    private static String erroreJsonTesto(Object id, int codice, String messaggio) {
        return MiniJson.scrivi(erroreJson(id, codice, messaggio));
    }

    // --- utilita' HTTP -------------------------------------------------------

    private static void rispondi(Socket s, int codice, String tipo, String corpo)
            throws IOException {
        byte[] b = corpo.getBytes(StandardCharsets.UTF_8);
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(codice).append(' ').append(motivo(codice)).append("\r\n");
        h.append("Content-Type: ").append(tipo).append("\r\n");
        h.append("Content-Length: ").append(b.length).append("\r\n");
        h.append("Connection: close\r\n");
        h.append("\r\n");
        OutputStream out = s.getOutputStream();
        out.write(h.toString().getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }

    private static String motivo(int codice) {
        switch (codice) {
            case 200: return "OK";
            case 202: return "Accepted";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 405: return "Method Not Allowed";
            default:  return "Errore";
        }
    }
}
