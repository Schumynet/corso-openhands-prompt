package dev.schumynet.pavo.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le specifiche del Canvas: modelli, vincoli, stili e guide.
 *
 * I dati stanno in {@code assets/canvas_specs.json}, copiati da
 * {@code GET /pixa/canvas/tool-specs}. Si possono aggiornare dall'app.
 *
 * Serve soprattutto a una cosa: dire **prima** se una richiesta sta nei vincoli.
 * Chiedere 20 secondi a un modello che ne ammette 15 e' un giro a vuoto, e i
 * crediti non tornano.
 */
public final class CanvasSpecs {

    private final Map<String, Object> dati;

    public CanvasSpecs(Map<String, Object> dati) {
        this.dati = dati == null ? new LinkedHashMap<String, Object>() : dati;
    }

    public static CanvasSpecs daJson(String json) {
        return new CanvasSpecs(MiniJson.oggetto(json));
    }

    public String versione() {
        return MiniJson.testo(dati, "versione", "sconosciuta");
    }

    // --- consultazione -------------------------------------------------------

    public List<Object> modelli(String tipo) {
        String chiave = "testo".equals(tipo) ? "modelli_testo" : tipo;
        return MiniJson.lista(dati, chiave);
    }

    public Map<String, Object> modello(String codice) {
        for (String tipo : new String[]{"video", "immagine", "audio", "modelli_testo"}) {
            for (Object o : modelli(tipo)) {
                if (codice.equals(MiniJson.testo(o, "codice", ""))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) o;
                    return m;
                }
            }
        }
        return null;
    }

    public List<Object> stili() {
        return MiniJson.lista(dati, "stili");
    }

    public Map<String, Object> guide() {
        return MiniJson.mappa(dati, "guide");
    }

    /** I codici dei modelli che supportano un certo modo di generazione. */
    public List<String> compatibili(String tipo, String modo) {
        List<String> out = new ArrayList<>();
        for (Object o : modelli(tipo)) {
            List<Object> modi = MiniJson.lista(o, "modi");
            for (Object m : modi) {
                if (modo.equals(String.valueOf(m))) {
                    out.add(MiniJson.testo(o, "codice", ""));
                    break;
                }
            }
        }
        return out;
    }

    // --- validazione ---------------------------------------------------------

    /** I modi di un modello, per il messaggio d'errore. */
    private static String elenca(List<Object> valori) {
        StringBuilder b = new StringBuilder();
        for (Object v : valori) {
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(String.valueOf(v));
        }
        return b.toString();
    }

    private static boolean contiene(List<Object> valori, Object cercato) {
        for (Object v : valori) {
            if (v instanceof Number && cercato instanceof Number) {
                if (((Number) v).doubleValue() == ((Number) cercato).doubleValue()) {
                    return true;
                }
            } else if (String.valueOf(v).equals(String.valueOf(cercato))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Controlla i parametri contro i vincoli. Lista vuota = tutto a posto.
     *
     * @param durata      secondi, o null
     * @param rapporto    tipo "9:16", o null
     * @param risoluzione "hd", "fhd"..., o null
     * @param modo        modo di generazione, o null
     * @param immagini    quante immagini di riferimento, o null
     * @param caratteri   lunghezza del testo, o null
     */
    public List<String> valida(String codice, Integer durata, String rapporto,
                               String risoluzione, String modo, Integer immagini,
                               Integer caratteri) {
        List<String> errori = new ArrayList<>();
        Map<String, Object> m = modello(codice);
        if (m == null) {
            errori.add("modello '" + codice + "' inesistente");
            return errori;
        }
        if (Boolean.FALSE.equals(m.get("online"))) {
            errori.add("'" + codice + "' non e' disponibile in questo momento");
        }

        if (durata != null) {
            List<Object> durate = MiniJson.lista(m, "durate");
            if (!durate.isEmpty() && !contiene(durate, durata)) {
                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                for (Object d : durate) {
                    double v = ((Number) d).doubleValue();
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
                errori.add("durata " + durata + "s non ammessa (scegli tra "
                        + (long) min + "-" + (long) max + "s)");
            }
        }
        if (rapporto != null) {
            List<Object> ammessi = MiniJson.lista(m, "rapporti");
            if (!ammessi.isEmpty() && !contiene(ammessi, rapporto)) {
                errori.add("rapporto '" + rapporto + "' non ammesso (scegli tra "
                        + elenca(ammessi) + ")");
            }
        }
        if (risoluzione != null) {
            List<Object> ammesse = MiniJson.lista(m, "risoluzioni");
            if (!ammesse.isEmpty() && !contiene(ammesse, risoluzione)) {
                errori.add("risoluzione '" + risoluzione + "' non ammessa (scegli tra "
                        + elenca(ammesse) + ")");
            }
        }
        if (modo != null) {
            List<Object> modi = MiniJson.lista(m, "modi");
            if (!modi.isEmpty() && !contiene(modi, modo)) {
                errori.add("modo '" + modo + "' non ammesso (scegli tra "
                        + elenca(modi) + ")");
            }
        }
        if (immagini != null) {
            Object max = MiniJson.cerca(m, "max_immagini");
            if (max instanceof Number && immagini > ((Number) max).doubleValue()) {
                errori.add(immagini + " immagini, ma il massimo e' " + (long) ((Number) max).doubleValue());
            }
        }
        if (caratteri != null) {
            Object max = MiniJson.cerca(m, "max_caratteri");
            if (max instanceof Number && caratteri > ((Number) max).doubleValue()) {
                errori.add(caratteri + " caratteri, ma il massimo e' " + (long) ((Number) max).doubleValue());
            }
        }
        return errori;
    }
}
