import dev.schumynet.pavo.core.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Collaudo del core su JVM.
 *
 * Il core non dipende da Android proprio per questo: si compila ed esegue qui,
 * e si puo' provare contro l'API vera senza un telefono.
 */
public class Prova {

    private static int passati = 0;
    private static int falliti = 0;

    public static void main(String[] args) throws Exception {
        String modalita = args.length > 0 ? args[0] : "tutto";

        System.out.println("=== 1. MiniJson ===");
        provaJson();

        System.out.println();
        System.out.println("=== 2. Specifiche Canvas ===");
        provaCanvas();

        System.out.println();
        System.out.println("=== 3. Server MCP (socket vero) ===");
        provaMcp();

        System.out.println();
        System.out.println("=== 4. Vista da PC ===");
        provaVistaPc();

        if ("tutto".equals(modalita) || "api".equals(modalita)) {
            System.out.println();
            System.out.println("=== 5. Client contro l'API vera ===");
            provaApi();
        }

        System.out.println();
        System.out.println("=====================================");
        System.out.println("  passati: " + passati + "   falliti: " + falliti);
        System.out.println("=====================================");
        if (falliti > 0) {
            System.exit(1);
        }
    }

    private static void verifica(String nome, boolean condizione) {
        if (condizione) {
            passati++;
            System.out.println("  OK   " + nome);
        } else {
            falliti++;
            System.out.println("  FALLITO   " + nome);
        }
    }

    // --- 1. JSON -------------------------------------------------------------

    private static void provaJson() {
        verifica("oggetto semplice",
                MiniJson.testo(MiniJson.leggi("{\"a\":1}"), "a", "").equals("1.0"));
        verifica("intero scritto senza .0",
                MiniJson.scrivi(MiniJson.leggi("{\"a\":1}")).equals("{\"a\":1}"));
        verifica("annidato", MiniJson.testo(
                MiniJson.leggi("{\"a\":{\"b\":[{\"c\":\"x\"}]}}"), "a/b/0/c", "").equals("x"));
        verifica("escape e unicode", MiniJson.testo(
                MiniJson.leggi("{\"t\":\"a\\nb\\u00e8\\\"c\"}"), "t", "").equals("a\nb\u00e8\"c"));
        verifica("booleani e null",
                MiniJson.cerca(MiniJson.leggi("{\"v\":true,\"n\":null}"), "v").equals(Boolean.TRUE)
                        && MiniJson.cerca(MiniJson.leggi("{\"n\":null}"), "n") == null);
        verifica("array vuoto", ((List<?>) MiniJson.leggi("[]")).isEmpty());
        verifica("oggetto vuoto", ((Map<?, ?>) MiniJson.leggi("{}")).isEmpty());
        verifica("numeri negativi e decimali",
                MiniJson.numero(MiniJson.leggi("{\"a\":-1.5}"), "a", 0) == -1.5);
        verifica("esponente", MiniJson.numero(MiniJson.leggi("{\"a\":1e3}"), "a", 0) == 1000.0);

        boolean rifiutato = false;
        try {
            MiniJson.leggi("{\"a\":1");
        } catch (IllegalArgumentException e) {
            rifiutato = true;
        }
        verifica("JSON troncato viene rifiutato", rifiutato);

        rifiutato = false;
        try {
            MiniJson.leggi("{\"a\":1} spazzatura");
        } catch (IllegalArgumentException e) {
            rifiutato = true;
        }
        verifica("caratteri di troppo vengono rifiutati", rifiutato);

        verifica("giro completo scrivi/leggi",
                MiniJson.scrivi(MiniJson.leggi(
                        "{\"items\":[{\"type\":\"text\",\"content\":\"ciao\"}],\"n\":3}"))
                        .equals("{\"items\":[{\"type\":\"text\",\"content\":\"ciao\"}],\"n\":3}"));
    }

    // --- 2. Canvas -----------------------------------------------------------

