# Baixar MicBridge 0.2.0

**Atualize os dois apps:** o celular agora mostra o IP e funciona como servidor; o PC conecta a ele. A versão 0.2.0 não se conecta à versão 0.1.0.

- [Android — APK 0.2.0](https://github.com/Erickg246/micbridge/raw/refs/heads/main/downloads/MicBridge-0.2.0-android.apk)
- [Linux Debian/Ubuntu x64 — DEB 0.2.0](https://github.com/Erickg246/micbridge/raw/refs/heads/main/downloads/micbridge_0.2.0_amd64.deb)

Instale no Linux:

```sh
sudo apt install ./micbridge_0.2.0_amd64.deb
```

Abra MicBridge pelo menu ou execute `/opt/MicBridge/micbridge`, sem sudo. Esta atualização corrige o erro de permissão e o atalho ausente.

No Android, toque em **Iniciar servidor**. No PC, digite o IP mostrado no celular e clique em **Conectar**. Confirme a identidade na primeira conexão e toque em **Permitir PC** no celular. Use a mesma rede Wi-Fi e selecione **MicBridge_Microfone_do_celular** como entrada de áudio no Linux.

O áudio usa TLS. APK de teste para Android 8+, assinado com chave de depuração. Ainda é necessário testar em celular físico.
