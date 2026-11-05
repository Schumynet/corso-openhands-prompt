#!/usr/bin/env python3
"""pavo-mcp — server MCP per usare Pavo AI come una persona, da Claude.

Cosa fa
-------
Espone come strumenti MCP gli endpoint di `app.pavo-ai.work`, gli stessi che
l'app usa dal browser. Claude puo' cosi' guardare i crediti, scegliere un
modello, creare un progetto, generare immagini e video, montare l'export —
senza aprire il browser.

Il token non passa mai da Claude: lo legge il server dal file di sessione.
Pavo non ha API key: usa il token di sessione del browser, che scade.

Avvio
-----
    python3 pavo_mcp.py                      # trasporto stdio

Configurazione per Claude Desktop (`claude_desktop_config.json`):

    {
      "mcpServers": {
        "pavo": {
          "command": "python3",
          "args": ["/percorso/senna_series/pavo_mcp.py"]
        }
      }
    }

Per Claude Code basta aprire la cartella: vede i moduli e li usa.

Le tre famiglie di strumenti
----------------------------
* **Lettura** — crediti, modelli, stili, voci, progetti, stato. Non costano
  nulla e non cambiano niente. Si possono chiamare liberamente.
* **Scrittura** — creare progetti, confermare fasi, modificare inquadrature.
  Cambiano lo stato su Pavo ma non consumano crediti.
* **Generazione** — fotogrammi, video, voci. **Consumano crediti.** Prima di
  chiamarle, guarda il saldo e il costo.

Nota
----
Questi sono endpoint interni, non una API pubblica documentata. Funzionano, ma
se Pavo cambia qualcosa si rompono senza preavviso, e non c'e' supporto.
"""

from __future__ import annotations

import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))

from mcp.server.mcpserver import MCPServer

import pavo_api as A

server = MCPServer(
    name="pavo",
    title="Pavo AI",
    instructions=(
        "Usa Pavo AI come farebbe una persona dal browser: crediti, modelli, "
        "progetti, generazione di immagini e video, montaggio.\n\n"
        "Prima di ogni generazione guarda `saldo_crediti` e `costo_modello`: "
        "le generazioni consumano crediti e non si annullano.\n\n"
        "Per un progetto nuovo, preferisci `crea_da_copione`: il testo entra "
        "come lo scriviamo noi e l'outline esce in italiano. `crea_automatico` "
        "lascia che l'AI inventi trama e scene, e puo' produrre testo in "
        "inglese.\n\n"
        "Il campo `riferimento` (immagine) e' l'unico che fa rispettare "
        "davvero l'aspetto di un luogo o di un personaggio: senza, la stessa "
        "scena cambia a ogni generazione.\n\n"
        "Nessuno strumento pubblica su YouTube: la pubblicazione su YouTube e' "
        "manuale per scelta."
    ),
)


def _client() -> A.Pavo:
    """Un client per chiamata, cosi' il token viene riletto ogni volta."""
    return A.Pavo()


# =============================================================================
# LETTURA — non costano nulla, non cambiano niente
# =============================================================================

@server.tool(description="Stato del token di sessione: valido o scaduto, e "
                         "quanti giorni mancano. Chiamalo per primo: senza "
                         "token valido nessuno strumento funziona.")
def token_stato() -> dict:
    return A.scadenza_token(A.leggi_token())


@server.tool(description="Crediti disponibili. Guardalo prima di ogni "
                         "generazione: le generazioni non si annullano.")
def saldo_crediti() -> dict:
    s = A.saldo(_client())
    return {"crediti": s.get("total_balance"),
            "permanenti": s.get("permanent_balance"),
            "a_scadenza": s.get("time_sensitive_balance"),
            "livello": s.get("level_name")}


@server.tool(description="Costo in crediti di una generazione con un dato "
                         "modello. Usa questo, non credits/calculate: "
                         "calculate risponde sempre 0.")