    private static void provaCanvas() throws Exception {
        Path f = Paths.get("app/src/main/assets/canvas_specs.json");
        if (!Files.isRegularFile(f)) {
            verifica("canvas_specs.json presente", false);
            return;
        }
        CanvasSpecs s = CanvasSpecs.daJson(new String(
                Files.readAllBytes(f), StandardCharsets.UTF_8));

        int video = s.modelli("video").size();
        int img = s.modelli("immagine").size();
        int audio = s.modelli("audio").size();
        int testo = s.modelli("testo").size();
        System.out.println("  modelli: " + video + " video, " + img + " immagine, "
                + audio + " audio, " + testo + " testo");
        verifica("8 modelli video", video == 8);
        verifica("10 modelli immagine", img == 10);
        verifica("2 modelli audio", audio == 2);
        verifica("9 modelli testo", testo == 9);
        verifica("60 stili", s.stili().size() == 60);
        // guide() restituisce le 4 categorie; le voci dentro sono 13 in tutto.
        int vociGuide = 0;
        for (Object categoria : s.guide().values()) {
            vociGuide += (categoria instanceof List) ? ((List<?>) categoria).size() : 1;
        }
        System.out.println("  guide: " + s.guide().size() + " categorie, " + vociGuide + " voci");
        verifica("13 voci di guida", vociGuide == 13);

        // Il caso che fa buttare crediti: 20 secondi su un modello che ne ammette 15.
        List<String> errori = s.valida("seedance-2-0", 20, null, null, null, null, null);
        System.out.println("  seedance-2-0 con 20s -> " + errori);
        verifica("20s rifiutato", !errori.isEmpty());
        verifica("il messaggio dice il limite",
                errori.get(0).contains("4-15s"));

        verifica("10s accettato",
                s.valida("seedance-2-0", 10, null, null, null, null, null).isEmpty());
        verifica("modello inesistente segnalato",
                !s.valida("non-esiste", null, null, null, null, null, null).isEmpty());
        // seedance-2-0 ammette 9:16, 3:4, 1:1, 4:3, 16:9, 21:9: uso 2:1.
        List<String> errRapporto = s.valida("seedance-2-0", null, "2:1", null, null, null, null);
        System.out.println("  rapporto 2:1 -> " + errRapporto);
        verifica("rapporto non ammesso segnalato", !errRapporto.isEmpty());
        verifica("rapporto ammesso accettato",
                s.valida("seedance-2-0", null, "9:16", null, null, null, null).isEmpty());
        verifica("risoluzione non ammessa segnalata",
                !s.valida("seedance-2-0", null, null, "8k", null, null, null).isEmpty());
        verifica("modo non ammesso segnalato",
                !s.valida("seedance-2-0", null, null, null, "modo/inventato", null, null).isEmpty());
        verifica("troppe immagini segnalato",
                !s.valida("seedance-2-0", null, null, null, null, 99, null).isEmpty());
        verifica("testo troppo lungo segnalato",
                !s.valida("seedance-2-0", null, null, null, null, null, 99999).isEmpty());

        List<String> estensori = s.compatibili("video", "video_extend");
        System.out.println("  sanno allungare un video: " + estensori);
        verifica("compatibili() trova qualcuno", !estensori.isEmpty());

        Map<String, Object> m = s.modello("seedance-2-0");
        verifica("modello() restituisce i vincoli",
                m != null && m.get("durate") instanceof List);
    }

    // --- 3. MCP --------------------------------------------------------------

