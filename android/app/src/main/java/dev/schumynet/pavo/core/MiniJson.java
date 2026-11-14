package dev.schumynet.pavo.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON minimo: legge e scrive quello che serve a Pavo.
 *
 * Scritto a mano di proposito: l'APK non ha dipendenze, e su Android una
 * libreria JSON in piu' e' peso e superficie d'attacco in piu'.
 *
 * I valori sono resi con i tipi Java naturali: {@code Map<String,Object>} per
 * gli oggetti, {@code List<Object>} per gli array, String, Double, Boolean, null.
 */
public final class MiniJson {

    private final String testo;
    private int pos;

    private MiniJson(String testo) {
        this.testo = testo;
    }

    // --- lettura -------------------------------------------------------------

    public static Object leggi(String testo) {
        if (testo == null) {
            throw new IllegalArgumentException("JSON nullo");
        }
        MiniJson p = new MiniJson(testo);
        p.saltaSpazi();
        Object valore = p.valore();
        p.saltaSpazi();
        if (p.pos < p.testo.length()) {
            throw new IllegalArgumentException(
                    "caratteri di troppo dopo il JSON, alla posizione " + p.pos);
        }
        return valore;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> oggetto(String testo) {
        Object v = leggi(testo);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("atteso un oggetto JSON");
        }
        return (Map<String, Object>) v;
    }

    private void saltaSpazi() {
        while (pos < testo.length()) {
            char c = testo.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private Object valore() {
        if (pos >= testo.length()) {
            throw new IllegalArgumentException("JSON troncato");
        }
        char c = testo.charAt(pos);
        switch (c) {
            case '{':
                return oggettoInterno();
            case '[':
                return array();
            case '"':
                return stringa();
            case 't':
                return parola("true", Boolean.TRUE);
            case 'f':
                return parola("false", Boolean.FALSE);
            case 'n':
                return parola("null", null);
            default:
                return numero();
        }
    }

    private Object parola(String atteso, Object valore) {
        if (!testo.startsWith(atteso, pos)) {
            throw new IllegalArgumentException(
                    "atteso '" + atteso + "' alla posizione " + pos);
        }
        pos += atteso.length();
        return valore;
    }

    private Map<String, Object> oggettoInterno() {
        Map<String, Object> mappa = new LinkedHashMap<>();
        pos++;                                   // {
        saltaSpazi();
        if (pos < testo.length() && testo.charAt(pos) == '}') {
            pos++;
            return mappa;
        }
        while (true) {
            saltaSpazi();
            if (pos >= testo.length() || testo.charAt(pos) != '"') {
                throw new IllegalArgumentException(
                        "attesa una chiave alla posizione " + pos);
            }
            String chiave = stringa();
            saltaSpazi();
            if (pos >= testo.length() || testo.charAt(pos) != ':') {
                throw new IllegalArgumentException(
                        "attesi ':' alla posizione " + pos);
            }
            pos++;
            saltaSpazi();
            mappa.put(chiave, valore());
            saltaSpazi();
            if (pos >= testo.length()) {
                throw new IllegalArgumentException("oggetto non chiuso");
            }
            char c = testo.charAt(pos);
            if (c == ',') {
                pos++;
            } else if (c == '}') {
                pos++;
                return mappa;
            } else {
                throw new IllegalArgumentException(
                        "attesi ',' o '}' alla posizione " + pos);
            }
        }
    }

    private List<Object> array() {
        List<Object> lista = new ArrayList<>();
        pos++;                                   // [
        saltaSpazi();
        if (pos < testo.length() && testo.charAt(pos) == ']') {
            pos++;
            return lista;
        }
        while (true) {
            saltaSpazi();
            lista.add(valore());
            saltaSpazi();
            if (pos >= testo.length()) {
                throw new IllegalArgumentException("array non chiuso");
            }
            char c = testo.charAt(pos);
            if (c == ',') {
                pos++;
            } else if (c == ']') {
                pos++;
                return lista;
            } else {
                throw new IllegalArgumentException(
                        "attesi ',' o ']' alla posizione " + pos);
            }
        }
    }

    private String stringa() {
        pos++;                                   // "
        StringBuilder b = new StringBuilder();
        while (true) {
            if (pos >= testo.length()) {
                throw new IllegalArgumentException("stringa non chiusa");
            }
            char c = testo.charAt(pos++);
            if (c == '"') {
                return b.toString();
            }
            if (c != '\\') {
                b.append(c);
                continue;
            }
            if (pos >= testo.length()) {
                throw new IllegalArgumentException("sequenza di escape troncata");
            }
            char e = testo.charAt(pos++);
            switch (e) {
                case '"':  b.append('"');  break;
                case '\\': b.append('\\'); break;
                case '/':  b.append('/');  break;
                case 'b':  b.append('\b'); break;
                case 'f':  b.append('\f'); break;
                case 'n':  b.append('\n'); break;
                case 'r':  b.append('\r'); break;
                case 't':  b.append('\t'); break;
                case 'u':
                    if (pos + 4 > testo.length()) {
                        throw new IllegalArgumentException("\\u troncato");
                    }
                    b.append((char) Integer.parseInt(testo.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default:
                    throw new IllegalArgumentException("escape sconosciuto: \\" + e);
            }
        }
    }

    private Object numero() {
        int inizio = pos;
        if (pos < testo.length() && (testo.charAt(pos) == '-' || testo.charAt(pos) == '+')) {
            pos++;
        }
        while (pos < testo.length()) {
            char c = testo.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E'
                    || c == '-' || c == '+') {
                pos++;
            } else {
                break;
            }
        }
        String grezzo = testo.substring(inizio, pos);
        if (grezzo.isEmpty()) {
            throw new IllegalArgumentException(
                    "atteso un valore alla posizione " + inizio);
        }
        try {
            return Double.valueOf(grezzo);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("numero non valido: " + grezzo);
        }
    }

    // --- scrittura -----------------------------------------------------------

    public static String scrivi(Object valore) {
        StringBuilder b = new StringBuilder();
        scriviIn(b, valore);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void scriviIn(StringBuilder b, Object v) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String) {
            scriviStringa(b, (String) v);
        } else if (v instanceof Boolean) {
            b.append(v);
        } else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            // Gli interi si scrivono senza ".0": piu' leggibile, e Pavo
            // distingue i due casi in alcuni campi.
            if (d == Math.floor(d) && !Double.isInfinite(d)
                    && Math.abs(d) < 1e15) {
                b.append((long) d);
            } else {
                b.append(d);
            }
        } else if (v instanceof Map) {
            b.append('{');
            boolean primo = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                if (!primo) {
                    b.append(',');
                }
                primo = false;
                scriviStringa(b, e.getKey());
                b.append(':');
                scriviIn(b, e.getValue());
            }
            b.append('}');
        } else if (v instanceof Iterable) {
            b.append('[');
            boolean primo = true;
            for (Object o : (Iterable<Object>) v) {
                if (!primo) {
                    b.append(',');
                }
                primo = false;
                scriviIn(b, o);
            }
            b.append(']');
        } else {
            scriviStringa(b, String.valueOf(v));
        }
    }

