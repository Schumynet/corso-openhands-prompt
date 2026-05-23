# 🎓 CORSO VELOCIZZATO: PROMPT PER OPENHANDS
## Pipeline, Librerie, No-Code, YouTube AI Dream Team

---

## 1. PROMPT PER PIPELINE (n8n / Make.com)

### 🔧 Validazione Workflow
```
Verifica il workflow n8n in [URL/repo] e proponi ottimizzazioni.
Usa i validatori Department G: n8n Workflow Validator (Agent 32)
```

### ⚡ Build Pipeline Base
```
Crea una pipeline n8n che:
1. Riceve trigger webhook da YouTube
2. Invia dati a LLM per analisi (usa Agent 31: Master Orchestrator)
3. Salva risultati in GitHub (Agent 34: Memory Manager)
4. Notifica su Discord/Slack

Struttura: trigger → transform → AI → storage → notify
```

### 🚀 Pipeline Complessa Multi-Step
```
Costruisci pipeline Make.com con:
- Webhook in entrata (GitHub webhook / YouTube API)
- Router per smistamento canali multidimensionale
- 5 sub-workflow paralleli (Department E: Engagement, Distribution, etc.)
- GitHub memory persistence (Agent 34)
- Error handling con retry 3x
```

### 📦 Ricerca Librerie Pipeline
```
Trova librerie Python per pipeline dati in questo ordine:
1. Workflow orchestration: Prefect, Dagster, Metaflow
2. ETL: pandas, polars, dlt
3. API clients: httpx, aiohttp
4. Valida compatibilità con n8n/Make webhooks

Cerca in PyPI e GitHub, filtra per: stars > 100, recente (2023+), buona docs
```

---

## 2. PROMPT PER YOUTUBE AI DREAM TEAM

### 🎬 Script Video
```
Genera script per video YouTube usando:
- Agent 01: YouTube Channel Architect
- Agent 07: Hook & Intro Writer
- Agent 08: Core Script & Storyboard Writer

Input: [topic/titolo]
Output: hook (3sec) → intro (15sec) → corpo → CTA → outro
```

### 📊 Analisi Video Completa
```
Esegue analisi video completa con Department G:
- Agent 31: Master Video Analyzer & Orchestrator
- Agent 32: n8n Workflow Validator
- Agent 33: Make.com Workflow Validator

Analizza: [video URL o repo], estrai metadata, suggerisci workflow ottimali
```

### 🧠 Ricerca Contenuti Virali
```
Usa Agent 03: Viral Topic Ideation Engine
- Analizza nicchia: [nicchia]
- Trova 10 topic trending
- Genera titoli con Agent 05: Title Generator
- Output: markdown table con titoli, thumbnail concept, hook
```

### 💰 Monetizzazione
```
Pipeline lead generation con Department F:
- Agent 26: Lead Generation & Funnel Architect
- Agent 27: Sponsorship Negotiator
- Agent 29: A/B Testing Coordinator

Crea funnel: landing → lead magnet → email → conversion
```

---

## 3. PROMPT NO-CODE AUTOMATION

### 🌐 n8n Workflow Builder
```
Costruisci workflow n8n da zero:
- Trigger: HTTP Request / Webhook / Schedule
- Step 1: Parse JSON
- Step 2: AI Agent (Claude/GPT)
- Step 3: GitHub API per memory
- Step 4: Discord notification
- Output: n8n JSON workflow export
```

### 🔗 Make.com Scenario
```
Crea scenario Make.com:
1. GitHub trigger (new issue/pr)
2. Router →分流 a 3 path
   - Path A: AI analysis (Agent 31)
   - Path B: Data storage (Agent 34)
   - Path C: Notification
3. Aggregator
4. GitHub update
```

### 🔄 Automazione GitHub Actions
```
Pipeline CI/CD per YouTube automation:
- Trigger: push, schedule (cron 7:00 UTC daily)
- Jobs:
  1. Generate content (Gemini API)
  2. Create video (ffmpeg/images)
  3. Upload YouTube (YouTube Data API v3)
  4. Commit metadata to repo
```

