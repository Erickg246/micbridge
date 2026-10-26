const $ = id => document.getElementById(id);
function render({ status, connection }) {
  $('status').textContent = status.message;
  $('dot').className = status.state === 'error' ? 'error' : '';
  $('disconnect').disabled = status.state !== 'streaming';
  if (connection) {
    $('address').textContent = connection.addresses.map(ip => `${ip}:${connection.port}`).join(' / ') || 'Sem endereço de rede';
    $('code').textContent = connection.code;
    $('fingerprint').textContent = connection.fingerprint;
    $('copy').disabled = !connection.addresses.length;
  }
}
window.micbridge.onUpdate(render);
window.micbridge.state().then(render);
$('copy').onclick = async () => { await window.micbridge.copy(); $('copy').textContent = 'Copiado!'; setTimeout(() => { $('copy').textContent = 'Copiar endereço'; }, 1500); };
$('disconnect').onclick = () => window.micbridge.disconnect();
