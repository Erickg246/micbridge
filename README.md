# MicBridge

Seu Android como microfone no Linux, com dois aplicativos instalados e áudio criptografado. **Na versão 0.2.0, o celular é o servidor: ele mostra o IP, e o PC conecta.**

[Baixar os aplicativos](downloads/README.md)

## Instalar e conectar

Atualize **os dois aplicativos** para 0.2.0; o protocolo mudou e não funciona com o app 0.1.0 do outro lado.

1. No Android 8+, instale `MicBridge-0.2.0-android.apk`. Abra o app, permita o microfone e toque em **Iniciar servidor**. O IP do próprio celular aparece na tela.
2. No Linux Debian/Ubuntu x64, instale `micbridge_0.2.0_amd64.deb` com `sudo apt install ./micbridge_0.2.0_amd64.deb`. Abra **MicBridge** pelo menu, na sessão do usuário. Pelo terminal: `/opt/MicBridge/micbridge`, sem sudo.
3. Conecte ambos à mesma rede Wi-Fi. Digite **somente o IP do celular no PC** e clique em **Conectar**; a porta 47321 é automática. Também é possível informar `IP:porta`.
4. Na primeira conexão, o PC mostra a identidade do celular. No Android, toque em **Identidade do celular** e confirme no PC apenas se os números forem iguais. A identidade confirmada é lembrada para esse endereço.
5. No celular, toque em **Permitir PC**. A captura só começa depois dessa autorização; cada nova conexão precisa ser autorizada. Não há código numérico para digitar.
6. Selecione **MicBridge_Microfone_do_celular** como microfone no Discord, OBS ou outro aplicativo Linux.

O Android permite silenciar o áudio, recusar o PC e parar o servidor. Uma notificação mantém o controle disponível em segundo plano. Ao desconectar, o servidor volta a aguardar; não há reconexão automática. Fechar o app Linux remove os dispositivos virtuais criados por ele. O microfone padrão do sistema não é alterado.

O APK é uma versão de desenvolvimento assinada com chave de depuração, fora da Play Store. A atualização mantém o mesmo applicationId e a mesma assinatura de depuração da versão anterior. O novo `.deb` corrige as permissões que impediam abrir o app e visualizar o atalho no menu.

## Se não conectar

- Inicie o servidor no Android antes de clicar em Conectar no PC. Deixe o app do celular aberto para autorizar.
- Use o IP exibido pelo app do **celular**, não o IP do computador. Se houver mais de um, escolha o da mesma rede do PC.
- Ambos precisam conseguir se comunicar pela rede local. Redes de convidados com isolamento entre clientes, VPNs e firewalls podem bloquear a conexão. O servidor escuta **TCP 47321 no celular**; o PC inicia a conexão de saída.
- Confira a permissão de microfone e a data/hora dos aparelhos. A rede precisa ter IPv4 local nesta versão.
- No Linux, `pactl info` deve funcionar. Instale `pulseaudio-utils` e use uma sessão PulseAudio ou PipeWire com `pipewire-pulse` ativo.

## Criptografia e áudio

O servidor Android usa TLS 1.2/1.3 e uma chave privada gerada no Android Keystore. A conexão inicial de descoberta lê somente o certificado público, sem pedir áudio. Depois de o usuário comparar a impressão digital SHA-256, o PC abre outra conexão com validação de certificado e identidade. Uma mudança de identidade exige nova confirmação. Cada transmissão também exige autorização no celular; o microfone só é aberto após a aprovação.

O áudio é PCM mono de 48 kHz/16 bits. O PC envia os dados ao `pacat` e publica um `module-remap-source`, que aparece como entrada de gravação. Não há conta, navegador, servidor externo nem reprodução automática nos alto-falantes. Use em rede local; não publique a porta na internet. Latência ponta a ponta ainda não foi medida.

## Desenvolvimento

Receptor: Node.js 22+, npm e servidor de áudio Linux funcional.

```sh
sudo apt install pulseaudio-utils
npm ci
npm start
npm run package:linux
```

O `.deb` sai em `dist/`. O instalador configura o sandbox do Chromium e as permissões do app. Não execute como root nem desative o sandbox no uso normal.

Android: abra `android/` no Android Studio ou configure JDK 17/21, Android SDK 35 e Build Tools 35.0.0:

```sh
cd android
./gradlew assembleDebug lintDebug
```

Configure `ANDROID_HOME` ou `android/local.properties`. O APK fica em `android/app/build/outputs/apk/debug/app-debug.apk`.

```sh
npm test
# Integrações opcionais: sessão de áudio Linux, OpenSSL e JDK disponíveis
MICBRIDGE_AUDIO_TEST=1 MICBRIDGE_JAVA_TEST=1 npm test
```

Os testes verificam o cliente TLS do PC, validação de identidade, espera por autorização, recusa, desconexão, formato de áudio e transferência do PCM. A integração Java executa o mesmo protocolo servidor usado pelo Android. A integração de áudio verifica a entrada virtual e grava nela um tom de 440 Hz.

A captura em celular físico, o Android Keystore em aparelhos reais, redes Wi-Fi reais e a seleção no Discord/OBS ainda precisam de validação. O teste Java usa um certificado de teste e áudio sintético; não substitui essa validação. Não há versão para iPhone, Windows, USB ou Bluetooth.

Repositório: [Erickg246/micbridge](https://github.com/Erickg246/micbridge).