---

## 4. TERMINOLOGIA CHIAVE (dai tuoi repo)

| Termine | Significato |
|---------|-------------|
| **Department A** | Strategia & Ideazione (01-06) |
| **Department B** | Pre-Produzione & Copione (07-11) |
| **Department C** | Produzione & Post-Produzione (12-16) |
| **Department D** | Distribuzione & Repurposing (17-21) |
| **Department E** | Engagement & Community (22-25) |
| **Department F** | Analytics & Monetizzazione (26-30) |
| **Department G** | Intelligence & Orchestrazione (31-35) |
| **Department H** | Next-Gen AI & Automation (36-39) |
| **SOP** | Standard Operating Procedure |
| **Memory GitHub** | Persistenza dati su repo (Agent 34) |
| **Smistamento multidimensionale** | Routing multi-criteria |
| **Ledger** | Registro transazioni/stato |

---

## 5. TEMPLATE PROMPT RAPIDI

### 💬 Domanda Singola
```
[Azione] con [Input] → [Output atteso]

Esempio:
"Analizza repo youtube-ai-dream-team-open, estrai Agent 31-35 (Department G), 
genera workflow n8n di validazione automatica"
```

### 🔄 Pipeline Completa
```
Prompt tipo: 
"
Ruolo: Sei Agent 31 (Master Orchestrator)
Contesto: Canale YouTube [nome], nicchia [nicchia]
Compito: Orchestrazione pipeline completa

Passaggi:
1. Analizza trend con Agent 03
2. Genera script con Agent 08
3. Valida workflow n8n con Agent 32
4. Salva in GitHub (Agent 34)
5. Notifica team

Output: Report markdown + n8n workflow JSON
"
```

### 🏃 Prompt Istantanei
```
"n8n pipeline: webhook → AI → GitHub → Discord"

"cerca librerie Python per ETL con supporto n8n webhooks"

"crea workflow Make.com per automatizzare upload YouTube"

"usa Agent 03 per trovare 10 topic virali su [tema]"

"valida questo workflow n8n e proponi ottimizzazioni Department G"

"build pipeline con memory persistente su GitHub (Agent 34)"

"genera script completo video YouTube con hook, corpo, CTA"
```

---

## 6. PATTERN AVANZATI

### 🎯 Chained Agents
```
Pipeline orchestrata:
Agent 01 (Architect) → Agent 03 (Ideation) → Agent 08 (Script) → 
Agent 32 (n8n Validate) → Agent 34 (GitHub Save)
```

### 🔀 Parallel Execution
```
Esegui in parallelo:
- Agent 02: Competitor Analysis
- Agent 04: Keyword Research  
- Agent 05: Title Generation

Poi aggrega con Agent 31 (Master Orchestrator)
```

### 📝 Memory Pattern
```
Ogni operazione:
1. Leggi contesto da GitHub (Agent 34)
2. Esegui task
3. Aggiorna GitHub con risultati
4. Notifica change via Agent 21 (Newsletter)
```

---

## 7. ESEMPI OPERATIVI

### ✅ Esempio 1: Nuovo Video
```
Prompt:
"
Ruolo: Agent 31 Master Orchestrator
Canale: Tech Reviews ITA
Azione: Pipeline video completo

1. Agent 02: Analizza competitor [lista canali]
2. Agent 03: 5 topic virali su Tech 2024
3. Agent 05: Genera 3 titoli con hook
4. Agent 08: Script completo (10 min)
5. Agent 12: Metadata SEO
6. Agent 32: Valida n8n workflow upload
7. Agent 34: Salva in /channels/tech-reviews/

Output: README + script.md + metadata.json + n8n_workflow.json
"
```

### ✅ Esempio 2: Librerie Pipeline
```
Prompt:
"
Trova e confronta librerie per pipeline dati Python:
- Task: ETL + AI + n8n integration
- Requisiti: async support, type hints, good docs
- Output: Tabella comparativa con pro/contro

Cerca in ordine:
1. Documentazione ufficiale
2. GitHub stars & activity
3. PyPI downloads settimanali
4. Esempi d'uso reali
"
```

