# Grava & Print

> Este repositório também tem os apps **[Traço Mágico — Foto em Desenho](traco-magico/)** e **[Avatar Falante](avatar-falante/)**.

App Android nativo (Kotlin + Jetpack Compose) que **grava a tela em vídeo e, durante a gravação, tira prints** tocando num botão flutuante, sem pausar nem interromper o vídeo.

- **Baixar o APK:** [Grava & Print 1.0 — Releases](https://github.com/ferreiraaraujo74-jpg/Grava-e-print/releases/latest) (assinado; também em [`release/`](release/))
- minSdk 29 (Android 10) · targetSdk 34 (Android 14)

## Instalar
1. Copie o APK para o celular e abra o arquivo.
2. Se o Android pedir, permita "Instalar apps desconhecidos" para o app que abriu o arquivo (Arquivos, Chrome etc.).

## Como usar
1. Escolha a qualidade (720p/1080p), o FPS (30/60) e o áudio (sem áudio / microfone / som interno).
2. Toque em **Iniciar gravação**. O app pede, em sequência e explicando cada uma: sobrepor a outros apps → microfone (só se houver áudio) → notificações (Android 13+) → captura de tela.
3. O app minimiza e aparece a **bolha flutuante** com o cronômetro:
   - **toque** = print (vibração + piscada branca + "Print salvo");
   - **toque longo** = menu: Pausar/Retomar, Parar gravação, Esconder bolha;
   - **arraste** para levar a bolha a qualquer canto.
4. A notificação fixa mostra "Gravando 00:00" com os botões **Print**, **Pausar** e **Parar**. Tocar nela mostra de novo a bolha escondida.
5. **Ver gravações** abre a galeria (abas Vídeos e Prints): abrir, compartilhar (WhatsApp, Instagram, TikTok…) ou apagar.

Arquivos (aparecem na Galeria do celular):
- Vídeos: `Movies/GravaEPrint/video_AAAAMMDD_HHMMSS.mp4`
- Prints: `Pictures/GravaEPrint/print_AAAAMMDD_HHMMSS.png`

## Como funciona
```
MediaProjection (1) ─► VirtualDisplay (1, resolução nativa)
                          │
                          ▼
             SurfaceTexture (OES) ── ScreenRenderer (OpenGL ES 2, thread própria)
                          │                         │
     desenha cada frame ◄─┘                         └─► print: desenha num FBO + glReadPixels
                          ▼                                  → Bitmap → PNG (MediaStore)
   Surface de entrada do MediaCodec (H.264) ─► MediaMuxer (MP4) ◄─ AAC (AudioRecord: mic ou som interno)
```
- `RecorderService`: serviço em primeiro plano (`foregroundServiceType="mediaProjection|microphone"`), com `startForeground` antes de `getMediaProjection` (exigência do Android 14) e o callback `onStop` do MediaProjection, que fecha tudo e salva o vídeo.
- Há **um único** MediaProjection e **um único** VirtualDisplay (o Android 14 não deixa criar um segundo). O print sai do mesmo contexto OpenGL que alimenta o encoder, então o vídeo continua normalmente.
- O VirtualDisplay usa a resolução real da tela, então os prints saem em resolução nativa. O vídeo é reduzido para 720p/1080p, num tamanho que o encoder do aparelho aceita.
- A bolha some por cerca de 0,2 s na hora do print para não aparecer na imagem.
- Na pausa, o encoder para de receber frames e áudio; os timestamps descontam o tempo pausado, então o vídeo final não fica com buracos.
- O som interno usa `AudioPlaybackCaptureConfiguration`. Apps que proíbem a captura (alguns streamings e chamadas) ficam mudos.

Código principal: `app/src/main/java/com/gravaeprint/app/`
| Arquivo | Função |
|---|---|
| `service/RecorderService.kt` | serviço, ciclo de vida, notificação, prints |
| `gl/ScreenRenderer.kt`, `gl/EglCore.kt` | renderizador OpenGL e captura com glReadPixels |
| `service/VideoEncoder.kt`, `AudioEncoder.kt`, `SafeMuxer.kt`, `RecordingClock.kt` | codificação H.264/AAC, MP4 e pausa |
| `overlay/OverlayController.kt` | bolha, cronômetro, mini menu e piscada |
| `MainActivity.kt`, `ui/MainScreen.kt` | tela principal e permissões |
| `GalleryActivity.kt`, `ui/GalleryScreen.kt`, `data/` | galeria e MediaStore |

## Compilar
Requer JDK 17+ e Android SDK (platform 34).
```bash
./gradlew assembleRelease
```
Para assinar, crie `keystore.properties` na raiz (fica fora do git):
```
storeFile=keystore/gravaeprint-release.jks
storePassword=...
keyAlias=gravaeprint
keyPassword=...
```
Sem esse arquivo, o build de release é assinado com a chave de debug.

Para publicar uma nova versão: aumente `versionCode`/`versionName` em `app/build.gradle.kts`, coloque o APK em `release/GravaEPrint-<versão>.apk` e envie para o branch. O workflow `.github/workflows/release.yml` cria a release sozinho (também dá para rodar manualmente na aba Actions).

> A chave de assinatura **não** está no repositório. Guarde o `.jks` e as senhas: atualizações do app precisam ser assinadas com a mesma chave.
