# Elenco completo degli endpoint Pavo

155 chiamate, estratte dal bundle JavaScript di `app.pavo-ai.work`. Questi sono
gli endpoint interni dell'app, non una API pubblica: non sono documentati e
possono cambiare senza preavviso.

Base: `https://api.pixa-ai.work/api/v1`

I percorsi con `//e` o `//t` sono quelli che il bundle costruisce con
`.concat()`: nella pratica sono `/{id}`. I percorsi con `{id}` vanno completati
con un id vero.

## Nota sui metodi

Il metodo indicato e' quello che l'app usa. Alcuni endpoint rispondono a piu'
metodi: per esempio `/user/account` risponde 405 a GET perche' vuole DELETE.

## Elenco

```
POST   /v1/aigc/credits/calculate
POST   /v1/aigc/credits/estimate
GET    /v1/aigc/model-capability-spec
POST   /v1/chat/conversation
POST   /v1/chat/stream
POST   /v1/chat/stream/cancel
POST   /v1/chat/stream/hitl-resume
POST   /v1/chat/stream/resume
POST   /v1/file/convert-to-markdown
POST   /v1/file/inspect
POST   /v1/file/presigned-url
GET    /v1/im/user-sig
POST   /v1/media/watermark
GET    /v1/pixa/canvas/
POST   /v1/pixa/canvas/assets/characters
POST   /v1/pixa/canvas/assets/characters/generate-fourview
POST   /v1/pixa/canvas/assets/description/expand
GET    /v1/pixa/canvas/assets/models
POST   /v1/pixa/canvas/assets/props
POST   /v1/pixa/canvas/assets/scenes
POST   /v1/pixa/canvas/generation/cancel
POST   /v1/pixa/canvas/generation/progress
GET    /v1/pixa/canvas/project/
PATCH  /v1/pixa/canvas/project/
POST   /v1/pixa/canvas/project/
GET    /v1/pixa/canvas/project//encodeURIComponent(e.project_uuid
POST   /v1/pixa/canvas/project//encodeURIComponent(n
?      /v1/pixa/canvas/project//{id}
POST   /v1/pixa/canvas/prompt/enhance
POST   /v1/pixa/canvas/prompt/translate
GET    /v1/pixa/canvas/tool-specs
GET    /v1/pixa/design_tools
POST   /v1/pixa/face_recognition
GET    /v1/pixa/mode_support_models
GET    /v1/pixa/models
GET    /v1/pixa/promo
GET    /v1/pixa/sd-feed/banners
POST   /v1/pixa/sd-feed/works
DELETE /v1/pixa/sd-feed/works//e
GET    /v1/pixa/sd-feed/works//e
POST   /v1/pixa/sd-feed/works//e,like
?      /v1/pixa/sd-feed/works//{id}
POST   /v1/pixa/short-drama/art/art-versions
DELETE /v1/pixa/short-drama/art/art-versions//e
?      /v1/pixa/short-drama/art/art-versions//{id}
POST   /v1/pixa/short-drama/art/batch-generate-image
POST   /v1/pixa/short-drama/art/cancel-generate-image
POST   /v1/pixa/short-drama/art/characters/generate-cover
POST   /v1/pixa/short-drama/art/characters/generate-fourview
POST   /v1/pixa/short-drama/art/expand-description
POST   /v1/pixa/short-drama/art/props/generate-image
POST   /v1/pixa/short-drama/art/scenes/generate-image
DELETE /v1/pixa/short-drama/episodes//e
GET    /v1/pixa/short-drama/episodes//e
PATCH  /v1/pixa/short-drama/episodes//e
POST   /v1/pixa/short-drama/episodes//e,compose
POST   /v1/pixa/short-drama/episodes//e,generate
POST   /v1/pixa/short-drama/episodes//e,segments
GET    /v1/pixa/short-drama/episodes//e,storyboarddetail
POST   /v1/pixa/short-drama/episodes//e,storyboardgenerate
POST   /v1/pixa/short-drama/episodes//e,subtitle
POST   /v1/pixa/short-drama/episodes//e.episode_id,segmentsbatchkeyframegenera
POST   /v1/pixa/short-drama/episodes//e.episode_id,segmentsbatchvideogenerate
?      /v1/pixa/short-drama/episodes//{id}
POST   /v1/pixa/short-drama/episodes/batch/storyboard/generate
POST   /v1/pixa/short-drama/export
DELETE /v1/pixa/short-drama/projects//e
PATCH  /v1/pixa/short-drama/projects//e
POST   /v1/pixa/short-drama/projects//e,assetextract
GET    /v1/pixa/short-drama/projects//e,assetrefs
GET    /v1/pixa/short-drama/projects//e,assets
DELETE /v1/pixa/short-drama/projects//e,assetscharacters
PATCH  /v1/pixa/short-drama/projects//e,assetscharacters
POST   /v1/pixa/short-drama/projects//e,assetscharacters
DELETE /v1/pixa/short-drama/projects//e,assetscharacterstates
DELETE /v1/pixa/short-drama/projects//e,assetsprops
PATCH  /v1/pixa/short-drama/projects//e,assetsprops
POST   /v1/pixa/short-drama/projects//e,assetsprops
DELETE /v1/pixa/short-drama/projects//e,assetspropstates
DELETE /v1/pixa/short-drama/projects//e,assetsscenes
PATCH  /v1/pixa/short-drama/projects//e,assetsscenes
POST   /v1/pixa/short-drama/projects//e,assetsscenes
DELETE /v1/pixa/short-drama/projects//e,assetsscenestates
PATCH  /v1/pixa/short-drama/projects//e,autosyncassets
POST   /v1/pixa/short-drama/projects//e,episodes
PUT    /v1/pixa/short-drama/projects//e,episodes
POST   /v1/pixa/short-drama/projects//e,episodesresume
POST   /v1/pixa/short-drama/projects//e,episodesretry
GET    /v1/pixa/short-drama/projects//e,episodesstoryboard
PATCH  /v1/pixa/short-drama/projects//e,outline
POST   /v1/pixa/short-drama/projects//e,outlineconfirm
POST   /v1/pixa/short-drama/projects//e,outlineretry
GET    /v1/pixa/short-drama/projects//e,progress
POST   /v1/pixa/short-drama/projects//e.project_id,assetsfinalize
POST   /v1/pixa/short-drama/projects//e.project_id,assetsimportfrommaster
POST   /v1/pixa/short-drama/projects//t,assetscharacters
POST   /v1/pixa/short-drama/projects//t,assetsprops
POST   /v1/pixa/short-drama/projects//t,assetsscenes
?      /v1/pixa/short-drama/projects//{id}
POST   /v1/pixa/short-drama/projects/ai-generate
POST   /v1/pixa/short-drama/projects/batch-delete
POST   /v1/pixa/short-drama/projects/paste
POST   /v1/pixa/short-drama/projects/upload
DELETE /v1/pixa/short-drama/segments//e
POST   /v1/pixa/short-drama/segments//e,keyframegenerate
PUT    /v1/pixa/short-drama/segments//e,media
GET    /v1/pixa/short-drama/segments//e,mediarole
POST   /v1/pixa/short-drama/segments//e,mediaupload
GET    /v1/pixa/short-drama/segments//e,referenceimages
POST   /v1/pixa/short-drama/segments//e,shots
POST   /v1/pixa/short-drama/segments//e,videogenerate
?      /v1/pixa/short-drama/segments//{id}
PATCH  /v1/pixa/short-drama/shots
DELETE /v1/pixa/short-drama/shots//e
?      /v1/pixa/short-drama/shots//{id}
GET    /v1/pixa/short-drama/styles
POST   /v1/pixa/short-drama/tasks//e,stop
?      /v1/pixa/short-drama/tasks//{id}
POST   /v1/pixa/short-drama/voices
DELETE /v1/pixa/short-drama/voices//e
DELETE /v1/pixa/short-drama/voices//e,favorite
POST   /v1/pixa/short-drama/voices//e,favorite
?      /v1/pixa/short-drama/voices//{id}
POST   /v1/pixa/short-drama/voices/generate
GET    /v1/pixa/short-drama/voices/generate/credits
GET    /v1/pixa/support_modes
POST   /v1/redemption-codes/benefits/redeem
POST   /v1/security/parameter
DELETE /v1/skills/
POST   /v1/skills/
GET    /v1/skills/favorites
GET    /v1/skills/tabs
GET    /v1/subscription/credits-packs/t
?      /v1/subscription/credits-packs/{id}
POST   /v1/subscription/credits-transactions
POST   /v1/subscription/subscribe
POST   /v1/subscription/upgrade/preview
GET    /v1/ugc/categories
POST   /v1/ugc/like
DELETE /v1/user/account
POST   /v1/user/auth/phone-otp
GET    /v1/user/auth_methods
POST   /v1/user/avatar/presigned-url
POST   /v1/user/bind_phone
POST   /v1/user/bind_wechat
GET    /v1/user/captcha/status
POST   /v1/user/code/send
POST   /v1/user/influencer-invite/bind
POST   /v1/user/login
PUT    /v1/user/profile
POST   /v1/user/register
POST   /v1/user/reset_password
POST   /v1/visuals/batch_delete
POST   /v1/visuals/download
POST   /v1/visuals/remove_watermark
```