### ✅ Esempio 3: Make.com Workflow
```
Prompt:
"
Crea scenario Make.com completo per YouTube automation:

Trigger: GitHub Actions completion
↓
Router: Tipo contenuto (short/long)
↓
Path A (Long): Agent 13 Thumbnail → upload
Path B (Short): Agent 18 Shorts Creator → upload
↓
Storage: GitHub (Agent 34)
↓
Notify: Discord webhook

Output: Immagine schema + Make.com JSON export
"
```

---

## 8. CHECKLIST RAPIDA

### 📋 Prima di ogni prompt, definisci:
- [ ] **WHO**: Quale Agent/Department (01-39)
- [ ] **WHAT**: Azione specifica
- [ ] **INPUT**: Dati/URL/repo di partenza
- [ ] **OUTPUT**: Formato atteso (md/json/immagini)
- [ ] **CONSTRAINTS**: Limitazioni o requisiti particolari

### 🎯 Template Base
```
[AGENT/DEPT] + [AZIONE] + [INPUT] + [OUTPUT]

Esempio:
"Agent 32 valida workflow n8n in ./workflows/publish.yml 
e genera report errori + fix suggeriti in formato markdown"
```

---

## 9. RISORSE E RIFERIMENTI

### Repository Chiave
- **YouTube AI Dream Team**: 39 agenti, workflow n8n/Make
- **Corso AI Completo**: Fondamenti, automazione, OpenHands
- **gemini-youtube-automation**: GitHub Actions + YouTube API

### Tool suggeriti
- n8n (workflow engine)
- Make.com (scenario automation)  
- GitHub Actions (CI/CD)
- Prefect/Dagster (data pipelines)

---

## 10. SVILUPPATORE NO-CODE AVANZATO

### 🎯 Cos'è lo Sviluppatore No-Code

Un **sviluppatore no-code** costruisce applicazioni, automazioni e pipeline COMPLEXE usando:
- **Piattaforme visuali**: n8n, Make.com, Zapier
- **Tool di orchestrazione**: GitHub Actions, GitHub Workflows
- **API integration**: Webhook, REST API, GraphQL
- **AI/LLM integration**: Claude, GPT, Gemini

**Non serve scrivere codice** - ma devi pensare come un developer.

---

### 📐 ARCHITETTURA NO-CODE

```
┌─────────────────────────────────────────────────────────────┐
│                    NO-CODE STACK                             │
├─────────────────────────────────────────────────────────────┤
│  FRONTEND (no-code)     │  BACKEND (no-code)    │  DATA      │
│  ─────────────────────  │  ──────────────────  │  ───────   │
│  Webflow / Carrd        │  n8n / Make.com      │  Airtable │
│  Softr / Glide          │  Zapier / Pipedream   │  Notion   │
│  Bubble (basic)         │  WordPress + plugins │  Google   │
│                         │                      │  Sheets   │
├─────────────────────────┼──────────────────────┼───────────┤
│  TRIGGER                │  PROCESS             │  OUTPUT   │
│  ───────                │  ───────             │  ──────   │
│  Schedule / Cron        │  AI Agents           │  Discord  │
│  Webhook / HTTP         │  Data Transform       │  Email    │
│  Form submission        │  Condition Router    │  GitHub   │
│  Email / RSS            │  Loop / Iterator     │  YouTube  │
└─────────────────────────┴──────────────────────┴───────────┘
```

---

### 🔧 NC1: n8n - WORKFLOW ENGINEER

#### 10.1.1 Fondamenti n8n

**Cos'è n8n:**
- Workflow automation open-source
- Simile a Zapier ma self-hosted
- 400+ integrazioni (nodes)
- Codice custom JS/Python allowed
- GitHub-first memory

