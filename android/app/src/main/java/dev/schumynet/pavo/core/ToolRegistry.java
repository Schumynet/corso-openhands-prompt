package dev.schumynet.pavo.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gli strumenti MCP esposti dall'app.
 *
 * Ogni strumento e' una funzione che riceve argomenti e restituisce JSON.
 * Gli schemi dichiarati qui sono quelli che il client MCP vede: se un
 * argomento non c'e' nello schema, il client non lo manda.
 *
 * Gli strumenti che **consumano crediti** (generazioni) sono separati e
 * marcati: chi chiama deve sapere che spende.
 */
public final class ToolRegistry {

    /** Cosa fa uno strumento. */
    public static final class Strumento {
        public final String nome;
        public final String descrizione;
        public final Map<String, Object> schema;
        public final Funzione funzione;
        public final boolean consumaCrediti;

        public Strumento(String nome, String descrizione, Map<String, Object> schema,
                         Funzione funzione, boolean consumaCrediti) {
            this.nome = nome;
            this.descrizione = descrizione;
            this.schema = schema;
            this.funzione = funzione;
            this.consumaCrediti = consumaCrediti;
        }
    }

    /** Una funzione eseguibile: riceve gli argomenti, restituisce JSON. */
    public interface Funzione {
        Object esegui(Map<String, Object> args) throws Exception;
    }

    private final PavoClient client;
    private final CanvasSpecs specifiche;
    private final Map<String, Strumento> strumenti = new LinkedHashMap<>();

    public ToolRegistry(PavoClient client, CanvasSpecs specifiche) {
        this.client = client;
        this.specifiche = specifiche;
        registraLettura();
        registraCanvas();
        registraScrittura();
        registraGenerazione();
    }

    public Strumento strumento(String nome) {
        return strumenti.get(nome);
    }

    public List<Strumento> elenco() {
        return new ArrayList<>(strumenti.values());
    }

    // --- aiuti per costruire gli schemi --------------------------------------