    private static void provaMcp() throws Exception {
        // Un client finto: cosi' il server si prova senza toccare l'API vera.
        PavoClient finto = new PavoClient("token-di-prova") {
            @Override
            public Map<String, Object> saldo() {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("total_balance", 869);
                return r;
            }
        };
        CanvasSpecs specs = CanvasSpecs.daJson(new String(Files.readAllBytes(
                Paths.get("app/src/main/assets/canvas_specs.json")), StandardCharsets.UTF_8));
        ToolRegistry reg = new ToolRegistry(finto, specs);
        McpServer srv = new McpServer(reg);
        srv.impostaToken("token-accoppiamento-prova");

        int porta = 18321;
        srv.avvia(porta, true);
        Thread.sleep(300);
        verifica("server attivo", srv.attivo());

        // Senza token: deve rifiutare.
        Risposta r401 = chiama(porta, null, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        verifica("senza token risponde 401", r401.http == 401);
        System.out.println("  401 -> " + accorcia(r401.corpo, 90));

        // Con token sbagliato: rifiuta.
        Risposta rko = chiama(porta, "sbagliato", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        verifica("token errato risponde 401", rko.http == 401);

        String tok = "token-accoppiamento-prova";

        Risposta init = chiama(porta, tok,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                        + "\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"prova\",\"version\":\"1\"}}}");
        verifica("initialize risponde 200", init.http == 200);
        Map<String, Object> ri = MiniJson.oggetto(init.corpo);
        verifica("nome del server",
                "pavo-mobile".equals(MiniJson.testo(ri, "result/serverInfo/name", "")));
        verifica("versione del protocollo",
                "2024-11-05".equals(MiniJson.testo(ri, "result/protocolVersion", "")));
        System.out.println("  initialize -> " + MiniJson.testo(ri, "result/serverInfo/name", "?"));

        Risposta elenco = chiama(porta, tok, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        List<Object> strumenti = MiniJson.lista(MiniJson.oggetto(elenco.corpo), "result/tools");
        System.out.println("  strumenti: " + strumenti.size());
        verifica("almeno 15 strumenti", strumenti.size() >= 15);

        int conSchema = 0;
        for (Object o : strumenti) {
            if (MiniJson.cerca(o, "inputSchema") instanceof Map) {
                conSchema++;
            }
        }
        verifica("tutti hanno inputSchema", conSchema == strumenti.size());

        Risposta saldo = chiama(porta, tok, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"saldo_crediti\",\"arguments\":{}}}");
        verifica("tools/call risponde 200", saldo.http == 200);
        verifica("saldo_crediti restituisce il valore",
                MiniJson.testo(MiniJson.oggetto(saldo.corpo),
                        "result/content/0/text", "").contains("869"));
        System.out.println("  saldo_crediti -> "
                + accorcia(MiniJson.testo(MiniJson.oggetto(saldo.corpo),
                        "result/content/0/text", ""), 80));

        Risposta valida = chiama(porta, tok, "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"canvas_valida\",\"arguments\":"
                + "{\"model_code\":\"seedance-2-0\",\"durata\":20}}}");
        String testoValida = MiniJson.testo(MiniJson.oggetto(valida.corpo),
                "result/content/0/text", "");
        verifica("canvas_valida rifiuta 20s via MCP", testoValida.contains("4-15s"));

        Risposta ignoto = chiama(porta, tok, "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"non_esiste\",\"arguments\":{}}}");
        verifica("strumento inesistente segnalato come errore",
                Boolean.TRUE.equals(MiniJson.cerca(MiniJson.oggetto(ignoto.corpo), "result/isError")));

        Risposta metodo = chiama(porta, tok,
                "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"metodo/inventato\"}");
        verifica("metodo sconosciuto -> -32601",
                MiniJson.numero(MiniJson.oggetto(metodo.corpo), "error/code", 0) == -32601);

        Risposta rotto = chiama(porta, tok, "{non e' json");
        verifica("JSON non valido -> 400", rotto.http == 400);

        // Le notifiche non vogliono risposta.
        Risposta notifica = chiama(porta, tok,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        verifica("notifica -> 202 senza corpo", notifica.http == 202);

        Risposta indice = chiama(porta, tok, null, "/");
        verifica("pagina di cortesia risponde", indice.http == 200);

        srv.ferma();
        Thread.sleep(200);
        verifica("server fermato", !srv.attivo());
    }

    private static final class Risposta {
        int http;
        String corpo;
    }

    private static Risposta chiama(int porta, String token, String corpo) throws Exception {
        return chiama(porta, token, corpo, "/mcp");
    }

    private static Risposta chiama(int porta, String token, String corpo, String percorso)
            throws Exception {
        HttpURLConnection c = (HttpURLConnection)
                new URL("http://127.0.0.1:" + porta + percorso).openConnection();
        if (corpo == null) {
            c.setRequestMethod("GET");
        } else {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
        }
        if (token != null) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (corpo != null) {
            byte[] b = corpo.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(b.length);
            try (OutputStream o = c.getOutputStream()) {
                o.write(b);
            }
        }
        Risposta r = new Risposta();
        r.http = c.getResponseCode();
        InputStream in = r.http >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) {
            r.corpo = "";
        } else {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
            }
            in.close();
            r.corpo = new String(b.toByteArray(), StandardCharsets.UTF_8);
        }
        c.disconnect();
        return r;
    }

    // --- 4. API vera ---------------------------------------------------------

    private static void provaApi() throws Exception {
        String token = System.getenv("PAVO_TOKEN");
        if (token == null || token.trim().isEmpty()) {
            Path p = Paths.get(System.getenv("PAVO_STATO") != null
                    ? System.getenv("PAVO_STATO")
                    : "/workspace/project/pavo_login/pavo_state.json");
            if (Files.isRegularFile(p)) {
                Object d = MiniJson.leggi(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
                for (Object origine : MiniJson.lista(d, "origins")) {
                    for (Object voce : MiniJson.lista(origine, "localStorage")) {
                        if ("token".equals(MiniJson.testo(voce, "name", ""))) {
                            token = MiniJson.testo(voce, "value", "");
                        }
                    }
                }
            }
        }
        if (token == null || token.trim().isEmpty()) {
            System.out.println("  (nessun token: salto il collaudo contro l'API vera)");
            return;
        }

        PavoClient c = new PavoClient(token);
        Map<String, Object> stato = c.tokenStato();
        System.out.println("  token: " + MiniJson.testo(stato, "scadenza", "?")
                + "  valido=" + MiniJson.testo(stato, "valido", "?"));
        verifica("token valido", Boolean.TRUE.equals(stato.get("valido")));

        Map<String, Object> saldo = c.saldo();
        double crediti = MiniJson.numero(saldo, "total_balance", -1);
        System.out.println("  crediti: " + (long) crediti);
        verifica("il saldo risponde", crediti >= 0);

        List<Object> modelli = c.modelli();
        System.out.println("  modelli: " + modelli.size());
        verifica("25 modelli", modelli.size() == 25);

        List<Object> progetti = c.elencaProgetti(1, 5);
        System.out.println("  progetti: " + progetti.size());
        verifica("i progetti rispondono", progetti.size() > 0);

        Map<String, Object> costo = c.costoStimato("seedream4-5", "image", null);
        System.out.println("  costo seedream4-5: "
                + (long) MiniJson.numero(costo, "required_credits", -1) + " crediti");
        verifica("il costo stimato risponde",
                MiniJson.numero(costo, "required_credits", -1) >= 0);

        // Il percorso d'errore conta quanto quello felice.
        boolean rifiutato = false;
        try {
            new PavoClient("token-palesemente-falso").saldo();
        } catch (IOException e) {
            rifiutato = true;
        }
        verifica("token falso viene rifiutato", rifiutato);
    }

    // --- 4. Vista da PC ------------------------------------------------------

    /**
     * La vista da PC e' la parte che ha gia' fallito una volta in silenzio:
     * con "Android" dentro lo user-agent, Pavo mostrava la versione mobile e
     * rimandava alla pagina di scaricamento della sua app, senza modo di
     * entrare. Qui si controlla che non possa succedere di nuovo.
     */
    private static void provaVistaPc() {
        // Pavo decide cosi': cerca le parole dei telefoni nello user-agent.
        // La stessa prova, rifatta qui.
        String uaPc = dev.schumynet.pavo.core.VistaBrowser.UA_DESKTOP;
        System.out.println("  user-agent: " + uaPc);
        verifica("lo user-agent da PC non contiene Android",
                !uaPc.contains("Android"));
        verifica("lo user-agent da PC non contiene iPhone/iPad/iPod",
                !uaPc.contains("iPhone") && !uaPc.contains("iPad")
                        && !uaPc.contains("iPod"));
        verifica("Pavo non lo scambia per un telefono",
                !dev.schumynet.pavo.core.VistaBrowser.sembraTelefono(uaPc));
        verifica("lo user-agent da PC sembra Chrome da Windows",
                uaPc.contains("Windows NT") && uaPc.contains("Chrome/"));

        // Lo user-agent di un WebView Android deve risultare un telefono: se
        // mai lo si usasse, la prova lo direbbe.
        String uaWebView = "Mozilla/5.0 (Linux; Android 13; SM-G991B Build/TP1A; wv) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 "
                + "Chrome/124.0.0.0 Mobile Safari/537.36";
        verifica("un WebView Android viene riconosciuto come telefono",
                dev.schumynet.pavo.core.VistaBrowser.sembraTelefono(uaWebView));

        // La pulizia deve togliere "wv" (Google rifiuta il login altrimenti)
        // senza intaccare il resto.
        String pulito = dev.schumynet.pavo.core.VistaBrowser.pulisci(uaWebView);
        verifica("la pulizia toglie 'wv'", !pulito.contains("; wv"));
        verifica("la pulizia toglie 'Version/x.y'", !pulito.contains("Version/"));
        verifica("la pulizia non tocca il resto",
                pulito.contains("Chrome/124.0.0.0") && pulito.contains("Android"));

        // Il viewport: senza larghezza da PC, la pagina resta mobile anche con
        // lo user-agent giusto.
        String script = dev.schumynet.pavo.core.VistaBrowser.scriptViewport();
        verifica("lo script impone la larghezza da PC",
                script.contains("width=1280"));
        verifica("lo script crea il viewport se manca",
                script.contains("createElement('meta')"));
        verifica("lo script lascia ingrandire",
                script.contains("user-scalable=yes"));
        verifica("lo script non fissa un ingrandimento sbagliato",
                !script.contains("initial-scale=0.25"));
        verifica("lo script per telefono rimette la larghezza del dispositivo",
                dev.schumynet.pavo.core.VistaBrowser.scriptViewportMobile()
                        .contains("width=device-width"));
    }

    private static String accorcia(String s, int n) {
        if (s == null) {
            return "";
        }
        String pulito = s.replace("\n", " ");
        return pulito.length() <= n ? pulito : pulito.substring(0, n) + "...";
    }
}
