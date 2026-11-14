package dev.schumynet.pavo;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import dev.schumynet.pavo.core.McpServer;
import dev.schumynet.pavo.core.MiniJson;
import dev.schumynet.pavo.core.PavoClient;

/**
 * La schermata principale: dice se il login e' fatto, se il server e' acceso, e
 * su quale indirizzo risponde.
 *
 * L'indirizzo e il token sono le due cose che servono per collegare un client
 * MCP: stanno qui, in chiaro, perche' l'utente li deve poter leggere.
 */
public class MainActivity extends Activity {

    private Sessione sessione;
    private TextView statoLogin;
    private TextView statoServer;
    private TextView indirizzo;
    private TextView token;
    private Switch reteLocale;

    @Override
    protected void onCreate(Bundle statoPrecedente) {
        super.onCreate(statoPrecedente);
        setContentView(R.layout.activity_main);
        sessione = new Sessione(this);

        statoLogin = findViewById(R.id.stato_login);
        statoServer = findViewById(R.id.stato_server);
        indirizzo = findViewById(R.id.indirizzo);
        token = findViewById(R.id.token);
        reteLocale = findViewById(R.id.rete_locale);

        findViewById(R.id.entra).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));

        findViewById(R.id.esci).setOnClickListener(v -> {
            sessione.dimenticaToken();
            aggiorna();
        });

        findViewById(R.id.avvia_server).setOnClickListener(v -> avviaServer());

        findViewById(R.id.ferma_server).setOnClickListener(v -> {
            Intent i = new Intent(this, ServerService.class);
            i.setAction(ServerService.AZIONE_FERMA);
            startService(i);
            indirizzo.postDelayed(this::aggiorna, 400);
        });

        Button copia = findViewById(R.id.copia);
        copia.setOnClickListener(v -> {
            android.content.ClipboardManager cb =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(android.content.ClipData.newPlainText(
                    "configurazione Pavo", configurazione()));
            copia.setText(R.string.copiato);
            copia.postDelayed(() -> copia.setText(R.string.copia), 1500);
        });

        reteLocale.setChecked(sessione.reteLocale());
        reteLocale.setOnCheckedChangeListener((v, attivo) -> sessione.salvaReteLocale(attivo));
    }

    @Override
    protected void onResume() {
        super.onResume();
        aggiorna();
    }

    private void avviaServer() {
        Intent i = new Intent(this, ServerService.class);
        i.setAction(ServerService.AZIONE_AVVIA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        indirizzo.postDelayed(this::aggiorna, 600);
    }

    private void aggiorna() {
        // --- login ---
        if (sessione.haToken()) {
            String quando = "sconosciuta";
            boolean valido = true;
            try {
                java.util.Map<String, Object> stato =
                        PavoClient.scadenzaToken(sessione.token());
                quando = MiniJson.testo(stato, "scadenza", "sconosciuta");
                valido = Boolean.TRUE.equals(stato.get("valido"));
            } catch (java.io.IOException e) {
                // Preferenze illeggibili: si mostra comunque lo stato "assente"
                // invece di far cadere l'interfaccia.
                valido = false;
            }
            statoLogin.setText(valido
                    ? getString(R.string.login_valido, quando)
                    : getString(R.string.login_scaduto, quando));
        } else {
            statoLogin.setText(R.string.login_assente);
        }

        // --- server ---
        McpServer s = ServerService.serverAttivo();
        if (s != null && s.attivo()) {
            String ind = s.soloLocale() ? "solo questo telefono" : "rete locale";
            statoServer.setText(getString(R.string.server_acceso, sessione.porta(), ind));
            indirizzo.setText(indirizzi());
            token.setText(s.token());
        } else {
            statoServer.setText(R.string.server_spento);
            indirizzo.setText(R.string.trattino);
            token.setText(R.string.trattino);
        }
    }

    private String indirizzi() {
        StringBuilder b = new StringBuilder();
        try {
            List<NetworkInterface> reti = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface r : reti) {
                if (r.isLoopback() || !r.isUp()) {
                    continue;
                }
                for (InetAddress a : Collections.list(r.getInetAddresses())) {
                    if (a instanceof Inet4Address) {
                        if (b.length() > 0) {
                            b.append("\n");
                        }
                        b.append("http://").append(a.getHostAddress())
                         .append(":").append(sessione.porta()).append("/mcp");
                    }
                }
            }
        } catch (Exception e) {
            return getString(R.string.indirizzo_ignoto);
        }
        return b.length() == 0 ? getString(R.string.indirizzo_ignoto) : b.toString();
    }

    /** Il pezzo di configurazione da incollare nel client MCP. */
    private String configurazione() {
        String primo = indirizzi().split("\n")[0];
        return "{\n"
                + "  \"mcpServers\": {\n"
                + "    \"pavo\": {\n"
                + "      \"url\": \"" + primo + "\",\n"
                + "      \"headers\": {\n"
                + "        \"Authorization\": \"Bearer " + sessione.tokenAccoppiamento() + "\"\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }
}