def costo_modello(model_code: str, model_type: str) -> dict:
    c = A.costo_stimato(_client(), model_code, model_type)
    return {"modello": model_code, "tipo": model_type,
            "crediti": c.get("required_credits"),
            "prezzo_unitario": c.get("unit_price"),
            "trovato": c.get("is_found")}


@server.tool(description="Elenca i modelli disponibili, divisi per immagine e "
                         "video. Sono 25: 15 per le immagini, 10 per i video.")
def elenca_modelli() -> dict:
    m = A.modelli(_client())
    return {"immagine": [x["model_code"] for x in m if x.get("model_type") == "image"],
            "video": [x["model_code"] for x in m if x.get("model_type") == "video"],
            "totale": len(m)}


@server.tool(description="Limiti e opzioni di un modello: risoluzioni, durate, "
                         "modalita' supportate.")
def capacita_modello(model_code: str, model_type: str) -> dict:
    return A.capacita_modello(_client(), model_code, model_type)


@server.tool(description="Gli stili visivi disponibili, divisi per categoria "
                         "(2D, 3D, Real Person). Restituisce gli id da passare "
                         "come style_id.")
def elenca_stili(categoria: str = "") -> list[dict]:
    st = A.stili(_client())
    fuori = []
    for c in st:
        if categoria and c.get("category_name") != categoria:
            continue
        fuori.append({"categoria": c.get("category_name"),
                      "stili": [{"style_id": s.get("style_id"),
                                 "nome": s.get("name")}
                                for s in c.get("styles", [])]})
    return fuori


@server.tool(description="Le voci disponibili per la narrazione, con costo. "
                         "Attenzione: sono tutte in inglese. L'italiano non "
                         "c'e'.")
def elenca_voci() -> dict:
    v = A.voci(_client())
    return {"totale": len(v),
            "lingue": sorted({x.get("locale") for x in v}),
            "costo_per_voce": A.costo_voce(_client()).get("required_credits"),
            "voci": [{"id": x.get("engine_voice_id"), "nome": x.get("name"),
                      "genere": x.get("gender")} for x in v]}


@server.tool(description="I pacchetti di crediti acquistabili, con prezzo in "
                         "dollari.")
def elenca_pacchetti_crediti() -> list[dict]:
    return [{"crediti": p.get("quota_amount"),
             "prezzo_usd": (p.get("price") or 0) / 100,
             "product_id": p.get("product_id")}
            for p in A.pacchetti_crediti(_client())]


@server.tool(description="I progetti dell'utente, con stato e copertina.")
def elenca_progetti(pagina: int = 1, quanti: int = 20) -> list[dict]:
    return [{"project_id": A.id_progetto(p), "titolo": p.get("title"),
             "stato": p.get("status"), "episodi": p.get("episode_count"),
             "aggiornato": p.get("updated_at")}
            for p in A.elenca_progetti(_client(), pagina, quanti)]


@server.tool(description="Stato di avanzamento di un progetto. Le fasi che "
                         "finiscono in '_confirming' aspettano una conferma: "
                         "senza, il progetto resta fermo per sempre.")
def stato_progetto(project_id: str) -> dict:
    a = A.avanzamento(_client(), project_id)
    return {"stato": a.get("status"), "nome": a.get("projectName"),
            "tipo": a.get("project_type")}


@server.tool(description="Gli episodi di un progetto, con stato del copione e "
                         "numero di inquadrature.")
def elenca_episodi(project_id: str) -> list[dict]:
    return [{"episode_id": str(e.get("episode_id")),
             "numero": e.get("episode_no"), "titolo": e.get("title"),
             "copione": e.get("script_status"),
             "storyboard": e.get("storyboard_phase"),
             "inquadrature": e.get("shot_count"),
             "personaggi": e.get("character_count"),
             "scene": e.get("scene_count")}
            for e in A.episodi(_client(), project_id)]


@server.tool(description="Dettaglio di un episodio: stato, inquadrature, "
                         "crediti necessari per lo storyboard.")