**Concenti Base:**
| Concetto | Descrizione |
|----------|-------------|
| **Node** | Unità funzionale (app/connessione) |
| **Workflow** | Grafo di nodi connessi |
| **Trigger** | Nodo che avvia il workflow |
| **Expression** | `{{ $json.field }}` per dynamic data |
| **Credentials** | Autenticazione salvata separatamente |
| **Webhooks** | Endpoint HTTP per trigger esterno |

#### 10.1.2 Prompt Template n8n

```
Prompt Base per generare workflow n8n:

"
Genera workflow n8n in JSON con questa struttura:

TRIGGER: [webhook/schedule/http]

NODES richiesti:
1. [Node type] - config: [params]
2. [Node type] - config: [params]
... (almeno 5-10 nodes)

REQUIREMENTS:
- Error handling con Retry On Fail
- Logging con Slack notification
- Memory persistence su GitHub

OUTPUT: n8n workflow JSON completo
"
```

#### 10.1.3 Workflow n8n Comuni

**A. YouTube Content Pipeline:**
```
┌──────────────┐    ┌──────────┐    ┌───────────┐
│ Webhook      │───▶│ Gemini   │───▶│ Script    │
│ (trigger)    │    │ API      │    │ Generator │
└──────────────┘    └──────────┘    └───────────┘
                                          │
┌──────────────┐    ┌──────────┐    ┌──────────┴───┐
│ Discord      │◀───│ GitHub   │◀───│ Metadata     │
│ Notify       │    │ Commit   │    │ Writer       │
└──────────────┘    └──────────┘    └──────────────┘
```

**B. Lead Generation Funnel:**
```
┌──────────────┐    ┌──────────┐    ┌───────────┐
│ Landing Page │───▶│ Filter   │───▶│ AI Score  │
│ (Typeform)   │    │ Spreadsheet│  │ Qualify    │
└──────────────┘    └──────────┘    └───────────┘
                                          │
        ┌─────────────────────────────────┼───────┐
        ▼                 ▼               ▼       ▼
┌──────────────┐  ┌──────────┐  ┌──────────┐ ┌──────────┐
│ Hot: CRM      │  │ Warm:    │  │ Cold:    │ │ Trash:   │
│ (HubSpot)     │  │ EmailSeq │  │ Nurture  │ │ Reject   │
└──────────────┘  └──────────┘  └──────────┘ └──────────┘
```

#### 10.1.4 Esempio Reale: n8n per AI Dream Team

```json
{
  "name": "AI-Dream-Team-Orchestrator",
  "nodes": [
    {
      "name": "Webhook Trigger",
      "type": "n8n-nodes-base.webhook",
      "parameters": {
        "httpMethod": "POST",
        "path": "ai-content-request"
      }
    },
    {
      "name": "Parse Input",
      "type": "n8n-nodes-base.set",
      "parameters": {
        "values": {
          "channel": "{{ $json.channel }}",
          "topic": "{{ $json.topic }}",
          "type": "{{ $json.type }}"
        }
      }
    },
    {
      "name": "AI Script Generator",
      "type": "@n8n/n8n-nodes-langchain.chatOpenAI",
      "parameters": {
        "model": "gpt-4",
        "messages": [
          {
            "role": "system",
            "content": "Sei Agent 08 (Script Writer). Genera script YouTube completo."
          },
          {
            "role": "user",
            "content": "Topic: {{ $node.Parse Input.data.topic }}"
          }
        ]
      }
    },
    {
      "name": "GitHub Memory Save",
      "type": "n8n-nodes-base.github",
      "parameters": {
        "operation": "createFile",
        "filePath": "scripts/{{ $node.Parse Input.data.channel }}/{{ $json.timestamp }}.md",
        "content": "{{ $node['AI Script Generator'].data }}"
      }
    },
    {
      "name": "Discord Notify",
      "type": "discord-node.discord",
      "parameters": {
        "webhook": {
          "url": "YOUR_DISCORD_WEBHOOK"
        },
        "content": "✅ Script generato per {{ $node.Parse Input.data.topic }}"
      }
    }
  ]
}
```

