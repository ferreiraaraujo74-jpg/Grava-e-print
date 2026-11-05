# Traço Mágico — Foto em Desenho

App Android nativo (Kotlin + Jetpack Compose) que transforma fotos em desenhos: **esboço a lápis, carvão, lápis de cor, contorno, stencil, desenho animado, hachura, aquarela** e mais. Tem também o modo **Traçar**, para copiar o desenho no papel.

- **Baixar o APK:** veja as [Releases](https://github.com/ferreiraaraujo74-jpg/Grava-e-print/releases) (tag `traco-magico-v1.0`) ou [`release/`](release/)
- Android 10+ (minSdk 29, targetSdk 34) · funciona offline, sem anúncios e sem enviar fotos para a internet

## Recursos
- **55 estilos em 7 categorias:**
  - Lápis (14): clássico, HB, 2B, 6B, grafite, esboço fino/largo, papel kraft, papel antigo, caderno, lápis azul, sanguínea, giz no quadro, giz pastel no preto
  - Carvão (4): carvão, intenso, esfumado, conté
  - Lápis de cor (5): normal, suave, vivo, giz pastel, aquarela a lápis
  - Contorno (10): nanquim, fino, grosso, caneta azul/vermelha, neon, neon rosa, planta azul, gravura dourada, linha branca
  - Stencil (8): stencil, detalhado, forte, silhueta, vermelho, duas cores, três tons, grafite urbano
  - Desenho animado (6): desenho animado, cartoon suave, quadrinho, pop art, pintura chapada, mangá
  - Artístico (8): hachura, hachura cruzada, gravura, pontilhismo, retícula, aquarela, aquarela suave, sépia artística
- **Miniaturas com a sua própria foto** em cada estilo
- Ajustes de **Intensidade** e **Mostrar foto**, que mistura o desenho com a foto original
- **Segure a imagem** para comparar com a foto original
- **Salvar** em alta resolução (até 2048 px) na Galeria: `Pictures/TracoMagico/desenho_AAAAMMDD_HHMMSS.png`
- **Compartilhar** (WhatsApp, Instagram etc.). O app também aparece no "Compartilhar" da Galeria, para abrir fotos direto nele.
- **Traçar no papel:**
  - *Câmera*: o desenho fica semitransparente sobre a câmera. Apoie o celular acima da folha e copie os traços olhando a tela.
  - *Mesa de luz*: tela com brilho máximo. Coloque uma folha fina sobre a tela e trace.
  - Arraste, use a pinça e gire o desenho; ajuste a opacidade; espelhe; use a lanterna; **trave** para não mexer (segure o cadeado para destravar).
- **Minhas criações**: grade dos desenhos salvos, com abrir, compartilhar, traçar e apagar

## Como os filtros funcionam
Tudo roda no aparelho, em Kotlin puro (`app/src/main/java/com/tracomagico/app/filters/`):
- **Lápis/carvão**: cinza + *color dodge* com o negativo desfocado (gaussiano por 3 passadas de box blur), curva gama, granulado de papel e duas cores (grafite e papel).
- **Contorno**: gradiente de Sobel com limiar adaptativo por percentil, então funciona em fotos claras e escuras.
- **Stencil**: limiar adaptativo local ou por percentil global; versões em 2 e 3 tons.
- **Desenho animado**: suavização de cores, posterização, saturação e contornos.
- **Hachura, gravura, pontilhismo, retícula, aquarela**: padrões gerados a partir do tom da foto.

Cada estilo é um `Style` em `Styles.all`, com algoritmo + parâmetros. Para criar um novo, basta adicionar uma linha.

## Compilar
Requer JDK 17+ e Android SDK (platform 34).
```bash
./gradlew assembleRelease
```
Teste dos filtros no computador, que também exporta todos os estilos de uma foto `.ppm` para conferência:
```bash
./gradlew testDebugUnitTest -Psample=/caminho/foto.ppm -Pout=/caminho/saida
```
Assinatura: crie `keystore.properties` nesta pasta (fora do git) com `storeFile`, `storePassword`, `keyAlias` e `keyPassword`. Sem esse arquivo, o release é assinado com a chave de debug.

> A chave de assinatura não está no repositório. Guarde o `.jks` e as senhas: as atualizações precisam da mesma chave.