def dettaglio_episodio(episode_id: str) -> dict:
    return A.dettaglio_episodio(_client(), episode_id)


@server.tool(description="Gli asset di un progetto: personaggi, oggetti o "
                         "luoghi. Il gruppo e' character, prop o scene.")
def elenca_asset(gruppo: str, project_id: str = "") -> list[dict]:
    return A.elenca_asset(_client(), gruppo, project_id=project_id or None)


@server.tool(description="Le immagini di riferimento allegate a un segmento.")
def riferimenti_segmento(segment_id: str) -> dict:
    return A.riferimenti_segmento(_client(), segment_id)


@server.tool(description="Specifiche degli strumenti canvas: audio, immagine, "
                         "video. Dice quali parametri accetta ognuno.")
def strumenti_canvas() -> dict:
    return A.strumenti_canvas(_client())


@server.tool(description="Il profilo dell'utente: nome, email, metodo di "
                         "accesso.")
def profilo() -> dict:
    u = (A.profilo(_client()).get("user_info") or {})
    return {"utente": u.get("username"), "email": u.get("email"),
            "accesso": u.get("auth_provider"),
            "creato": u.get("created_at")}


# =============================================================================
# SCRITTURA — cambiano lo stato, non consumano crediti
# =============================================================================

@server.tool(description="Crea un progetto dal NOSTRO copione. Il testo non "
                         "viene riscritto dall'AI e l'outline esce in "
                         "italiano. E' la via per il controllo totale su trama "
                         "e personaggi.")
def crea_da_copione(copione: str, titolo: str, ratio: str = "16:9") -> dict:
    d = A.crea_da_copione(_client(), copione, titolo, ratio)
    return {"project_id": A.id_progetto(d), "stato": d.get("status")}


@server.tool(description="Crea un progetto lasciando che l'AI scriva trama e "
                         "scene. Utile solo se non abbiamo un copione nostro: "
                         "il testo puo' uscire in inglese.")
def crea_automatico(requirements: str, titolo: str, sinossi: str,
                    durata_sec: int = 900, drama_type: str = "revenge",
                    voce: str = "female", note: str = "") -> dict:
    st = A.stili(_client())
    stile = {}
    for c in st:
        for s in c.get("styles", []):
            if "2D" in (c.get("category_name") or ""):
                stile = {"style_id": s.get("style_id"),
                         "style_name": s.get("name")}
                break
        if stile:
            break
    d = A.crea_automatico(_client(), requirements, titolo, sinossi, stile,
                          durata_sec, drama_type, voce, note)
    return {"project_id": A.id_progetto(d), "stato": d.get("status")}


@server.tool(description="Conferma una fase della pipeline. Fasi: outline, "
                         "script, assets, storyboard. Senza conferma il "
                         "progetto resta fermo per sempre.")
def conferma_fase(project_id: str, fase: str) -> dict:
    A.conferma(_client(), project_id, fase)
    return {"ok": True, "project_id": project_id, "fase": fase}


@server.tool(description="Riprova una fase fallita: outline o episode.")
def riprova_fase(project_id: str, fase: str) -> dict:
    A.riprova_fase(_client(), project_id, fase)
    return {"ok": True, "fase": fase}


@server.tool(description="Modifica un'inquadratura: prompt, dialogo, durata, "
                         "ordine. Non consuma crediti.")
def modifica_inquadratura(shot_id: str, prompt: str = "",
                          dialogo: str = "", durata_s: int = 0) -> dict:
    campi = {}
    if prompt:
        campi["prompt"] = prompt
    if dialogo:
        campi["dialogue"] = dialogo
    if durata_s:
        campi["duration"] = durata_s
    if not campi:
        return {"ok": False, "errore": "nessun campo da modificare"}
    A.modifica_shot(_client(), shot_id, **campi)
    return {"ok": True, "modificati": list(campi)}


@server.tool(description="Riordina le inquadrature di un episodio. Passa gli "
                         "id nell'ordine voluto.")
