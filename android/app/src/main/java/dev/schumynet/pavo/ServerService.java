package dev.schumynet.pavo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.schumynet.pavo.core.CanvasSpecs;
import dev.schumynet.pavo.core.McpServer;
import dev.schumynet.pavo.core.PavoClient;
import dev.schumynet.pavo.core.ToolRegistry;

/**
 * Tiene il server MCP acceso mentre l'app e' in background.
 *
 * Un server dentro un'app muore quando Android decide di risparmiare memoria.
 * Come servizio in primo piano, con una notifica sempre visibile, resta vivo —
 * e la notifica e' anche giusta: dice all'utente che c'e' qualcosa in ascolto.
 */
public class ServerService extends Service {

    public static final String AZIONE_AVVIA = "dev.schumynet.pavo.AVVIA";
    public static final String AZIONE_FERMA = "dev.schumynet.pavo.FERMA";

    private static final String CANALE = "pavo_mcp";
    private static final int NOTIFICA = 1;

    private static McpServer serverCondiviso;

    private McpServer server;
    private Sessione sessione;

    /** Il server attivo, o null. Serve all'interfaccia per mostrare lo stato. */
    public static McpServer serverAttivo() {
        return serverCondiviso;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sessione = new Sessione(this);
        creaCanale();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int avvioId) {
        String azione = intent == null ? AZIONE_AVVIA : intent.getAction();
        if (AZIONE_FERMA.equals(azione)) {
            ferma();
            stopSelf();
            return START_NOT_STICKY;
        }
        avvia();
        return START_STICKY;
    }

    private void avvia() {
        if (server != null && server.attivo()) {
            return;
        }
        try {
            CanvasSpecs specifiche = leggiSpecifiche();
            PavoClient client = new PavoClient(sessione);
            ToolRegistry registro = new ToolRegistry(client, specifiche);

            ToolRegistry.Sessione.configura("preferenze dell'app", sessione.haToken());

            server = new McpServer(registro);
            String token = sessione.tokenAccoppiamento();
            if (token == null || token.isEmpty()) {
                token = server.generaToken();
                sessione.salvaTokenAccoppiamento(token);
            } else {
                server.impostaToken(token);
            }
            server.avvia(sessione.porta(), !sessione.reteLocale());
            serverCondiviso = server;
            notifica(getString(R.string.server_attivo, sessione.porta()));
        } catch (IOException e) {
            notifica(getString(R.string.server_errore, String.valueOf(e.getMessage())));
        }
    }

    private CanvasSpecs leggiSpecifiche() throws IOException {
        try (java.io.InputStream in = getAssets().open("canvas_specs.json")) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
            }
            return CanvasSpecs.daJson(new String(b.toByteArray(), "UTF-8"));
        }
    }

    private void ferma() {
        if (server != null) {
            server.ferma();
            server = null;
        }
        serverCondiviso = null;
    }

    @Override
    public void onDestroy() {
        ferma();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // --- notifica ------------------------------------------------------------

    private void creaCanale() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canale = new NotificationChannel(CANALE,
                    getString(R.string.canale_nome), NotificationManager.IMPORTANCE_LOW);
            canale.setDescription(getString(R.string.canale_descrizione));
            NotificationManager m = getSystemService(NotificationManager.class);
            if (m != null) {
                m.createNotificationChannel(canale);
            }
        }
    }

    private void notifica(String testo) {
        Intent apri = new Intent(this, MainActivity.class);
        int bandiere = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bandiere |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent p = PendingIntent.getActivity(this, 0, apri, bandiere);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CANALE)
                : new Notification.Builder(this);
        b.setContentTitle(getString(R.string.app_name))
                .setContentText(testo)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(p)
                .setOngoing(true);
        startForeground(NOTIFICA, b.build());
    }
}
