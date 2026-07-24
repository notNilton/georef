# 🚀 Guia Completo de Publicação: GeoRef Field (Google Play & App Store)

Este documento contém o passo a passo detalhado para gerar as versões de produção e publicar o **GeoRef Field** no **Google Play Store** (Android) e **Apple App Store** (iOS).

---

## 🟢 1. Publicação no Google Play Store (Android)

### 📌 Pré-requisitos:
- Conta de Desenvolvedor no [Google Play Console](https://play.google.com/console) (Taxa única de **$25 USD**).

---

### 🔑 Passo 1: Gerar a Chave de Assinatura (Keystore)
No terminal da sua máquina, execute o comando abaixo para criar a chave de assinatura de produção:

```bash
keytool -genkey -v -keystore georef-release.jks -alias georef-key -keyalg RSA -keysize 2048 -validity 10000
```
> ⚠️ **IMPORTANTE**: Guarde o arquivo `georef-release.jks`, a senha e o alias em local seguro. Sem essa chave você não conseguirá atualizar o aplicativo no futuro!

---

### 📦 Passo 2: Gerar o Android App Bundle (`.aab`)
Navegue até a pasta `mobile` do projeto e execute o Gradle para gerar a versão de produção:

```bash
cd mobile
./gradlew :androidApp:bundleRelease
```

O arquivo gerado estará localizado em:
`mobile/androidApp/build/outputs/bundle/release/androidApp-release.aab`

---

### 🏪 Passo 3: Cadastrar e Enviar no Google Play Console
1. Acesse o [Google Play Console](https://play.google.com/console) e clique em **"Criar app"**.
2. Preencha os dados do aplicativo:
   - **Nome do App**: GeoRef Field - Mapeamento GIS Offline
   - **Idioma padrão**: Português (Brasil)
   - **Tipo de App**: Aplicativo (Gratuito)
3. Preencha a **Ficha da Loja (Store Listing)**:
   - **Ícone**: Imagem de `512x512 px` (PNG).
   - **Gráfico de Recursos (Feature Graphic)**: Imagem de `1024x500 px`.
   - **Capturas de tela (Screenshots)**: Mínimo de 2 capturas de tela do celular funcionando (Mapa, Lista de Camadas, Navegação GPS).
4. Na aba **Produção** (ou Teste Aberto/Fechado):
   - Faça o upload do arquivo `androidApp-release.aab`.
5. Preencha o **Questionário de Conteúdo**, **Público-alvo** e forneça a URL da **Política de Privacidade**.
6. Clique em **Enviar para análise**. (A análise do Google leva entre 24h e 72h).

---

## 🍎 2. Publicação na Apple App Store (iOS)

### 📌 Pré-requisitos:
- Conta de Desenvolvedor no [Apple Developer Program](https://developer.apple.com/programs/) (**$99 USD/ano**).
- Computador **Mac (macOS)** com **Xcode** instalado.

---

### 🛠️ Passo 1: Gerar os Binários KMP para iOS
No terminal do Mac, dentro da pasta `mobile`:

```bash
cd mobile
./gradlew :shared:assembleReleaseXCFramework
```

---

### 📱 Passo 2: Configurar o Projeto no Xcode
1. Abra o arquivo `mobile/iosApp/iosApp.xcodeproj` no **Xcode**.
2. Em **Signing & Capabilities**:
   - Selecione o seu **Team** da Apple Developer.
   - Configure o **Bundle Identifier** (ex: `com.nilbyte.georef`).
3. Verifique as permissões no arquivo `Info.plist`:
   - `NSLocationWhenInUseUsageDescription`: *"O GeoRef necessita da sua localização para navegação e marcação de pontos GIS no campo."*

---

### 📤 Passo 3: Compilar e Enviar via Xcode (Archive)
1. No menu superior do Xcode, selecione o dispositivo de destino como **Any iOS Device (arm64)**.
2. Acesse o menu **Product** -> **Archive**.
3. Quando o processo terminar, o **Organizer** do Xcode será aberto.
4. Clique em **Distribute App** -> selecione **App Store Connect** -> **Upload**.

---

### 🏬 Passo 4: Finalizar no App Store Connect
1. Acesse o [App Store Connect](https://appstoreconnect.apple.com/).
2. Em **Meus Apps**, selecione o **GeoRef Field**.
3. Adicione as capturas de tela para iPhone (6.5" e 5.5").
4. Adicione a **Descrição**, **Palavras-chave** (ex: *gis, agricultura, gps, shapefile, mapa, fazenda*).
5. Selecione a compilação (Build) que você enviou do Xcode.
6. Clique em **Enviar para Revisão**. (A revisão da Apple leva de 24h a 48h).

---

## 💡 Checklist Antes da Publicação:
- [x] Testar o aplicativo em modo Offline (sem internet).
- [x] Garantir que o cache de imagens e o banco SQLite estejam funcionando.
- [x] Testar permissões de GPS/Localização no dispositivo real.
- [x] Criar uma página simples de Política de Privacidade.