---

### ⚡ NC2: Make.com (Integromat) - SCENARIO BUILDER

#### 10.2.1 Fondamenti Make

**Cos'è Make.com:**
- Scenario automation (ex Integromat)
- Visuale "flowchart" invece di lista
- Eccellente per orchestrazione complessa
- Costi basati su operazioni
- AI Scenarios (GPT integration)

**Concetti Chiave:**
| Concetto | Equivalente n8n |
|----------|-----------------|
| **Scenario** | Workflow |
| **Module** | Node |
| **Trigger** | Starting Module |
| **Router** | IF/Switch node |
| **Iterator** | For-each loop |
| **Aggregator** | Combine results |

#### 10.2.2 Prompt Template Make

```
Prompt per generare scenario Make.com:

"
Genera scenario Make.com in formato JSON Schema con:

TRIGGER: [webhook/http/schedule/github/etc]

MODULES richiesti:
1. [Module] - action: [what]
2. [Module] - action: [what]
3. Router con [N] paths
4. Aggregation finale

FLOW:
[Descrivi il flusso logico passo-passo]

ERROR HANDLING:
- Retry 3x on failure
- Slack alert on error
- Dead letter queue

OUTPUT: Make.com bundle JSON
"
```

#### 10.2.3 Pattern Make.com Avanzati

**A. GitHub-triggered Multi-Path:**
```
Trigger: GitHub webhook (new PR)
    │
    ▼
┌─────────┐
│ Router  │ ──── label: "bug fix" ──── Agent 02 → Priority High
│         │ ──── label: "feature" ──── Agent 03 → Review Queue  
│         │ ──── label: "docs" ─────── Agent 34 → Auto Merge
└─────────┘
```

**B. AI-Orchestrated Pipeline:**
```
Trigger: Schedule (daily 7AM)
    │
    ▼
┌──────────┐
│ YouTube   │ ──── Get new videos
│ Module   │
└──────────┘
    │
    ▼
┌──────────┐
│ Iterator │ ──── For each video
└──────────┘
    │
    ▼
┌─────────────────────────────────────────────────┐
│ Parallel Branches (run simultaneously):          │
│  ├── AI Analysis (Claude)                       │
│  ├── Transcript (Whisper)                       │
│  ├── Thumbnail Generator (DALL-E)               │
│  └── SEO Optimizer (Surfer AI)                   │
└─────────────────────────────────────────────────┘
    │
    ▼
┌──────────┐
│ Aggregator│ ──── Combine all results
└──────────┘
    │
    ▼
┌──────────┐
│ GitHub   │ ──── Create/update PR with analysis
│ Module  │
└──────────┘
```

**C. Lead Scoring Funnel:**
```
Form Submission
      │
      ▼
┌──────────┐
│ Router   │─── score > 80 ──→ High Priority CRM
│ (Score)  │─── score 50-80 ──→ Email Sequence
│          │─── score < 50 ──→ Nurture Content
└──────────┘
```

#### 10.2.4 Esempio: Make Scenario JSON

```json
{
  "name": "YouTube-AI-Pipeline",
  "flow": [
    {
      "id": "trigger",
      "type": "github",
      "action": "watchRepository",
      "options": {
        "repository": "Schumynet/youtube-ai-dream-team-open",
        "events": ["push", "pull_request"]
      }
    },
    {
      "id": "router1",
      "type": "router",
      "routes": [
        {
          "label": "content-update",
          "condition": "{{ contains('script|workflow', trigger.path) }}",
          "modules": ["ai-analyzer", "github-commit"]
        },
        {
          "label": "config-update",
          "condition": "{{ contains('config|env', trigger.path) }}",
          "modules": ["validator", "notify"]
        }
      ]
    },
    {
      "id": "ai-analyzer",
      "type": "openai",
      "action": "chat",
      "model": "gpt-4",
      "messages": [
        {"role": "system", "content": "Sei Agent 31 Master Orchestrator"},
        {"role": "user", "content": "Analizza: {{ trigger.content }}"}
      ]
    },
    {
      "id": "github-commit",
      "type": "github",
      "action": "createFile",
      "options": {
        "content": "{{ ai-analyzer.response }}",
        "path": "analysis/{{ trigger.sha }}.md"
      }
    },
    {
      "id": "notify",
      "type": "discord",
      "action": "sendMessage",
      "webhook": "{{ variables.DISCORD_WEBHOOK }}",
      "content": "✅ Pipeline completata!"
    }
  ]
}
```