    private static void scriviStringa(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n");  break;
                case '\r': b.append("\\r");  break;
                case '\t': b.append("\\t");  break;
                case '\b': b.append("\\b");  break;
                case '\f': b.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        b.append('"');
    }

    // --- comodita' per leggere valori annidati -------------------------------

    /** Il valore a un percorso tipo {@code items/0/content}, o null.
     *  Un percorso vuoto o nullo significa la radice stessa. */
    @SuppressWarnings("unchecked")
    public static Object cerca(Object radice, String percorso) {
        if (percorso == null || percorso.isEmpty()) {
            return radice;
        }
        Object corrente = radice;
        for (String pezzo : percorso.split("/")) {
            if (corrente == null) {
                return null;
            }
            if (corrente instanceof Map) {
                corrente = ((Map<String, Object>) corrente).get(pezzo);
            } else if (corrente instanceof List) {
                try {
                    corrente = ((List<Object>) corrente).get(Integer.parseInt(pezzo));
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return corrente;
    }

    public static String testo(Object radice, String percorso, String seManca) {
        Object v = cerca(radice, percorso);
        return v == null ? seManca : String.valueOf(v);
    }

    public static double numero(Object radice, String percorso, double seManca) {
        Object v = cerca(radice, percorso);
        return v instanceof Number ? ((Number) v).doubleValue() : seManca;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> lista(Object radice, String percorso) {
        Object v = cerca(radice, percorso);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> mappa(Object radice, String percorso) {
        Object v = cerca(radice, percorso);
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }
}
