package dev.schumynet.pavo.core;

/**
 * Come presentarsi a un sito perche' ci mostri la versione da computer.
 *
 * Sta qui, e non dentro la schermata di login, perche' e' la parte che si puo'
 * sbagliare in silenzio: se lo user-agent contiene la parola "Android", il sito
 * decide che siamo su un telefono e ci manda alla pagina di scaricamento della
 * sua app, invece di farci entrare. Qui si puo' collaudare senza Android.
 */
public final class VistaBrowser {

    private VistaBrowser() {
    }

    /**
     * Chrome da PC.
     *
     * Non deve contenere "Android", "iPhone", "iPad" ne' "iPod": sono le parole
     * con cui i siti riconoscono un telefono.
     */
    public static final String UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    /** Larghezza a cui si finge lo schermo. 1280 e' una misura da portatile. */
    public static final int LARGHEZZA_PC = 1280;

    /**
     * Ripulisce lo user-agent del WebView.
     *
     * Toglie "wv", che segnala una WebView e fa rifiutare il login a Google, e
     * la parte "Version/x.y" che hanno solo i browser del telefono.
     */
    public static String pulisci(String ua) {
        if (ua == null) {
            return "";
        }
        return ua.replace("; wv", "").replaceAll("Version/\\d+\\.\\d+ ", "");
    }

    /**
     * Se un sito considererebbe questo user-agent un telefono.
     *
     * Rifa' la stessa prova che fa Pavo: cerca le parole dei telefoni. Serve
     * solo alle prove, per dimostrare che lo user-agent da PC non le contiene.
     */
    public static boolean sembraTelefono(String ua) {
        if (ua == null) {
            return false;
        }
        return ua.matches("(?s).*(iPhone|iPad|iPod|Android).*");
    }

    /**
     * Lo script che impone la larghezza da PC alla pagina.
     *
     * Lo user-agent da solo non basta: la pagina decide il layout dalla
     * larghezza del viewport, e su un telefono e' stretta. Si riscrive il
     * viewport, creandolo se manca. Il fattore di ingrandimento lo calcola il
     * WebView, cosi' i 1280 punti entrano nello schermo.
     */
    public static String scriptViewport() {
        return "(function(){"
                + "var m=document.querySelector('meta[name=viewport]');"
                + "if(!m){m=document.createElement('meta');"
                + "m.setAttribute('name','viewport');"
                + "(document.head||document.documentElement).appendChild(m);}"
                + "m.setAttribute('content',"
                + "'width=" + LARGHEZZA_PC
                + ", maximum-scale=5.0, user-scalable=yes');"
                + "})()";
    }

    /** Lo script che riporta il viewport a misura di telefono. */
    public static String scriptViewportMobile() {
        return "(function(){var m=document.querySelector('meta[name=viewport]');"
                + "if(m){m.setAttribute('content',"
                + "'width=device-width, initial-scale=1');}})()";
    }
}
