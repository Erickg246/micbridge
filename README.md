# MicBridge 0.3.0

Seu Android como microfone no Linux. **Conexão TCP direta, sem criptografia, TLS, certificados ou códigos.** O celular mostra o IP; o PC conecta.

[Baixar APK e DEB](downloads/README.md)

## Usar

Atualize os dois aplicativos para 0.3.0. As versões anteriores usam TLS e são incompatíveis.

1. No Android 8+, abra MicBridge e toque em **Iniciar servidor**. Permita o microfone.
2. No Linux Debian/Ubuntu x64, instale o DEB e abra MicBridge pelo menu ou execute `/opt/MicBridge/micbridge`, sem sudo.
3. Na mesma rede Wi-Fi, digite no PC o IP mostrado no celular e clique em **Conectar**. A porta TCP 47321 é automática.
4. No celular, toque em **Permitir PC**. Não há confirmação de certificado nem código.
5. Escolha **MicBridge_Microfone_do_celular** como entrada no Discord, OBS ou outro aplicativo Linux.

O Android permite silenciar, recusar o PC e parar o servidor. O microfone só é capturado após autorização. O áudio não é criptografado; use sua rede local. A conexão não usa servidor externo. Fechar o app Linux remove os dispositivos virtuais criados por ele.

O APK é de desenvolvimento e mantém a assinatura da versão anterior. O DEB inclui a correção de permissões do aplicativo e do atalho.

## Desenvolvimento

Node.js 22+, npm e PulseAudio ou PipeWire com compatibilidade PulseAudio. `pactl info` precisa funcionar.

```sh
npm ci
npm start
npm run package:linux
```

Android: JDK 17/21, SDK 35 e Build Tools 35.0.0. Configure ANDROID_HOME e execute:

```sh
cd android
./gradlew assembleDebug lintDebug
```

Testes:

```sh
npm test
MICBRIDGE_AUDIO_TEST=1 MICBRIDGE_JAVA_TEST=1 npm test
```

Os testes verificam TCP sem TLS, autorização, recusa, formato de áudio, desconexão, interoperabilidade Java e a entrada virtual com um tom de 440 Hz. O áudio é PCM mono de 48 kHz/16 bits. Testes usam áudio sintético; captura em celular físico e Wi-Fi real ainda precisam de validação.

Se não conectar, confira se ambos estão em 0.3.0, se o servidor está iniciado, o IP do celular e se a rede permite comunicação entre os aparelhos. Não há versão para iPhone ou Windows.