def riordina_inquadrature(segment_ids: list[str]) -> dict:
    A.riordina_segmenti(_client(), segment_ids)
    return {"ok": True, "quanti": len(segment_ids)}


@server.tool(description="Crea un asset (personaggio, oggetto o luogo) e ne "
                         "genera l'immagine. Consuma crediti per l'immagine.")
def crea_asset(gruppo: str, nome: str, descrizione: str,
               project_id: str = "", modello: str = "seedream4-5",
               ratio: str = "16:9", riferimento: str = "") -> dict:
    return A.crea_asset(_client(), gruppo, nome, descrizione,
                        project_id=project_id, modello=modello,
                        ratio=ratio, riferimento=riferimento or None)


@server.tool(description="Allunga una descrizione con l'AI. Puo' uscire in "
                         "inglese: controlla il risultato.")
def espandi_descrizione(gruppo: str, descrizione: str) -> dict:
    return {"descrizione": A.espandi_descrizione(_client(), gruppo, descrizione)}


@server.tool(description="Prepara l'export del video finale di un progetto.")
def esporta_progetto(project_id: str, episode_id: str = "") -> dict:
    return A.esporta(_client(), project_id, episode_id or None)


# =============================================================================
# GENERAZIONE — consumano crediti
# =============================================================================

@server.tool(description="Genera il fotogramma di un segmento. CONSUMA "
                         "CREDITI. Passa `riferimento` con l'URL di "
                         "un'immagine: e' l'unico modo per far rispettare "
                         "l'aspetto di un luogo o di un personaggio.")
def genera_fotogramma(segment_id: str, prompt: str,
                      riferimento: str = "",
                      model_code: str = "seedream4-5",
                      style_id: str = "334937235111833600",
                      risoluzione: str = "720P") -> dict:
    return A.fotogramma(_client(), segment_id, prompt,
                        model_code=model_code, style_id=style_id,
                        risoluzione=risoluzione,
                        riferimento=riferimento or None)


@server.tool(description="Genera il video di un segmento. CONSUMA CREDITI. "
                         "Stessi campi del fotogramma: usa `riferimento` per "
                         "la coerenza visiva.")
def genera_video(segment_id: str, prompt: str,
                 riferimento: str = "",
                 model_code: str = "seedance-1-5-pro",
                 style_id: str = "334937235111833600",
                 risoluzione: str = "720P") -> dict:
    return A.video(_client(), segment_id, prompt,
                   model_code=model_code, style_id=style_id,
                   risoluzione=risoluzione, riferimento=riferimento or None)


@server.tool(description="Genera i fotogrammi di piu' segmenti in una volta. "
                         "CONSUMA CREDITI. Piu' economico delle singole "
                         "chiamate.")
def fotogrammi_a_blocchi(segment_ids: list[str], prompt: str = "",
                         model_code: str = "seedream4-5",
                         riferimento: str = "") -> dict:
    return A.fotogrammi_a_blocchi(_client(), segment_ids,
                                  custom_style_prompt=prompt,
                                  model_code=model_code,
                                  upload_reference_image_url=riferimento or None)


@server.tool(description="Genera i video di piu' segmenti in una volta. "
                         "CONSUMA CREDITI.")
def video_a_blocchi(segment_ids: list[str], prompt: str = "",
                    model_code: str = "seedance-1-5-pro",
                    riferimento: str = "") -> dict:
    return A.video_a_blocchi(_client(), segment_ids,
                             custom_style_prompt=prompt,
                             model_code=model_code,
                             upload_reference_image_url=riferimento or None)


@server.tool(description="Genera lo storyboard di tutte le inquadrature di un "
                         "episodio. CONSUMA CREDITI: guarda prima il costo.")
def genera_storyboard(episode_id: str) -> dict:
    return A.genera_storyboard(_client(), episode_id)


@server.tool(description="Genera una voce per un segmento. CONSUMA CREDITI. "
                         "Le voci sono tutte in inglese: per l'italiano serve "
                         "un altro servizio.")
