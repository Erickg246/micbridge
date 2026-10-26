# MicBridge

Seu Android como microfone no Linux. Dois aplicativos instalados, sem navegador, conta ou serviço na internet.

**Versão inicial: Android 8+ e Linux x64 com PulseAudio ou PipeWire/PulseAudio.** O receptor cria uma entrada de gravação chamada **MicBridge_Microfone_do_celular**. Selecione essa entrada no Discord, OBS, gravador ou nas configurações de som. Ela fica disponível enquanto o app Linux está aberto; ao fechá-lo, os dispositivos virtuais criados por ele são removidos. O app não altera o microfone padrão do sistema.

## Instalar e usar

1. No Linux Debian/Ubuntu, instale `micbridge_0.1.0_amd64.deb` com `sudo apt install ./micbridge_0.1.0_amd64.deb`. O pacote instala o app e declara as dependências de áudio e OpenSSL. Abra **MicBridge** pelo menu de aplicativos, na sessão normal do usuário, sem sudo.
2. No Android, instale `app-debug.apk`. É um APK de desenvolvimento, assinado com chave de depuração; autorize a instalação pela fonte usada. Não é uma publicação na Play Store.
3. Conecte os dois aparelhos à mesma rede Wi-Fi. No celular, informe o IP/porta e o código de seis dígitos exibidos no PC.
4. Compare a impressão digital mostrada no celular com **Conferir identidade do PC** no Linux. Confirme apenas se forem iguais. Depois, permita o microfone. Notificações são recomendadas para controlar a transmissão.
5. Escolha **MicBridge_Microfone_do_celular** como entrada de áudio no aplicativo Linux que deseja usar. Use **Silenciar microfone** ou **Parar transmissão** no celular; a notificação também permite parar.

Se houver firewall, permita **TCP 47321** apenas na rede local. Redes Wi-Fi com isolamento entre clientes impedem a conexão. Não exponha essa porta na internet. A transmissão é TLS com certificado do PC validado por impressão digital e código de sessão; não há relay externo. O código muda a cada abertura do receptor. Apenas um celular pode transmitir por vez.

## Desenvolver o receptor Linux

Requer Node.js 22+, npm, OpenSSL e uma sessão de áudio funcional. Debian/Ubuntu:

```sh
sudo apt install pulseaudio-utils openssl
npm ci
npm start
```

No PipeWire, o serviço `pipewire-pulse` precisa estar ativo. Em distribuições que usam PulseAudio, o daemon do usuário precisa estar ativo. `pactl info` deve funcionar antes de abrir o app. Se o Chromium avisar que o sandbox não está configurado, use o pacote `.deb`, que instala o helper com os privilégios corretos, ou habilite os namespaces de usuário conforme a política da sua distribuição. Não execute o app como root.

```sh
npm run package:linux
```

O instalador sai em `dist/`. O primeiro início gera um certificado local usando OpenSSL no diretório de configuração do app. A chave privada não pertence ao repositório. O receptor envia PCM mono de 48 kHz/16 bits ao `pacat` e publica um `module-remap-source`, que faz a entrada aparecer como microfone real para os clientes PulseAudio/PipeWire. Não há reprodução automática nos alto-falantes.

## Desenvolver o Android

Abra `android/` no Android Studio ou use JDK 17/21, Android SDK 35 e Build Tools 35.0.0:

```sh
cd android
./gradlew assembleDebug lintDebug
```

Configure `ANDROID_HOME` ou `android/local.properties` para o SDK. O APK fica em `android/app/build/outputs/apk/debug/app-debug.apk`. A captura usa `AudioRecord` e um serviço foreground com notificação; o usuário inicia a transmissão com o app visível e pode continuar com a tela bloqueada. A permissão do microfone é solicitada em tempo de execução. O mute envia silêncio; parar encerra a captura. A compatibilidade com políticas de bateria e chamadas simultâneas ainda precisa de teste em aparelhos físicos.

## Validar

```sh
npm test
# Integração opcional: sessão de áudio Linux e JDK disponíveis
MICBRIDGE_AUDIO_TEST=1 MICBRIDGE_JAVA_TEST=1 npm test
```

Os testes básicos verificam TLS, código incorreto, formato incompatível, concorrência, cabeçalhos inválidos e preservação do PCM. A integração de áudio verifica a presença da entrada virtual e grava um tom de 440 Hz através dela. A integração Java executa a mesma implementação TLS usada pelo Android contra o receptor, incluindo rejeição de uma impressão digital incorreta.

**Validação desta entrega:** build do APK de depuração, lint Android, empacotamento Linux, abertura da janela desktop e testes automatizados de transporte/áudio. Ainda não foi validada a captura em um celular físico, o desempenho em redes reais ou a seleção no Discord/OBS. Não há suporte a iPhone, Windows, USB, Bluetooth ou reconexão automática nesta versão. Latência ponta a ponta não foi medida.

## GitHub

Repositório: [Erickg246/micbridge](https://github.com/Erickg246/micbridge). O código está na branch `main`. Os instaladores são gerados localmente pelos comandos acima; ainda não foram publicados em GitHub Releases nem em lojas de aplicativos.
