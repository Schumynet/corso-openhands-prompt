"""Collaudo del server MCP dell'app con il client ufficiale dell'SDK.

Il server dell'app e' scritto a mano: se il client ufficiale non lo accetta,
allora ho inventato un protocollo mio, e l'APK non serve a niente. Questo
script e' il giudice.

Uso un finto PavoClient, cosi' il collaudo non tocca l'API vera.
"""

import asyncio
import json
import sys

from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client

PORTA = 18999
TOKEN = "token-di-accoppiamento-per-il-collaudo"


async def main():
    import httpx

    indirizzo = f"http://127.0.0.1:{PORTA}/mcp"
    intestazioni = {"Authorization": f"Bearer {TOKEN}"}

    print("=== il client ufficiale MCP parla col mio server? ===")

    # Il trasporto vuole un client HTTP suo: gli passo quello con l'header.
    http = httpx.AsyncClient(headers=intestazioni, timeout=30.0)
    try:
        async with streamable_http_client(indirizzo, http_client=http) as (leggi, scrivi):
            async with ClientSession(leggi, scrivi) as sessione:
                risultato = await sessione.initialize()
                info = risultato.server_info
                print(f"  initialize -> {info.name} {info.version}")
                print(f"  protocollo -> {risultato.protocol_version}")
                assert info.name == "pavo-mobile", info.name

                elenco = await sessione.list_tools()
                print(f"  strumenti  -> {len(elenco.tools)}")
                assert len(elenco.tools) >= 15

                nomi = {t.name for t in elenco.tools}
                for atteso in ("saldo_crediti", "canvas_valida", "canvas_modelli",
                               "canvas_guide", "chiamata_grezza"):
                    assert atteso in nomi, f"manca {atteso}"
                print(f"  strumenti attesi: tutti presenti")

                # Uno strumento che legge.
                esito = await sessione.call_tool("saldo_crediti", {})
                testo = esito.content[0].text
                print(f"  saldo_crediti -> {testo}")
                assert "869" in testo

                # Uno strumento che valida: il caso che fa buttare crediti.
                esito = await sessione.call_tool("canvas_valida", {
                    "model_code": "seedance-2-0", "durata": 20})
                testo = esito.content[0].text
                print(f"  canvas_valida(20s) -> {testo[:110]}")
                assert "4-15s" in testo

                # E il caso buono.
                esito = await sessione.call_tool("canvas_valida", {
                    "model_code": "seedance-2-0", "durata": 10})
                print(f"  canvas_valida(10s) -> {esito.content[0].text[:80]}")

                # Uno strumento che restituisce dati veri dal file delle specifiche.
                esito = await sessione.call_tool("canvas_modelli", {"tipo": "video"})
                testo = esito.content[0].text
                quanti = testo.count('"codice"')
                print(f"  canvas_modelli(video) -> {quanti} modelli")
                assert quanti == 8, quanti

                # Uno strumento inesistente deve dare errore, non cadere.
                esito = await sessione.call_tool("non_esiste", {})
                print(f"  strumento inesistente -> isError={esito.is_error}")
                assert esito.is_error

        print()
        print("  IL CLIENT UFFICIALE MCP ACCETTA IL SERVER DELL'APP")
        return 0

    except AssertionError as e:
        print(f"  FALLITO: {e}")
        return 1
    except Exception as e:
        print(f"  ERRORE: {type(e).__name__}: {e}")
        # I TaskGroup incapsulano l'errore vero: lo srotolo, altrimenti non si
        # capisce niente.
        def srotola(x, liv=1):
            for s2 in getattr(x, "exceptions", None) or []:
                print("  " * liv + f"-> {type(s2).__name__}: {s2}")
                srotola(s2, liv + 1)
        srotola(e)
        return 2
    finally:
        await http.aclose()


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