## Verificati funzionanti

Questi sono stati provati con il token reale e rispondono `code: 000000`:

| endpoint | metodo | risposta |
|---|---|---|
| `/pixa/models` | GET | 25 modelli |
| `/pixa/design_tools` | GET | strumenti di disegno |
| `/pixa/promo` | GET | promozioni |
| `/pixa/support_modes` | GET | modalita' supportate |
| `/subscription/credits-balance` | GET | saldo |
| `/subscription/credits-packs` | GET | 6 pacchetti |
| `/user/profile` | GET | profilo utente |
| `/user/auth_methods` | GET | metodi di accesso |
| `/pixa/short-drama/styles` | GET | 60 stili in 3 categorie |
| `/pixa/short-drama/voices` | GET | 20 voci, tutte `en` |
| `/pixa/short-drama/voices/generate/credits` | GET | 2 crediti |
| `/pixa/short-drama/assets` | GET | asset per tipo |
| `/pixa/canvas/assets/models` | GET | modelli canvas |
| `/pixa/canvas/tool-specs` | GET | specifiche audio/immagine/video |
| `/pixa/sd-feed/banners` | GET | banner |
| `/pixa/sd-feed/works` | GET | lavori pubblici |
| `/pixa/short-drama/projects` | GET | progetti dell'utente |
| `/pixa/short-drama/projects/{id}/progress` | GET | avanzamento |
| `/pixa/short-drama/projects/{id}/episodes/storyboard` | GET | episodi |
| `/aigc/credits/estimate` | POST | costo vero |

## Verificati NON funzionanti

| endpoint | esito | nota |
|---|---|---|
| `/aigc/credits/calculate` | 200 ma `0` | inutile: usa `estimate` |
| `/pixa/general-library` | 404 | non esiste piu' |
| `/v3/subscription/plans` | 404 | non esiste |
| `/v2/user/profile` | 404 | usa `/user/profile` |
| `/subscription/faq` | 404 | non esiste |
| `/pixa/mode_support_models` | 400 | vuole parametri |
| `/aigc/model-capability-spec` | 400 | vuole `model_code` e `model_type` |
| `/pixa/canvas` | 404 | serve un id |
| `/subscription/credits-transactions` | 405 | vuole POST, non GET |
| `/user/account` | 405 | vuole DELETE |