---

### 🚀 NC3: GITHUB ACTIONS - CI/CD NO-CODE

#### 10.3.1 Fondamenti GitHub Actions

**GitHub Actions = CI/CD automation integrato in GitHub**

**Componenti:**
| Component | Descrizione |
|-----------|-------------|
| **Workflow** | File YAML in `.github/workflows/` |
| **Job** | Serie di steps in un runner |
| **Step** | Azione individuale (shell/azione) |
| **Action** | Unità riutilizzabile |
| **Runner** | Server dove gira il job |

#### 10.3.2 Prompt Template GitHub Actions

```
Prompt per generare GitHub Actions workflow:

"
Crea workflow GitHub Actions in YAML con:

TRIGGER:
- push su [branches]
- pull_request
- schedule: cron "0 7 * * *" (daily 7AM UTC)
- repository_dispatch per webhook manuale

JOBS:
1. generate-content
   - uses: actions/setup-python@v4
   - run: python scripts/generate.py
   
2. create-video
   - run: ffmpeg -i image.png -i audio.mp3 output.mp4
   
3. upload-youtube
   - uses: google-github-actions/upload-youtube@v1
   
4. commit-metadata
   - uses: EndBug/add-and-commit@v9

ENV:
- GEMINI_API_KEY: secrets.GEMINI_API_KEY
- YOUTUBE_CLIENT_SECRETS: secrets.YOUTUBE_CREDENTIALS

OUTPUT: .github/workflows/ai-content.yml
"
```

#### 10.3.3 Workflow Completi

**A. Daily YouTube Content Generator:**
```yaml
name: AI YouTube Pipeline

on:
  schedule:
    - cron: '0 7 * * *'  # Daily 7 AM UTC
  workflow_dispatch:  # Manual trigger

jobs:
  ideate:
    runs-on: ubuntu-latest
    outputs:
      topic: ${{ steps.topic.outputs.selected }}
    steps:
      - uses: actions/checkout@v4
      
      - name: Generate Topic
        id: topic
        run: |
          TOPIC=$(python scripts/ideation.py)
          echo "selected=$TOPIC" >> $GITHUB_OUTPUT
      
      - name: Save to Memory
        run: |
          echo "${{ steps.topic.outputs.selected }}" >> topics/ledger.md
          git config user.name "AI Agent"
          git add . && git commit -m "Daily topic: ${{ steps.topic.outputs.selected }}"
          git push

  script:
    runs-on: ubuntu-latest
    needs: ideate
    steps:
      - uses: actions/checkout@v4
      
      - name: Generate Script
        env:
          TOPIC: ${{ needs.ideate.outputs.topic }}
        run: python scripts/script_generator.py "$TOPIC"
      
      - name: Upload Script
        uses: actions/upload-artifact@v4
        with:
          name: script-${{ needs.ideate.outputs.topic }}
          path: output/script.md

  video:
    runs-on: ubuntu-latest
    needs: script
    steps:
      - uses: actions/checkout@v4
      
      - name: Download Script
        uses: actions/download-artifact@v4
        with:
          name: script-${{ needs.ideate.outputs.topic }}
      
      - name: Create Video
        run: |
          pip install moviepy gtts
          python scripts/create_video.py
      
      - name: Upload Video
        uses: google-github-actions/upload-youtube@v1
        with:
          credentials: ${{ secrets.YOUTUBE_CREDENTIALS }}
          file_path: output/video.mp4
          title: "${{ needs.ideate.outputs.topic }} - AI Generated"
          description: "${{ needs.script.outputs.description }}"

  notify:
    runs-on: ubuntu-latest
    needs: [ideate, script, video]
    steps:
      - name: Discord Notification
        run: |
          curl -X POST ${{ secrets.DISCORD_WEBHOOK }} \
            -d "content=✅ Video pubblicato: ${{ needs.ideate.outputs.topic }}"
```

