import dev.schumynet.pavo.core.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Accende il server dell'app su una porta, per il collaudo col client MCP ufficiale. */
public class AvviaProva {
    public static void main(String[] a) throws Exception {
        PavoClient finto = new PavoClient("token-finto") {
            @Override
            public Map<String, Object> saldo() {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("total_balance", 869);
                return r;
            }
        };
        CanvasSpecs s = CanvasSpecs.daJson(new String(Files.readAllBytes(
                Paths.get("app/src/main/assets/canvas_specs.json")), StandardCharsets.UTF_8));
        McpServer srv = new McpServer(new ToolRegistry(finto, s));
        srv.impostaToken("token-di-accoppiamento-per-il-collaudo");
        srv.avvia(18999, true);
        System.out.println("  server acceso sulla porta 18999, in attesa del client ufficiale");
        Thread.sleep(600000);
    }
}
