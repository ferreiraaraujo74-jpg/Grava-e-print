# Avatar Falante

App Android nativo (Kotlin + Jetpack Compose) que faz **uma foto ou um personagem falar** o que você quiser, e gera um **vídeo MP4** para compartilhar. É gratuito e funciona sem internet: detecção do rosto, voz e vídeo rodam no próprio celular.

- **Baixar o APK:** veja as [Releases](https://github.com/ferreiraaraujo74-jpg/Grava-e-print/releases) (tag `avatar-falante-v1.0`) ou [`release/`](release/)
- Android 10+ (minSdk 29, targetSdk 34), celulares ARM (praticamente todos)

## Como usar
1. **Escolha o avatar:** uma foto (galeria ou câmera) ou **Criar personagem** (pele, cabelo, olhos, roupa, fundo e óculos).
2. **Escolha a fala:**
   - **Texto:** digite e toque em *Gerar fala*. Usa a voz do Android em português, com escolha de voz, velocidade e tom.
   - **Gravar voz:** fale no microfone (até 2 min).
   - **Áudio:** use um arquivo de som do celular (até 3 min).
3. Escolha a **expressão**: neutro, feliz, surpreso, bravo ou triste.
4. Toque em **Ouvir** para ver a prévia e em **Gerar vídeo**. O vídeo fica em `Movies/AvatarFalante/avatar_AAAAMMDD_HHMMSS.mp4`, com opção de compartilhar.

Dica: use foto de frente, com o rosto bem iluminado e a boca e os olhos visíveis. **Use só fotos suas ou de pessoas que autorizaram.**

## Como funciona
- **Rosto:** o [ML Kit Face Detection](https://developers.google.com/ml-kit/vision/face-detection) (gratuito, modelo embutido) encontra os contornos do rosto, lábios, olhos e sobrancelhas.
- **Animação da foto** (`anim/PhotoAvatar.kt`): uma malha deformável (`Canvas.drawBitmapMesh`) com campos de deslocamento suaves e sem dobras:
  - **Maxilar e lábios:** o corte segue a linha real entre os lábios. Se a foto já mostra os dentes, o corte fica abaixo dos dentes de cima. A abertura afina até sumir nos cantos.
  - **Interior da boca:** desenhado por cima, com fundo escuro, dentes (quando a boca original estava fechada), língua e sombras.
  - **Formato da boca** (largo/estreito), sorriso, pálpebras, sobrancelhas e balanço da cabeça.
- **Sincronia labial** (`anim/Timeline.kt`): volume (RMS) por quadro, com o ritmo das sílabas destacado, mais a taxa de cruzamento por zero para diferenciar vogais (“o/u”) de sons como “s/i”. A boca adianta 40 ms em relação ao som. Piscadas e movimentos de cabeça são naturais e pseudoaleatórios.
- **Personagem** (`anim/CartoonAvatar.kt`): desenhado com Canvas e animado com os mesmos parâmetros.
- **Vídeo** (`export/VideoExporter.kt`): cada quadro é desenhado num Bitmap, vira textura OpenGL e vai para a Surface do encoder H.264 com o timestamp exato. O áudio vai em AAC, tudo no mesmo MP4, gerado mais rápido que o tempo real.

Limitações: é uma animação 2D. Fica convincente para vídeos curtos, mas não é uma IA de geração de vídeo. Rostos de perfil, cobertos ou muito pequenos não funcionam bem.

## Compilar
Requer JDK 17+ e Android SDK (platform 34).
```bash
./gradlew assembleRelease
```
Testes no computador (Robolectric com o Canvas nativo do Android):
```bash
./gradlew testDebugUnitTest
```
Com `-Pface=<pasta com portrait.jpg, landmarks.json e fala.wav> -Pout=<pasta>`, os testes também salvam quadros PNG da animação para conferência visual.

Assinatura: crie `keystore.properties` nesta pasta (fora do git) com `storeFile`, `storePassword`, `keyAlias` e `keyPassword`. A chave não está no repositório.