**B. Multi-Agent Pipeline (Department G):**
```yaml
name: Multi-Agent Orchestration

on:
  repository_dispatch:
    types: [agent-request]

jobs:
  department-a-strategy:
    runs-on: ubuntu-latest
    steps:
      - name: Agent 01-03 (Strategy)
        run: python agents/dept_a.py
    outputs:
      strategy: ${{ steps.run.outputs.result }}

  department-b-script:
    runs-on: ubuntu-latest
    needs: department-a-strategy
    steps:
      - name: Agent 07-11 (Scripting)
        run: python agents/dept_b.py
    outputs:
      script: ${{ steps.run.outputs.result }}

  department-g-validate:
    runs-on: ubuntu-latest
    needs: [department-a-strategy, department-b-script]
    steps:
      - name: Agent 31-35 (Orchestration)
        run: python agents/dept_g.py
      - name: Agent 34 (Memory Save)
        uses: EndBug/add-and-commit@v9
        with:
          message: "Pipeline output from Multi-Agent system"
```

---

### 📊 NC4: PIPELINE DATA NO-CODE

#### 10.4.1 Stack Data Pipeline

```
┌──────────────────────────────────────────────────────────────┐
│                    DATA PIPELINE STACK                        │
├──────────────────────────────────────────────────────────────┤
│  SOURCES          │  TRANSFORM        │  DESTINATIONS        │
│  ───────          │  ─────────        │  ────────────        │
│  YouTube API      │  n8n/Make          │  Airtable            │
│  Google Sheets    │  (visual ETL)     │  Notion              │
│  Airtable         │                   │  BigQuery            │
│  CSV/JSON files   │  Python (if needed)│  Webhook → other    │
│  RSS feeds        │                   │  dashboards          │
├──────────────────────────────────────────────────────────────┤
│  TRIGGER          │  VALIDATION        │  MONITORING          │
│  ───────          │  ──────────        │  ───────────        │
│  Schedule         │  JSON Schema       │  Discord alerts     │
│  Webhook          │  Type checking     │  Email on error     │
│  New record       │  Dept G (Agent 32) │  GitHub Actions log │
└──────────────────────────────────────────────────────────────┘
```

#### 10.4.2 Prompt per Pipeline Data

```
Prompt:

"
Crea pipeline no-code per ETL dati:

SOURCE:
- YouTube Analytics API (daily metrics)
- Google Sheets (content plan)

TRANSFORM (in n8n):
1. Fetch YouTube metrics (views, likes, comments)
2. Fetch content plan from Sheets
3. Join on video ID
4. Calculate: CTR, engagement rate, trending score
5. Filter top 10 videos by trending score

OUTPUT:
- Airtable: Daily metrics table
- Discord: Weekly summary report
- GitHub: JSON file with analysis (Agent 34)

TRIGGER: Every day at 8 AM

Provide complete n8n workflow JSON + setup instructions.
"
```

---

### 🎓 NC5: APPRENDIMENTO PROGRESSION

#### Level 1: Beginner (Week 1-2)
```
□ Comprendere trigger e azioni base
□ Creare 5 workflow semplici (email, notify, etc.)
□ Padroneggiare expressions ({{ $json.field }})
□ Usare n8n o Make.com (scegli uno)
□ 10 minuti/day di pratica
```

#### Level 2: Intermediate (Week 3-4)
```
□ Integrare API REST (not YouTube, Twitter, etc.)
□ Usare AI/LLM nodes (OpenAI, Claude)
□ Implementare error handling e retry
□ Creare workflow multi-step con router
□ Lavorare con JSON data transformation
```