def genera_voce(segment_id: str, testo: str, voice_id: str) -> dict:
    return A.genera_voce(_client(), segment_id=segment_id, text=testo,
                         engine_voice_id=voice_id)


@server.tool(description="Migliora un prompt con l'AI. Non consuma crediti. "
                         "Utile per arricchire una descrizione prima di "
                         "generare.")
def migliora_prompt(prompt: str) -> dict:
    return A.migliora_prompt(_client(), prompt)


@server.tool(description="Traduce un prompt. Non consuma crediti.")
def traduci_prompt(prompt: str, lingua: str = "en") -> dict:
    return A.traduci_prompt(_client(), prompt, lingua)


@server.tool(description="Stato di un'attivita' lunga (task) avviata su Pavo.")
def stato_task(task_id: str) -> dict:
    return A.stato_task(_client(), task_id)


@server.tool(description="Ferma un'attivita' in corso.")
def ferma_task(task_id: str) -> dict:
    A.ferma_task(_client(), task_id)
    return {"ok": True, "task_id": task_id}


# =============================================================================
# SESSIONE
# =============================================================================

@server.tool(description="Rifà il login su Pavo dal browser e rigenera il "
                         "file di sessione. Serve quando il token e' scaduto. "
                         "Richiede un browser disponibile sulla macchina.")
def rinnova_token() -> dict:
    import subprocess

    script = BASE.parent / "pavo_login" / "save_session.py"
    if not script.is_file():
        return {"ok": False, "errore": f"script assente: {script}"}
    esito = subprocess.run([sys.executable, str(script)], cwd=script.parent,
                           capture_output=True, text=True, timeout=600)
    return {"ok": esito.returncode == 0,
            "uscita": (esito.stdout or "")[-600:],
            "errore": (esito.stderr or "")[-300:] if esito.returncode else ""}



# --- Canvas: generazione libera per nodi -------------------------------------
# Il Canvas non ha progetti a copione: si compone una catena di nodi (testo,
# immagine, video, audio) e ogni nodo vede l'uscita del precedente.

@server.tool(description="I modelli della modalita' Canvas, per tipo di nodo. "
                         "tipo: 'video', 'immagine', 'audio' o 'testo'. "
                         "Ogni voce porta i vincoli: durate, rapporti, "
                         "risoluzioni, modi ammessi.")
def canvas_modelli(tipo: str = "video") -> list[dict]:
    import canvas as C
    return C.modelli(tipo)


@server.tool(description="I modi di generazione ammessi da un modello Canvas "
                         "(text_to_video, reference_image, first_last_frame, "
                         "video_edit, video_extend...).")
def canvas_modi(model_code: str) -> dict:
    import canvas as C
    m = C.modello(model_code)
    if not m:
        return {"ok": False, "errore": f"modello '{model_code}' inesistente"}
    return {"ok": True, "codice": model_code, "modi": m.get("modi", []),
            "durate": m.get("durate", []), "rapporti": m.get("rapporti", []),
            "risoluzioni": m.get("risoluzioni", [])}


@server.tool(description="Controlla se dei parametri stanno nei vincoli di un "
                         "modello Canvas, PRIMA di generare. Restituisce la "
                         "lista degli errori: vuota vuol dire che va bene.")
def canvas_valida(model_code: str, durata: int = 0, rapporto: str = "",
                  risoluzione: str = "", modo: str = "", immagini: int = 0,
                  caratteri: int = 0) -> dict:
    import canvas as C
    errori = C.valida("video", model_code,
                      durata=durata or None, rapporto=rapporto or None,
                      risoluzione=risoluzione or None, modo=modo or None,
                      immagini=immagini or None, caratteri=caratteri or None)
    return {"ok": not errori, "errori": errori}


@server.tool(description="Quali modelli Canvas supportano un certo modo di "
                         "generazione. Esempio: chi sa allungare un video.")
