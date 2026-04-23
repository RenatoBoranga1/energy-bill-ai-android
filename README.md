# Energy Bill AI Android

Aplicativo Android recriado para o produto de leitura inteligente de contas de energia, com foco em UX premium e integrao real com o backend FastAPI.

## Stack

- Kotlin
- Jetpack Compose
- Material Design 3
- Hilt
- Retrofit + OkHttp
- Room
- DataStore
- Coroutines + Flow

## Estrutura

- `app/src/main/java/br/com/energybillai/core`
- `app/src/main/java/br/com/energybillai/data`
- `app/src/main/java/br/com/energybillai/domain`
- `app/src/main/java/br/com/energybillai/feature`
- `app/src/main/java/br/com/energybillai/navigation`

## Design system

O tema foi reconstruido com uma paleta inspirada na identidade visual da CPFL:

- azul institucional para estados primarios e navegacao
- amarelo para destaque e alertas orientados a acao
- superfices claras com contraste corporativo

## Funcionalidades implementadas

- login e cadastro
- bootstrap de sessao com refresh token
- dashboard com cards e serie resumida
- historico com cache local
- upload de PDF, imagem e foto da camera
- revisao da extracao antes da confirmacao
- detalhe da conta
- analytics
- forecast
- perfil e logout

## Configuracao local

1. Em tablet ou celular fisico, mantenha o backend FastAPI rodando na maquina da rede em `http://192.168.31.19:8000/`.

2. Gere o APK debug apontando para esse IP:

```powershell
./gradlew.bat :app:assembleDebug -PAPI_BASE_URL=http://192.168.31.19:8000/ -PAPI_ENVIRONMENT=local
```

O app valida a URL em build/runtime e espera um host acessivel por LAN ou producao. Para producao futura, use `-PAPI_ENVIRONMENT=production` e uma URL HTTPS.

3. Se precisar pular autenticacao em debug:

```powershell
./gradlew.bat :app:assembleDebug -PSKIP_LOGIN_FOR_DEV=true
```

4. APK gerado em:

- `app/build/outputs/apk/debug/app-debug.apk`

## Observacoes

- O app usa `Room` para cache do historico e `DataStore` para sessao.
- O upload por camera usa contrato de captura do Android com `FileProvider`.
- O fluxo esta pronto para evoluir para CameraX inline e previews mais ricos.