    private static Map<String, Object> schema(String... coppie) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<Object> richiesti = new ArrayList<>();
        for (int i = 0; i + 2 < coppie.length + 1 && i + 1 < coppie.length; i += 2) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("type", coppie[i + 1]);
            props.put(coppie[i], p);
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", props);
        s.put("required", richiesti);
        return s;
    }

    private void aggiungi(String nome, String descrizione, Map<String, Object> schema,
                          Funzione f) {
        strumenti.put(nome, new Strumento(nome, descrizione, schema, f, false));
    }

    private void aggiungiCosto(String nome, String descrizione, Map<String, Object> schema,
                               Funzione f) {
        strumenti.put(nome, new Strumento(nome, descrizione, schema, f, true));
    }

    private static String testo(Map<String, Object> args, String chiave) {
        Object v = args == null ? null : args.get(chiave);
        return v == null ? "" : String.valueOf(v);
    }

    private static Integer intero(Map<String, Object> args, String chiave) {
        Object v = args == null ? null : args.get(chiave);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String && !((String) v).isEmpty()) {
            try {
                return Integer.valueOf(((String) v).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Map<String, Object> esito(Object dati) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("dati", dati);
        return r;
    }

    private static Map<String, Object> errore(String messaggio) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", false);
        r.put("errore", messaggio);
        return r;
    }

    // --- lettura (non costa, non cambia niente) ------------------------------

    private void registraLettura() {
        aggiungi("token_stato",
                "Stato del token di sessione: valido o scaduto, e quando scade. "
                        + "Chiamalo prima di ogni altra cosa: senza token valido "
                        + "nessuno strumento funziona.",
                schema(), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        return client.tokenStato();
                    }
                });

        aggiungi("saldo_crediti",
                "Crediti disponibili. Guardalo prima di ogni generazione.",
                schema(), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        return client.saldo();
                    }
                });

        aggiungi("elenca_modelli",
                "I modelli del flusso a copione, con tipo immagine o video.",
                schema(), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        return client.modelli();
                    }
                });

        aggiungi("costo_modello",
                "Costo in crediti di una generazione con un dato modello.",
                schema("model_code", "string", "model_type", "string"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        return client.costoStimato(testo(a, "model_code"),
                                testo(a, "model_type"), null);
                    }
                });

        aggiungi("elenca_progetti",
                "I progetti del flusso a copione.",
                schema("pagina", "number", "quanti", "number"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        Integer pag = intero(a, "pagina");
                        Integer q = intero(a, "quanti");
                        return client.elencaProgetti(pag == null ? 1 : pag,
                                q == null ? 20 : q);
                    }
                });

        aggiungi("percorso_sessione",
                "Dove sta il token e se c'e'. Utile per capire se il login e' stato fatto.",
                schema(), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("sessione", Sessione.origine());
                        r.put("token_presente", Sessione.tokenPresente());
                        return r;
                    }
                });
    }

    // --- Canvas --------------------------------------------------------------

    private void registraCanvas() {
        aggiungi("canvas_modelli",
                "I modelli del Canvas per tipo di nodo. tipo: 'video', 'immagine', "
                        + "'audio' o 'testo'. Ogni voce porta i vincoli: durate, "
                        + "rapporti, risoluzioni, modi ammessi.",
                schema("tipo", "string"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        String tipo = testo(a, "tipo");
                        return specifiche.modelli(tipo.isEmpty() ? "video" : tipo);
                    }
                });

        aggiungi("canvas_modi",
                "I modi di generazione di un modello Canvas, con i vincoli.",
                schema("model_code", "string"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        Map<String, Object> m = specifiche.modello(testo(a, "model_code"));
                        if (m == null) {
                            return errore("modello '" + testo(a, "model_code") + "' inesistente");
                        }
                        return m;
                    }
                });

        aggiungi("canvas_valida",
                "Controlla i parametri contro i vincoli di un modello Canvas, PRIMA "
                        + "di generare. Restituisce la lista degli errori: vuota vuol "
                        + "dire che va bene. Serve a non spendere crediti su una "
                        + "richiesta che il server rifiuterebbe.",
                schema("model_code", "string", "durata", "number", "rapporto", "string",
                        "risoluzione", "string", "modo", "string",
                        "immagini", "number", "caratteri", "number"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        String durata = testo(a, "durata");
                        String rapporto = testo(a, "rapporto");
                        String risoluzione = testo(a, "risoluzione");
                        String modo = testo(a, "modo");
                        List<String> errori = specifiche.valida(testo(a, "model_code"),
                                durata.isEmpty() ? null : intero(a, "durata"),
                                rapporto.isEmpty() ? null : rapporto,
                                risoluzione.isEmpty() ? null : risoluzione,
                                modo.isEmpty() ? null : modo,
                                intero(a, "immagini"), intero(a, "caratteri"));
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("ok", errori.isEmpty());
                        r.put("errori", errori);
                        return r;
                    }
                });

        aggiungi("canvas_compatibili",
                "Quali modelli Canvas supportano un certo modo di generazione. "
                        + "Esempio: chi sa allungare un video (video_extend).",
                schema("tipo", "string", "modo", "string"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        return specifiche.compatibili(testo(a, "tipo"), testo(a, "modo"));
                    }
                });

        aggiungi("canvas_stili",
                "Gli stili visivi del Canvas, con codice e nome.",
                schema(), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        return specifiche.stili();
                    }
                });

        aggiungi("canvas_guide",
                "Le guide ufficiali del Canvas, con il prompt che usa Pavo stesso. "
                        + "Sono il miglior modello di scrittura: mostrano come "
                        + "formula le richieste chi ha costruito l'app. "
                        + "tipo: 'video', 'image', 'text' o 'audio'.",
                schema("tipo", "string"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        String tipo = testo(a, "tipo");
                        Map<String, Object> g = specifiche.guide();
                        return tipo.isEmpty() ? g : MiniJson.cerca(g, tipo);
                    }
                });

        aggiungi("canvas_migliora_prompt",
                "Riscrive un prompt in inglese, piu' dettagliato. Gratis: non "
                        + "consuma crediti.",
                schema("testo", "string"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        Map<String, Object> corpo = new LinkedHashMap<>();
                        List<Object> items = new ArrayList<>();
                        Map<String, Object> voce = new LinkedHashMap<>();
                        voce.put("type", "text");
                        voce.put("content", testo(a, "testo"));
                        items.add(voce);
                        corpo.put("items", items);
                        Map<String, Object> r = MiniJson.mappa(
                                client.post("/pixa/canvas/prompt/enhance", corpo), "");
                        List<Object> fuori = MiniJson.lista(r, "items");
                        String prompt = fuori.isEmpty()
                                ? MiniJson.testo(r, "text", "")
                                : MiniJson.testo(fuori.get(0), "content", "");
                        Map<String, Object> esito = new LinkedHashMap<>();
                        esito.put("ok", true);
                        esito.put("prompt", prompt);
                        return esito;
                    }
                });

        aggiungi("canvas_traduciprompt",
                "Traduce un prompt verso il cinese. Nota: la direzione e' sempre "
                        + "en2zh, il parametro di lingua non ha effetto.",
                schema("testo", "string"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        Map<String, Object> corpo = new LinkedHashMap<>();
                        List<Object> items = new ArrayList<>();
                        Map<String, Object> voce = new LinkedHashMap<>();
                        voce.put("type", "text");
                        voce.put("content", testo(a, "testo"));
                        items.add(voce);
                        corpo.put("items", items);
                        Map<String, Object> r = MiniJson.mappa(
                                client.post("/pixa/canvas/prompt/translate", corpo), "");
                        Map<String, Object> esito = new LinkedHashMap<>();
                        esito.put("ok", true);
                        esito.put("testo", MiniJson.testo(r, "text", ""));
                        esito.put("direzione", MiniJson.testo(r, "direction", ""));
                        return esito;
                    }
                });

        aggiungi("canvas_espandi_descrizione",
                "Da un'immagine ricava la descrizione testuale. Serve l'URL "
                        + "dell'immagine, non un testo.",
                schema("image_url", "string", "asset_type", "string"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        Map<String, Object> corpo = new LinkedHashMap<>();
                        corpo.put("asset_type", testo(a, "asset_type").isEmpty()
                                ? "character" : testo(a, "asset_type"));
                        corpo.put("image_url", testo(a, "image_url"));
                        return client.post("/pixa/canvas/assets/description/expand", corpo);
                    }
                });
    }

    // --- scrittura (cambia lo stato, non costa) ------------------------------

    private void registraScrittura() {
        aggiungi("canvas_crea_asset",
                "Crea un asset Canvas: personaggio, scena o oggetto. Gli asset sono "
                        + "cio' che tiene coerenti i personaggi tra una clip e "
                        + "l'altra; nel prompt si richiamano con @. "
                        + "tipo: 'characters', 'scenes' o 'props'.",
                schema("tipo", "string", "nome", "string", "descrizione", "string"),
                new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        String tipo = testo(a, "tipo");
                        if (!"characters".equals(tipo) && !"scenes".equals(tipo)
                                && !"props".equals(tipo)) {
                            return errore("tipo deve essere 'characters', 'scenes' o 'props'");
                        }
                        Map<String, Object> corpo = new LinkedHashMap<>();
                        corpo.put("name", testo(a, "nome"));
                        corpo.put("description", testo(a, "descrizione"));
                        return client.post("/pixa/canvas/assets/" + tipo, corpo);
                    }
                });

        aggiungi("canvas_annulla",
                "Annulla una generazione Canvas in corso.",
                schema("corpo_json", "string"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        return client.post("/pixa/canvas/generation/cancel", corpoDa(a));
                    }
                });

        aggiungi("canvas_avanzamento",
                "Stato di una generazione Canvas. Il Canvas lavora in asincrono: la "
                        + "generazione non e' finita quando la chiamata risponde.",
                schema("corpo_json", "string"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        return client.post("/pixa/canvas/generation/progress", corpoDa(a));
                    }
                });

        aggiungi("chiamata_grezza",
                "Chiamata grezza a un endpoint Pavo. Usala solo se uno strumento "
                        + "dedicato non esiste: molti endpoint non sono documentati.",
                schema("metodo", "string", "percorso", "string", "corpo_json", "string"),
                new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        String metodo = testo(a, "metodo").toUpperCase();
                        Object corpo = corpoDa(a);
                        if ("GET".equals(metodo)) {
                            return client.get(testo(a, "percorso"));
                        }
                        return client.chiama(metodo, testo(a, "percorso"), corpo);
                    }
                });
    }

    // --- generazione (CONSUMA CREDITI) ---------------------------------------

    private void registraGenerazione() {
        aggiungiCosto("canvas_quattro_viste",
                "Genera il foglio a quattro viste di un personaggio (frontale, "
                        + "profilo, retro, tre quarti). E' il modo per fissare "
                        + "l'aspetto di un personaggio. CONSUMA CREDITI.",
                schema("corpo_json", "string"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        return client.post("/pixa/canvas/assets/characters/generate-fourview",
                                corpoDa(a));
                    }
                });

        aggiungiCosto("genera_voce",
                "Genera una voce. CONSUMA CREDITI: guarda prima il costo.",
                schema("corpo_json", "string"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        return client.post("/pixa/short-drama/voices/generate", corpoDa(a));
                    }
                });

        aggiungiCosto("genera_storyboard",
                "Genera lo storyboard di un episodio. CONSUMA CREDITI.",
                schema("episode_id", "string"), new Funzione() {
                    public Object esegui(Map<String, Object> a) throws Exception {
                        return client.post("/pixa/short-drama/episodes/"
                                + testo(a, "episode_id") + "/storyboard/generate", null);
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> corpoDa(Map<String, Object> a) {
        String grezzo = testo(a, "corpo_json");
        if (grezzo.trim().isEmpty()) {
            Map<String, Object> vuoto = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : a.entrySet()) {
                if (!"corpo_json".equals(e.getKey()) && !"metodo".equals(e.getKey())
                        && !"percorso".equals(e.getKey())) {
                    vuoto.put(e.getKey(), e.getValue());
                }
            }
            return vuoto;
        }
        try {
            Object letto = MiniJson.leggi(grezzo);
            if (letto instanceof Map) {
                return (Map<String, Object>) letto;
            }
        } catch (Exception e) {
            // Argomento malformato: si prosegue con i campi singoli.
        }
        return new LinkedHashMap<>();
    }

    /**
     * Il ponte verso lo stato della sessione.
     *
     * Il core non sa dove sta il token: su Android lo tiene l'app, nei test una
     * stringa. Qui c'e' solo il punto di aggancio.
     */
    public static final class Sessione {
        private static String origine = "in memoria";
        private static volatile boolean presente = false;

        public static void configura(String dove, boolean cE) {
            origine = dove;
            presente = cE;
        }

        public static String origine() {
            return origine;
        }

        public static boolean tokenPresente() {
            return presente;
        }
    }
}