def canvas_compatibili(tipo: str, modo: str) -> list[str]:
    import canvas as C
    return C.compatibili(tipo, modo)


@server.tool(description="Gli stili visivi del Canvas (60), con codice e nome.")
def canvas_stili() -> list[dict]:
    import canvas as C
    return C.stili()


@server.tool(description="Le guide ufficiali del Canvas, con il prompt che usa "
                         "Pavo stesso. Utili come modello di scrittura. "
                         "tipo: 'video', 'image', 'text' o 'audio'.")
def canvas_guide(tipo: str = "") -> dict:
    import canvas as C
    return C.guide(tipo or None)


@server.tool(description="Riscrive un prompt in inglese, piu' dettagliato. "
                         "Gratis: non consuma crediti.")
def canvas_migliora_prompt(testo: str) -> dict:
    import canvas as C
    return {"ok": True, "prompt": C.migliora(_client(), testo)}


@server.tool(description="Traduce un prompt verso il cinese. Nota: la direzione "
                         "e' sempre en2zh, il parametro di lingua non ha effetto.")
def canvas_traduciprompt(testo: str) -> dict:
    import canvas as C
    return {"ok": True, "testo": C.traduci(_client(), testo)}


@server.tool(description="Da un'immagine ricava la descrizione testuale. "
                         "Serve l'URL dell'immagine, non un testo.")
def canvas_espandi_descrizione(image_url: str, asset_type: str = "character") -> dict:
    import canvas as C
    return {"ok": True, "descrizione": C.espandi(_client(), image_url, asset_type)}


@server.tool(description="Annulla una generazione Canvas in corso.")
def canvas_annulla(corpo_json: str = "") -> dict:
    import json
    import canvas as C
    corpo = json.loads(corpo_json) if corpo_json.strip() else {}
    return C.annulla(_client(), **corpo)


@server.tool(description="Stato di una generazione Canvas. Il Canvas lavora in "
                         "asincrono: la generazione non e' finita quando la "
                         "chiamata risponde.")
def canvas_avanzamento(corpo_json: str = "") -> dict:
    import json
    import canvas as C
    corpo = json.loads(corpo_json) if corpo_json.strip() else {}
    return C.avanzamento(_client(), **corpo)


@server.tool(description="Crea un asset Canvas: personaggio, scena o oggetto. "
                         "Gli asset sono cio' che tiene coerenti i personaggi "
                         "tra una clip e l'altra; nel prompt si richiamano "
                         "con @. tipo: 'characters', 'scenes' o 'props'.")
def canvas_crea_asset(tipo: str, corpo_json: str) -> dict:
    import json
    import canvas as C
    return C.crea_asset(_client(), tipo, **json.loads(corpo_json))


@server.tool(description="Genera il foglio a quattro viste di un personaggio "
                         "(frontale, profilo, retro, tre quarti). E' il modo "
                         "per fissare l'aspetto di un personaggio. Consuma "
                         "crediti.")
def canvas_quattro_viste(corpo_json: str) -> dict:
    import json
    import canvas as C
    return C.quattro_viste(_client(), **json.loads(corpo_json))


@server.tool(description="Chiamata grezza a un endpoint Pavo. Usala solo se "
                         "uno strumento dedicato non esiste: molti endpoint "
                         "non sono documentati. Passa metodo, percorso e "
                         "corpo JSON.")
def chiamata_grezza(metodo: str, percorso: str, corpo_json: str = "") -> dict:
    import json

    corpo = None
    if corpo_json.strip():
        try:
            corpo = json.loads(corpo_json)
        except json.JSONDecodeError as e:
            return {"ok": False, "errore": f"JSON non valido: {e}"}
    p = _client()
    try:
        d = p.chiama(metodo.upper(), percorso, corpo)
        return {"ok": True, "http": p.ultimo_http, "dati": d}
    except A.PavoError as e:
        return {"ok": False, "http": e.http, "codice": e.codice,
                "errore": str(e)}



def main() -> int:
    server.run("stdio")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