#### Level 3: Advanced (Week 5-8)
```
□ Orchestrare multi-agent pipelines
□ GitHub Actions per CI/CD
□ Integrare database (PostgreSQL, MongoDB)
□ Costruire custom nodes/modules
□ Performance optimization
```

#### Level 4: Expert (Month 3+)
```
□ Architettura sistemi completi
□ Team collaboration con version control
□ Monitoring e alerting avanzato
□ Costruire template riutilizzabili
□ Consulting/freelancing no-code
```

---

### 🛠️ NC6: TOOL COMPARISON

#### Piattaforme No-Code Automation

| Tool | Best For | Pros | Cons | Prezzo |
|------|----------|------|------|--------|
| **n8n** | Self-hosted, developers | Open source, flessibile | Setup richiesto | Free/self-hosted |
| **Make** | Visual clarity, teams | Flowchart visivo | Limiti operazioni | €9-49/mo |
| **Zapier** | Beginners, SaaS | Semplice, 5000+ apps | Costoso per volume | €20-599/mo |
| **Pipedream** | Developers, code | Code + no-code | Curva ripida | Free tier generoso |
| **GitHub Actions** | CI/CD, devs | Integrato GitHub | Non visuale | Free 2000 min/mo |

#### Quando usare cosa:

```
Use n8n when:
├── Vuoi self-hosted (privacy, costi)
├── Hai bisogno di custom code
└── Usi GitHub memory (Agent 34)

Use Make when:
├── Team senza coding skills
├── Workflow complesso visivo
└── Vuoi template pre-fatti

Use GitHub Actions when:
├── CI/CD automation
├── Hai repository GitHub
└── Vuoi cron job reliable

Use combination:
└── n8n + Make + GitHub Actions = Stack completo!
```

---

### 🔥 NC7: QUICK REFERENCE CARD

```
╔════════════════════════════════════════════════════════════════╗
║              PROMPT RAPIDI NO-CODE                            ║
╠════════════════════════════════════════════════════════════════╣
║                                                                ║
║  "crea workflow n8n: trigger webhook → AI → GitHub → Discord"  ║
║                                                                ║
║  "genera scenario Make: GitHub trigger → 3 path paralleli"     ║
║                                                                ║
║  "workflow GitHub Actions: cron daily → Python → YouTube API"  ║
║                                                                ║
║  "pipeline data: YouTube API → n8n transform → Airtable"       ║
║                                                                ║
║  "agent chain: Agent 01 → 03 → 08 → 32 → 34 (full pipeline)"  ║
║                                                                ║
╚════════════════════════════════════════════════════════════════╝
```

---

### 📋 NC8: EXERCISI PRATICI

#### Esercizio 1: Primo Workflow (30 min)
```
Obiettivo: Crea workflow che invia notifica Discord quando
           viene aperta una PR sul tuo repo

Tool: n8n o Make.com

Steps:
1. Trigger: GitHub webhook
2. Filter: Solo pull_request events
3. Action: Discord send message con PR title + link
4. Test con creare PR test
```

#### Esercizio 2: AI Content Pipeline (1 ora)
```
Obiettivo: Pipeline che genera topic YouTube con AI

Tool: n8n

Steps:
1. Trigger: Schedule (ogni lunedì)
2. Fetch: Trend Twitter/Reddit API
3. AI: GPT analizza e propone 5 topic
4. Output: Salva in Google Sheets + notifica Slack
```

#### Esercizio 3: Multi-Agent System (2 ore)
```
Obiettivo: Riproduci Department G del YouTube AI Dream Team

Tool: n8n + GitHub Actions

Steps:
1. GitHub Actions: Orchestrator workflow
2. n8n: Agent 32 + 33 validators
3. n8n: Agent 34 memory saver
4. Output: Valida workflow, committa risultati
```

---

*Corso elaborato: Sviluppatore No-Code Avanzato*
*Include n8n, Make.com, GitHub Actions, Pipeline Data*
*Schumynet © 2024-2025*